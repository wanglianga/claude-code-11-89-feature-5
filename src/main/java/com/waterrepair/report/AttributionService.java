package com.waterrepair.report;

import com.waterrepair.network.AddressPoint;
import com.waterrepair.network.AddressPointRepository;
import com.waterrepair.network.GeoLandmark;
import com.waterrepair.network.GeoLandmarkRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把报修原文/坐标归属到管段。
 *
 * <p>关键纪律：坐标最近、同一栋楼都只会产生“候选”，跨相邻管段时给 AMBIGUOUS，
 * 绝不自动判定为同一事故；事故只能由调度显式确认。</p>
 */
@Service
public class AttributionService {

    /** 坐标归属半径（米）：范围内地址所属管段均作为候选。 */
    private static final double COORD_CANDIDATE_RADIUS_M = 80.0;

    private final AddressPointRepository addressRepo;
    private final GeoLandmarkRepository landmarkRepo;

    public AttributionService(AddressPointRepository addressRepo, GeoLandmarkRepository landmarkRepo) {
        this.addressRepo = addressRepo;
        this.landmarkRepo = landmarkRepo;
    }

    public AttributionJudgment judgeAutomatically(Long reportId, String rawAddress, Double lng, Double lat) {
        List<AddressPoint> addresses = addressRepo.findAll();
        List<GeoLandmark> landmarks = landmarkRepo.findAll();

        AttributionJudgment j = new AttributionJudgment();
        j.setReportId(reportId);
        j.setVersionNo(1);
        j.setSource("AUTO");
        j.setJudgedAt(OffsetDateTime.now());

        // 坐标最近点仅作为旁证保存
        if (lng != null && lat != null) {
            AddressPoint nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (AddressPoint a : addresses) {
                if (a.getLng() == null || a.getLat() == null) {
                    continue;
                }
                double d = distanceM(lng, lat, a.getLng(), a.getLat());
                if (d < nearestDist) {
                    nearestDist = d;
                    nearest = a;
                }
            }
            if (nearest != null) {
                j.setNearestAddressCode(nearest.getCode());
                j.setNearestDistanceM(Math.round(nearestDist * 10.0) / 10.0);
            }
        }

        // 1) 地址别名/楼栋命中：按“最长别名命中”消除泛化楼栋名的歧义
        Map<String, Integer> bestAliasLenByAddress = new LinkedHashMap<>();
        Map<String, String> matchedAliasByAddress = new LinkedHashMap<>();
        String text = rawAddress == null ? "" : rawAddress.trim();
        for (AddressPoint a : addresses) {
            int best = -1;
            String bestAlias = null;
            for (String alias : a.getAliases()) {
                if (alias == null || alias.length() < 2) {
                    continue;
                }
                if (text.contains(alias) || (alias.length() >= 4 && alias.contains(text) && text.length() >= 4)) {
                    if (alias.length() > best) {
                        best = alias.length();
                        bestAlias = alias;
                    }
                }
            }
            if (best >= 0) {
                bestAliasLenByAddress.put(a.getCode(), best);
                matchedAliasByAddress.put(a.getCode(), bestAlias);
            }
        }

        if (!bestAliasLenByAddress.isEmpty()) {
            int globalBest = bestAliasLenByAddress.values().stream().max(Integer::compare).orElse(0);
            List<AddressPoint> matched = new ArrayList<>();
            for (AddressPoint a : addresses) {
                if (globalBest == bestAliasLenByAddress.getOrDefault(a.getCode(), -2)) {
                    matched.add(a);
                }
            }
            Set<String> segments = new HashSet<>();
            matched.forEach(a -> segments.add(a.getSegmentCode()));
            j.getCandidateSegmentCodes().addAll(segments.stream().sorted().toList());
            j.setMatchedAddressCode(matched.size() == 1 ? matched.get(0).getCode()
                    : matched.stream().map(AddressPoint::getCode).sorted().findFirst().orElse(null));

            boolean buildingStyle = matchedAliasByAddress.values().stream()
                    .anyMatch(al -> al != null && al.contains("栋"));
            if (segments.size() == 1) {
                j.setStatus("MATCHED");
                j.setMethod("ADDRESS_ALIAS");
                j.getSelectedSegmentCodes().addAll(segments);
                j.setReason("报修原文命中地址别名 " + matchedAliasByAddress.values()
                        + "，唯一映射管段 " + segments + "；仍需调度确认后才形成事故。");
            } else {
                j.setStatus("AMBIGUOUS");
                j.setMethod(buildingStyle ? "BUILDING_MULTI_ADDRESS" : "ADDRESS_MULTI_ADDRESS");
                j.setReason("同一原文/楼栋（" + matchedAliasByAddress.values()
                        + "）命中分属不同管段的地址 "
                        + matched.stream().map(AddressPoint::getCode).sorted().toList()
                        + "，不能按同一栋楼合并为同一事故，需人工核实边界。");
            }
            return j;
        }

        // 2) 路口/地标：可能位于相邻管段之间，天然多候选
        for (GeoLandmark lm : landmarks) {
            if (text.contains(lm.getName()) || lm.getName().contains(text) && text.length() >= 4) {
                j.setStatus("AMBIGUOUS");
                j.setMethod("LANDMARK_BETWEEN_SEGMENTS");
                j.getCandidateSegmentCodes().addAll(lm.getCandidateSegmentCodes());
                j.setReason("原文命中地标「" + lm.getName() + "」，该位置落在相邻管段 "
                        + lm.getCandidateSegmentCodes() + " 之间，候选边界必须分别核实，不能按最近坐标合并。");
                return j;
            }
        }

        // 3) 坐标兜底：半径内所有管段都是候选，绝不直接并事故
        if (lng != null && lat != null) {
            Set<String> nearbySegments = new HashSet<>();
            for (AddressPoint a : addresses) {
                if (a.getLng() == null) {
                    continue;
                }
                double d = distanceM(lng, lat, a.getLng(), a.getLat());
                if (d <= COORD_CANDIDATE_RADIUS_M) {
                    nearbySegments.add(a.getSegmentCode());
                }
            }
            if (!nearbySegments.isEmpty()) {
                List<String> sorted = nearbySegments.stream().sorted().toList();
                j.getCandidateSegmentCodes().addAll(sorted);
                if (sorted.size() == 1) {
                    j.setStatus("MATCHED");
                    j.getSelectedSegmentCodes().addAll(sorted);
                    j.setReason("未命中地址文本，按坐标最近原则唯一接近管段 " + sorted
                            + "；坐标仅作归属线索，事故仍需调度确认。");
                } else {
                    j.setStatus("AMBIGUOUS");
                    j.setReason("坐标位于多个相邻管段 " + sorted + " 的临界范围，不能直接当作同一事故。");
                }
                j.setMethod("COORDINATE_NEAREST");
                return j;
            }
        }

        j.setStatus("UNMATCHED");
        j.setMethod("NONE");
        j.setReason("未能匹配地址、地标或有效坐标，需要调度人工录入归属管段。");
        return j;
    }

