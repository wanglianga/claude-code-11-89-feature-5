package com.waterrepair.topology;

import com.waterrepair.auth.UserAccount;
import com.waterrepair.auth.UserAccountRepository;
import com.waterrepair.common.ApiException;
import com.waterrepair.network.PipeSegment;
import com.waterrepair.network.PipeSegmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 现场证据登记/双人核验，以及资产工程师确认后发布不可变拓扑新版本。
 */
@Service
public class TopologyVersionService {

    private final TopologyEvidenceRepository evidenceRepo;
    private final TopologyVersionRepository versionRepo;
    private final TopologyQueryService queryService;
    private final TopologyChangeService changeService;
    private final PipeSegmentRepository segmentRepo;
    private final UserAccountRepository userRepo;

    public TopologyVersionService(TopologyEvidenceRepository evidenceRepo,
                                  TopologyVersionRepository versionRepo,
                                  TopologyQueryService queryService,
                                  TopologyChangeService changeService,
                                  PipeSegmentRepository segmentRepo,
                                  UserAccountRepository userRepo) {
        this.evidenceRepo = evidenceRepo;
        this.versionRepo = versionRepo;
        this.queryService = queryService;
        this.changeService = changeService;
        this.segmentRepo = segmentRepo;
        this.userRepo = userRepo;
    }

    // ---------------- 证据 ----------------

    @Transactional
    public TopologyEvidence createEvidence(TopologyDtos.EvidenceRequest req, String operator) {
        if (req.surveyorName() == null || req.surveyorName().isBlank()) {
            throw ApiException.badRequest("SURVEYOR_REQUIRED", "测量/探测人 surveyorName 不能为空");
        }
        if (req.code() != null && !req.code().isBlank()
                && evidenceRepo.findByCode(req.code().trim()).isPresent()) {
            throw ApiException.conflict("EVIDENCE_CODE_EXISTS",
                    "证据编号已存在: " + req.code(), null);
        }
        TopologyEvidence e = new TopologyEvidence();
        e.setCode(req.code() == null || req.code().isBlank() ? null : req.code().trim());
        e.setTitle(req.title());
        e.setIncidentId(req.incidentId());
        e.setSketchRef(req.sketchRef());
        e.setSketchDescription(req.sketchDescription());
        e.setEndANodeCode(req.endANodeCode());
        e.setEndAIdentifier(req.endAIdentifier());
        e.setEndAMarker(req.endAMarker());
        e.setEndALng(req.endALng());
        e.setEndALat(req.endALat());
        e.setEndBNodeCode(req.endBNodeCode());
        e.setEndBIdentifier(req.endBIdentifier());
        e.setEndBMarker(req.endBMarker());
        e.setEndBLng(req.endBLng());
        e.setEndBLat(req.endBLat());
        e.setMeasuredDistanceM(req.measuredDistanceM());
        e.setMeasurementErrorM(req.measurementErrorM());
        e.setMeasurementMethod(req.measurementMethod());
        e.setSurveyorName(req.surveyorName().trim());
        e.setSurveyedAt(req.surveyedAt() == null ? OffsetDateTime.now() : req.surveyedAt());
        e.setCreatedBy(operator);
        e.setCreatedAt(OffsetDateTime.now());

        if (req.reviewerName() != null && !req.reviewerName().isBlank()) {
            applyDualCheck(e, req.reviewerName(), req.reviewedAt(), req.reviewNote());
        } else {
            e.setStatus("REGISTERED");
        }
        e = evidenceRepo.saveAndFlush(e);
        if (e.getCode() == null || e.getCode().isBlank()) {
            e.setCode(String.format("TEV-%04d", e.getId()));
            e = evidenceRepo.save(e);
        }
        return e;
    }

    @Transactional
    public TopologyEvidence dualCheck(Long id, TopologyDtos.DualCheckRequest req, String operator) {
        TopologyEvidence e = mustEvidence(id);
        if ("DUAL_CHECKED".equals(e.getStatus())) {
            throw ApiException.conflict("EVIDENCE_ALREADY_CHECKED",
                    "证据已完成双人核验，不能重复复核", null);
        }
        applyDualCheck(e, req.reviewerName(), OffsetDateTime.now(), req.reviewNote());
        return evidenceRepo.save(e);
    }

    private void applyDualCheck(TopologyEvidence e, String reviewerName,
                                OffsetDateTime reviewedAt, String reviewNote) {
        String reviewer = reviewerName == null ? "" : reviewerName.trim();
        if (reviewer.equals(e.getSurveyorName() == null ? "" : e.getSurveyorName().trim())) {
            throw ApiException.unprocessable("SAME_PERSON_REVIEWER",
                    "双人核验要求复核人与测量/探测人不是同一人",
                    Map.of("surveyorName", e.getSurveyorName(), "reviewerName", reviewer));
        }
        e.setReviewerName(reviewer);
        e.setReviewedAt(reviewedAt == null ? OffsetDateTime.now() : reviewedAt);
        e.setReviewNote(reviewNote);
        e.setStatus("DUAL_CHECKED");
    }

    @Transactional(readOnly = true)
    public List<TopologyEvidence> listEvidence() {
        return evidenceRepo.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public TopologyEvidence getEvidenceByIdOrCode(String idOrCode) {
        try {
            long id = Long.parseLong(idOrCode);
            return evidenceRepo.findById(id)
                    .or(() -> evidenceRepo.findByCode(idOrCode)).orElseThrow();
        } catch (NumberFormatException ex) {
            return evidenceRepo.findByCode(idOrCode)
                    .orElseThrow(() -> ApiException.notFound("EVIDENCE_NOT_FOUND",
                            "现场证据不存在: " + idOrCode));
        }
    }

    private TopologyEvidence mustEvidence(Long id) {
        return evidenceRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("EVIDENCE_NOT_FOUND",
                        "现场证据不存在: " + id));
    }

    // ---------------- 发布新版本 ----------------

    public record PublishResult(TopologyQueryService.VersionView version,
                                List<TopologyChangeService.ReviewView> changeReviews) {
    }