    /** 追加人工归属判断（历史版本保留）。 */
    public AttributionJudgment judgeManually(Long reportId, int nextVersion, List<String> segmentCodes,
                                             String status, String reason, String operator) {
        AttributionJudgment j = new AttributionJudgment();
        j.setReportId(reportId);
        j.setVersionNo(nextVersion);
        j.setSource("MANUAL");
        j.setStatus(status == null ? "CONFIRMED" : status);
        j.setMethod("MANUAL");
        j.getCandidateSegmentCodes().addAll(segmentCodes.stream().sorted().toList());
        if ("CONFIRMED".equals(j.getStatus()) || "MATCHED".equals(j.getStatus())) {
            j.getSelectedSegmentCodes().addAll(segmentCodes.stream().sorted().toList());
        }
        j.setReason(reason == null ? "调度/勘查人工核实归属" : reason);
        j.setJudgedBy(operator);
        j.setJudgedAt(OffsetDateTime.now());
        return j;
    }

    /** 等距圆柱近似，足够城市街区百米级距离判定。 */
    public static double distanceM(double lng1, double lat1, double lng2, double lat2) {
        double earth = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double meanLat = Math.toRadians((lat1 + lat2) / 2.0);
        double x = dLng * earth * Math.cos(meanLat);
        double y = dLat * earth;
        return Math.sqrt(x * x + y * y);
    }

    /** 供列表排序使用的比较器（未使用时保留清晰语义）。 */
    public static Comparator<AddressPoint> byDistance(double lng, double lat) {
        return Comparator.comparingDouble(a -> distanceM(lng, lat, a.getLng(), a.getLat()));
    }
}