    @Transactional
    public PublishResult publish(TopologyDtos.PublishRevisionRequest req, String operator) {
        String mode = (req.mode() == null || req.mode().isBlank()) ? "PARTIAL" : req.mode().trim().toUpperCase();
        if (!"PARTIAL".equals(mode) && !"FULL".equals(mode)) {
            throw ApiException.badRequest("INVALID_REVISION_MODE",
                    "mode 只能是 PARTIAL（局部修订）或 FULL（整段测完整块替换）");
        }

        // 1) 证据闸门：必须存在且全部完成双人核验、关键证据要素齐全
        List<TopologyEvidence> evidences = loadReferencedEvidence(req.evidenceIds(), req.evidenceCodes());
        if (evidences.isEmpty()) {
            throw ApiException.unprocessable("EVIDENCE_REQUIRED",
                    "发布拓扑修订前必须提交至少一份现场证据（evidenceIds/evidenceCodes）", null);
        }
        for (TopologyEvidence e : evidences) {
            if (!"DUAL_CHECKED".equals(e.getStatus())
                    || e.getReviewerName() == null || e.getReviewerName().isBlank()) {
                throw ApiException.unprocessable("EVIDENCE_NOT_DUAL_CHECKED",
                        "证据 " + e.getCode() + " 尚未完成双人核验，不能发布拓扑版本",
                        Map.of("evidenceCode", e.getCode(), "status", e.getStatus()));
            }
            List<String> missing = new ArrayList<>();
            if (isBlank(e.getSketchRef()) && isBlank(e.getSketchDescription())) {
                missing.add("sketchRef/sketchDescription(现场草图)");
            }
            if (isBlank(e.getEndAIdentifier()) && isBlank(e.getEndANodeCode())) {
                missing.add("endAIdentifier/endANodeCode(管段一端标识)");
            }
            if (isBlank(e.getEndBIdentifier()) && isBlank(e.getEndBNodeCode())) {
                missing.add("endBIdentifier/endBNodeCode(管段另一端标识)");
            }
            if (e.getMeasuredDistanceM() == null) {
                missing.add("measuredDistanceM(测距结果)");
            }
            if (e.getMeasurementErrorM() == null) {
                missing.add("measurementErrorM(测量误差)");
            }
            if (!missing.isEmpty()) {
                throw ApiException.unprocessable("EVIDENCE_INCOMPLETE",
                        "证据 " + e.getCode() + " 缺少可审查要素: " + missing,
                        Map.of("evidenceCode", e.getCode(), "missing", missing));
            }
        }

        // 2) 基于当前 ACTIVE 快照打补丁，生成不可变新快照
        TopologyVersion active = queryService.currentActiveVersion();
        TopologyDtos.Snapshot base = queryService.readSnapshot(active.getSnapshotJson());

        List<TopologyDtos.SnapSegment> segments = new ArrayList<>(
                base.segments() == null ? List.of() : base.segments());
        if (req.addConnections() != null) {
            for (TopologyDtos.AddConnection c : req.addConnections()) {
                String code = isBlank(c.segmentCode())
                        ? "CX-" + c.fromNodeCode() + "-" + c.toNodeCode() : c.segmentCode();
                TopologyDtos.SnapSegment seg = new TopologyDtos.SnapSegment(
                        code, isBlank(c.name()) ? "现场探测新增连接管 " + code : c.name(),
                        c.fromNodeCode(), c.toNodeCode(), "CONFIRMED",
                        c.normalPressureMinMpa() == null ? 0.18 : c.normalPressureMinMpa(),
                        c.normalPressureMaxMpa() == null ? 0.42 : c.normalPressureMaxMpa(),
                        c.pressureTimeToleranceSeconds() == null ? 600 : c.pressureTimeToleranceSeconds());
                segments.removeIf(s -> s.code().equals(code));
                segments.add(seg);
            }
        }
        if (req.confirmSegmentCodes() != null) {
            for (String code : req.confirmSegmentCodes()) {
                boolean found = false;
                for (int i = 0; i < segments.size(); i++) {
                    if (segments.get(i).code().equals(code)) {
                        TopologyDtos.SnapSegment old = segments.get(i);
                        segments.set(i, new TopologyDtos.SnapSegment(old.code(), old.name(),
                                old.fromNodeCode(), old.toNodeCode(), "CONFIRMED",
                                old.normalPressureMinMpa(), old.normalPressureMaxMpa(),
                                old.pressureTimeToleranceSeconds()));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw ApiException.badRequest("UNKNOWN_SEGMENT",
                            "confirmSegmentCodes 中的管段在当前版本不存在: " + code);
                }
            }
        }
        List<TopologyDtos.UnknownBranch> unknownBranches = req.unknownBranches() == null
                ? (base.unknownBranches() == null ? List.of() : base.unknownBranches())
                : req.unknownBranches();
        if ("FULL".equals(mode) && !unknownBranches.isEmpty()) {
            throw ApiException.unprocessable("FULL_REPLACEMENT_NEEDS_ALL_MEASURED",
                    "整段测完后整块替换要求消除全部未知支管：请在 confirmSegmentCodes 中补测并将 "
                            + "unknownBranches 置空，或改走 PARTIAL 局部修订",
                    Map.of("unknownBranches", unknownBranches.stream()
                            .map(TopologyDtos.UnknownBranch::code).toList()));
        }

        TopologyDtos.Snapshot newSnapshot = new TopologyDtos.Snapshot(
                base.nodes(), segments, base.valves(), base.addresses(), unknownBranches);

        int nextNo = active.getVersionNo() + 1;
        String versionCode = isBlank(req.versionCode())
                ? "TOPO-V" + nextNo + "-" + mode
                : req.versionCode().trim();
        if (versionRepo.findByVersionCode(versionCode).isPresent()) {
            throw ApiException.conflict("TOPOLOGY_VERSION_EXISTS",
                    "拓扑版本号已存在: " + versionCode, null);
        }

        OffsetDateTime now = OffsetDateTime.now();
        TopologyVersion version = new TopologyVersion();
        version.setVersionCode(versionCode);
        version.setVersionNo(nextNo);
        version.setStatus("ACTIVE");
        version.setRevisionMode(mode);
        version.setParentVersionCode(active.getVersionCode());
        version.setChangeSummary(req.changeSummary());
        version.setEvidenceCodes(evidences.stream().map(TopologyEvidence::getCode).toList());
        version.setConfirmerName(resolveConfirmerName(req.confirmerName(), operator));
        version.setPublishedBy(operator);
        version.setPublishedAt(now);
        version.setEffectiveAt(req.effectiveAt() == null ? now : req.effectiveAt());
        version.setSnapshotJson(queryService.writeSnapshot(newSnapshot));
        version.setCreatedAt(now);

        active.setStatus("SUPERSEDED");
        versionRepo.save(active);
        version = versionRepo.saveAndFlush(version);

        // 3) 同步在线管网表，使新派工/在线影响计算立即使用新版本图
        applyToLiveTables(req);

        // 4) 评估引用旧版本的开放事故，生成人工重审单（不自动改任何工单/通知）
        List<TopologyChangeReview> reviews = changeService.assessOpenIncidents(versionCode);
        List<TopologyChangeService.ReviewView> reviewViews = reviews.stream()
                .map(r -> changeService.reviewView(r.getId())).toList();

        return new PublishResult(queryService.toView(version), reviewViews);
    }

    private void applyToLiveTables(TopologyDtos.PublishRevisionRequest req) {
        if (req.addConnections() != null) {
            for (TopologyDtos.AddConnection c : req.addConnections()) {
                String code = isBlank(c.segmentCode())
                        ? "CX-" + c.fromNodeCode() + "-" + c.toNodeCode() : c.segmentCode();
                PipeSegment seg = segmentRepo.findById(code).orElseGet(() -> {
                    PipeSegment s = new PipeSegment(code,
                            isBlank(c.name()) ? "现场探测新增连接管 " + code : c.name(),
                            c.fromNodeCode(), c.toNodeCode(),
                            c.normalPressureMinMpa() == null ? 0.18 : c.normalPressureMinMpa(),
                            c.normalPressureMaxMpa() == null ? 0.42 : c.normalPressureMaxMpa(),
                            c.pressureTimeToleranceSeconds() == null ? 600 : c.pressureTimeToleranceSeconds());
                    return s;
                });
                seg.setTopologyStatus("CONFIRMED");
                segmentRepo.save(seg);
            }
        }
        if (req.confirmSegmentCodes() != null) {
            for (String code : req.confirmSegmentCodes()) {
                PipeSegment seg = segmentRepo.findById(code)
                        .orElseThrow(() -> ApiException.badRequest("UNKNOWN_SEGMENT",
                                "在线管网表中不存在管段: " + code));
                seg.setTopologyStatus("CONFIRMED");
                segmentRepo.save(seg);
            }
        }
    }

    private List<TopologyEvidence> loadReferencedEvidence(List<Long> ids, List<String> codes) {
        List<TopologyEvidence> result = new ArrayList<>();
        if (ids != null) {
            for (Long id : ids) {
                result.add(evidenceRepo.findById(id).orElseThrow(() -> ApiException.notFound(
                        "EVIDENCE_NOT_FOUND", "现场证据不存在: id=" + id)));
            }
        }
        if (codes != null) {
            for (String code : codes) {
                TopologyEvidence e = evidenceRepo.findByCode(code)
                        .orElseThrow(() -> ApiException.notFound("EVIDENCE_NOT_FOUND",
                                "现场证据不存在: code=" + code));
                if (result.stream().noneMatch(x -> x.getId().equals(e.getId()))) {
                    result.add(e);
                }
            }
        }
        return result;
    }

    private String resolveConfirmerName(String confirmerName, String operator) {
        if (!isBlank(confirmerName)) {
            return confirmerName.trim();
        }
        return userRepo.findByUsername(operator)
                .map(UserAccount::getDisplayName).orElse(operator);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
