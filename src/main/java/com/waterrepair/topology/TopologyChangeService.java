package com.waterrepair.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waterrepair.common.ApiException;
import com.waterrepair.incident.Incident;
import com.waterrepair.incident.IncidentRepository;
import com.waterrepair.incident.IsolationPlan;
import com.waterrepair.incident.IsolationPlanRepository;
import com.waterrepair.incident.NotificationRecord;
import com.waterrepair.incident.NotificationRecordRepository;
import com.waterrepair.network.ImpactService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 拓扑新版本影响评估与人工重审：
 * 找出引用旧版本的开放事故，对比旧阀门组合/地址集合/已通知对象与新版本候选组合，
 * 生成 PENDING_CONFIRMATION 重审单；调度确认后才更新计划版本，绝不自动改动旧通知。
 */
@Service
public class TopologyChangeService {

    private final TopologyChangeReviewRepository reviewRepo;
    private final TopologyVersionRepository versionRepo;
    private final IncidentRepository incidentRepo;
    private final IsolationPlanRepository planRepo;
    private final NotificationRecordRepository notificationRepo;
    private final TopologyQueryService topologyQuery;
    private final ObjectMapper objectMapper;

    public TopologyChangeService(TopologyChangeReviewRepository reviewRepo,
                                 TopologyVersionRepository versionRepo,
                                 IncidentRepository incidentRepo,
                                 IsolationPlanRepository planRepo,
                                 NotificationRecordRepository notificationRepo,
                                 TopologyQueryService topologyQuery,
                                 ObjectMapper objectMapper) {
        this.reviewRepo = reviewRepo;
        this.versionRepo = versionRepo;
        this.incidentRepo = incidentRepo;
        this.planRepo = planRepo;
        this.notificationRepo = notificationRepo;
        this.topologyQuery = topologyQuery;
        this.objectMapper = objectMapper;
    }

    // ---------------- 发布后评估 ----------------

    @Transactional
    public List<TopologyChangeReview> assessOpenIncidents(String newVersionCode) {
        TopologyVersion newVersion = topologyQuery.mustVersion(newVersionCode);
        TopologyDtos.Snapshot newSnap = topologyQuery.readSnapshot(newVersion.getSnapshotJson());

        List<TopologyChangeReview> created = new ArrayList<>();
        for (Incident inc : incidentRepo.findAll()) {
            if ("CLOSED".equals(inc.getStatus())) {
                continue; // 已结案事故按旧图复演，不产生重审单
            }
            String oldVersionCode = inc.getTopologyVersionCode() == null
                    ? TopologyQueryService.BASELINE_VERSION
                    : inc.getTopologyVersionCode();

            IsolationPlan oldPlan = planRepo
                    .findFirstByIncidentIdAndStatusOrderByVersionNoDesc(inc.getId(), "ACTIVE")
                    .orElse(null);
            List<String> oldValves = oldPlan == null ? List.of() : oldPlan.getValveCodes();
            ImpactService.ImpactResult oldImpact =
                    topologyQuery.evaluate(oldVersionCode, oldValves);
            List<String> oldAddresses = oldPlan == null
                    ? oldImpact.affectedAddressCodes()
                    : oldPlan.getAffectedAddressCodes();

            List<ImpactService.CandidateCombo> newCombos =
                    TopologyGraphEvaluator.candidateCombos(newSnap, inc.getBoundarySegmentCodes());
            List<String> recommended = newCombos.isEmpty() ? List.of() : newCombos.get(0).valveCodes();
            Set<String> newRecommendedAddresses = newCombos.isEmpty()
                    ? Set.of()
                    : new HashSet<>(newCombos.get(0).affectedAddressCodes());

            // 旧阀门组合在新图下是否仍能隔离边界
            boolean oldComboStillIsolates;
            try {
                ImpactService.ImpactResult underNew =
                        TopologyGraphEvaluator.evaluate(newSnap, oldValves);
                oldComboStillIsolates = new HashSet<>(underNew.outageSegmentCodes())
                        .containsAll(inc.getBoundarySegmentCodes());
            } catch (Exception ex) {
                oldComboStillIsolates = false;
            }

            boolean sameSet = newRecommendedAddresses.equals(new HashSet<>(oldAddresses));
            boolean affected = oldPlan != null && (!oldComboStillIsolates || !sameSet);
            if (!affected) {
                continue;
            }

            Set<String> uncertain = new HashSet<>();
            List<TopologyDtos.UnknownBranch> branches = newSnap.unknownBranches() == null
                    ? List.of() : newSnap.unknownBranches();
            for (TopologyDtos.UnknownBranch ub : branches) {
                if (ub.candidateAddressCodes() != null) {
                    uncertain.addAll(ub.candidateAddressCodes());
                }
            }

            Set<String> oldSet = new HashSet<>(oldAddresses);
            List<String> added = newRecommendedAddresses.stream()
                    .filter(a -> !oldSet.contains(a)).sorted().toList();
            List<String> removed = oldAddresses.stream()
                    .filter(a -> !newRecommendedAddresses.contains(a)).sorted().toList();

            TopologyChangeReview review = new TopologyChangeReview();
            review.setTopologyVersionCode(newVersionCode);
            review.setIncidentId(inc.getId());
            review.setIncidentCode(inc.getCode());
            review.setOldTopologyVersionCode(oldVersionCode);
            review.setStatus("PENDING_CONFIRMATION");
            review.setAffected(true);
            review.setOldPlanId(oldPlan.getId());
            review.setOldValveCodes(oldValves.stream().sorted().toList());
            review.setOldOutageSegmentCodes(oldPlan == null
                    ? oldImpact.outageSegmentCodes() : oldPlan.getOutageSegmentCodes());
            review.setOldAddressCodes(oldAddresses.stream().sorted().toList());
            review.setRecommendedValveCodes(recommended);
            review.setAddedAddressCodes(added);
            review.setRemovedAddressCodes(removed);
            review.setUncertainAddressCodes(uncertain.stream().sorted().toList());
            review.setCandidateCombosJson(writeJson(newCombos));
            review.setNotifiedObjectsJson(writeJson(notifiedSnapshot(inc.getId())));
            review.setCreatedAt(OffsetDateTime.now());
            TopologyChangeReview saved = reviewRepo.saveAndFlush(review);
            saved.setCode(String.format("TCR-%04d", saved.getId()));
            created.add(reviewRepo.save(saved));
        }
        return created;
    }

    private List<Map<String, Object>> notifiedSnapshot(Long incidentId) {
        List<Map<String, Object>> objects = new ArrayList<>();
        for (NotificationRecord n : notificationRepo.findByIncidentIdOrderByIdAsc(incidentId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("notificationId", n.getId());
            m.put("addressCode", n.getAddressCode());
            m.put("kind", n.getKind());
            m.put("status", n.getStatus());
            m.put("channel", n.getChannel());
            m.put("planId", n.getPlanId());
            m.put("sentAt", n.getSentAt());
            objects.add(m);
        }
        return objects;
    }

    // ---------------- 查询视图 ----------------

    public record ReviewView(Long id, String code, String topologyVersionCode,
                             Long incidentId, String incidentCode,
                             String oldTopologyVersionCode, String status, Boolean affected,
                             Long oldPlanId,
                             List<String> oldValveCodes,
                             List<String> oldOutageSegmentCodes,
                             List<String> oldAddressCodes,
                             List<String> recommendedValveCodes,
                             List<String> addedAddressCodes,
                             List<String> removedAddressCodes,
                             List<String> uncertainAddressCodes,
                             List<String> confirmedValveCodes,
                             Long newPlanId,
                             String confirmedBy, OffsetDateTime confirmedAt,
                             String confirmationNote,
                             String dismissedBy, OffsetDateTime dismissedAt,
                             String dismissReason, OffsetDateTime createdAt,
                             Object candidateCombos,
                             Object notifiedObjects) {
        static ReviewView from(TopologyChangeReview r, Object combos, Object notified) {
            return new ReviewView(r.getId(), r.getCode(), r.getTopologyVersionCode(),
                    r.getIncidentId(), r.getIncidentCode(), r.getOldTopologyVersionCode(),
                    r.getStatus(), r.getAffected(), r.getOldPlanId(),
                    List.copyOf(r.getOldValveCodes()), List.copyOf(r.getOldOutageSegmentCodes()),
                    List.copyOf(r.getOldAddressCodes()), List.copyOf(r.getRecommendedValveCodes()),
                    List.copyOf(r.getAddedAddressCodes()), List.copyOf(r.getRemovedAddressCodes()),
                    List.copyOf(r.getUncertainAddressCodes()),
                    List.copyOf(r.getConfirmedValveCodes()), r.getNewPlanId(),
                    r.getConfirmedBy(), r.getConfirmedAt(), r.getConfirmationNote(),
                    r.getDismissedBy(), r.getDismissedAt(), r.getDismissReason(), r.getCreatedAt(),
                    combos, notified);
        }
    }

    @Transactional(readOnly = true)
    public List<TopologyChangeReview> listReviews(String status) {
        List<TopologyChangeReview> all = reviewRepo.findAllByOrderByIdAsc();
        if (status == null || status.isBlank()) {
            return all;
        }
        return all.stream().filter(r -> status.equalsIgnoreCase(r.getStatus())).toList();
    }

    @Transactional(readOnly = true)
    public List<TopologyChangeReview> incidentReviews(Long incidentId) {
        return reviewRepo.findByIncidentIdOrderByIdDesc(incidentId);
    }

    @Transactional(readOnly = true)
    public ReviewView reviewView(Long id) {
        TopologyChangeReview r = mustReview(id);
        return ReviewView.from(r, readJson(r.getCandidateCombosJson()),
                readJson(r.getNotifiedObjectsJson()));
    }

    @Transactional(readOnly = true)
    public List<ReviewView> reviewViews(String status) {
        return listReviews(status).stream()
                .map(r -> ReviewView.from(r, readJson(r.getCandidateCombosJson()),
                        readJson(r.getNotifiedObjectsJson())))
                .toList();
    }

    public TopologyChangeReview mustReview(Long id) {
        return reviewRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("TOPOLOGY_REVIEW_NOT_FOUND",
                        "拓扑变更人工重审单不存在: " + id));
    }

    // ---------------- 调度确认 / 排除 ----------------

    @Transactional
    public ReviewView confirm(Long reviewId, List<String> requestedValveCodes,
                              String note, String operator) {
        TopologyChangeReview review = mustReview(reviewId);
        if (!"PENDING_CONFIRMATION".equals(review.getStatus())) {
            throw ApiException.conflict("TOPOLOGY_REVIEW_ALREADY_DECIDED",
                    "重审单已处理，当前状态: " + review.getStatus(),
                    Map.of("reviewId", reviewId, "status", review.getStatus()));
        }
        Incident inc = incidentRepo.findById(review.getIncidentId())
                .orElseThrow(() -> ApiException.notFound("INCIDENT_NOT_FOUND",
                        "事故不存在: " + review.getIncidentId()));
        if ("CLOSED".equals(inc.getStatus())) {
            throw ApiException.conflict("INCIDENT_CLOSED", "事故已结案，不能再按拓扑变更更新计划", null);
        }

        List<String> valves = (requestedValveCodes == null || requestedValveCodes.isEmpty())
                ? review.getRecommendedValveCodes()
                : requestedValveCodes.stream().sorted().distinct().toList();
        if (valves.isEmpty()) {
            throw ApiException.unprocessable("NO_VALVE_COMBO",
                    "没有可确认的阀门组合，请显式指定 valveCodes", null);
        }
        // 必须在新版本拓扑下确实隔离边界
        ImpactService.ImpactResult impact = topologyQuery.assertIsolates(
                review.getTopologyVersionCode(), valves, inc.getBoundarySegmentCodes());

        List<IsolationPlan> plans =
                planRepo.findByIncidentIdOrderByVersionNoAscIdAsc(inc.getId());
        for (IsolationPlan p : plans) {
            if ("ACTIVE".equals(p.getStatus())) {
                p.setStatus("SUPERSEDED");
                planRepo.save(p);
            }
        }
        IsolationPlan plan = new IsolationPlan();
        plan.setIncidentId(inc.getId());
        plan.setVersionNo(plans.size() + 1);
        plan.setStatus("ACTIVE");
        plan.setValveCodes(valves);
        plan.setOutageSegmentCodes(impact.outageSegmentCodes());
        plan.setAffectedAddressCodes(impact.affectedAddressCodes());
        plan.setCreatedBy(operator);
        plan.setCreatedAt(OffsetDateTime.now());
        plan = planRepo.saveAndFlush(plan);

        inc.setTopologyVersionCode(review.getTopologyVersionCode());
        inc.setRevision(inc.getRevision() == null ? 1 : inc.getRevision() + 1);
        incidentRepo.save(inc);

        review.setStatus("CONFIRMED");
        review.setConfirmedValveCodes(valves);
        review.setNewPlanId(plan.getId());
        review.setConfirmedBy(operator);
        review.setConfirmedAt(OffsetDateTime.now());
        review.setConfirmationNote(note);
        reviewRepo.save(review);
        return reviewView(review.getId());
    }

    @Transactional
    public ReviewView dismiss(Long reviewId, String reason, String operator) {
        TopologyChangeReview review = mustReview(reviewId);
        if (!"PENDING_CONFIRMATION".equals(review.getStatus())) {
            throw ApiException.conflict("TOPOLOGY_REVIEW_ALREADY_DECIDED",
                    "重审单已处理，当前状态: " + review.getStatus(), null);
        }
        review.setStatus("DISMISSED");
        review.setDismissedBy(operator);
        review.setDismissedAt(OffsetDateTime.now());
        review.setDismissReason(reason);
        reviewRepo.save(review);
        return reviewView(review.getId());
    }

    // ---------------- 显式增量通知（不自动发生） ----------------

    public record DeltaNotifyResult(int sentCount, boolean alreadyNotified,
                                    List<NotificationRecord> notifications) {
    }

    @Transactional
    public DeltaNotifyResult notifyAddedAddresses(Long reviewId, String operator) {
        TopologyChangeReview review = mustReview(reviewId);
        if (!"CONFIRMED".equals(review.getStatus()) || review.getNewPlanId() == null) {
            throw ApiException.conflict("TOPOLOGY_REVIEW_NOT_CONFIRMED",
                    "必须先由调度确认新隔离计划，才能对新增地址发通知", null);
        }
        Long incidentId = review.getIncidentId();
        Set<String> alreadyExpansion = new HashSet<>();
        List<NotificationRecord> expansionRecords = new ArrayList<>();
        for (NotificationRecord n : notificationRepo.findByIncidentIdOrderByIdAsc(incidentId)) {
            if ("TOPOLOGY_EXPANSION".equals(n.getKind())) {
                alreadyExpansion.add(n.getAddressCode());
                expansionRecords.add(n);
            }
        }
        List<String> targets = review.getAddedAddressCodes().stream()
                .filter(a -> !alreadyExpansion.contains(a)).toList();
        int sent = 0;
        for (String code : targets) {
            NotificationRecord n = new NotificationRecord();
            n.setIncidentId(incidentId);
            n.setPlanId(review.getNewPlanId());
            n.setAddressCode(code);
            n.setKind("TOPOLOGY_EXPANSION");
            n.setStatus("SENT");
            n.setChannel("SIMULATED_SMS");
            n.setContent("【供水抢修范围扩大通知】拓扑修订 " + review.getTopologyVersionCode()
                    + " 经复核确认，原工单 " + review.getIncidentCode()
                    + " 的隔离范围扩大，您的地址 " + code
                    + " 新纳入停水范围，请提前储水。原通知继续有效。");
            n.setSentAt(OffsetDateTime.now());
            notificationRepo.save(n);
            sent++;
        }
        boolean already = sent == 0;
        List<NotificationRecord> all = new ArrayList<>(expansionRecords);
        if (sent > 0) {
            all = notificationRepo.findByIncidentIdOrderByIdAsc(incidentId).stream()
                    .filter(n -> "TOPOLOGY_EXPANSION".equals(n.getKind())).toList();
        }
        return new DeltaNotifyResult(sent, already, all);
    }

    // ---------------- 旧案复演 ----------------

    public record ReplayView(Long incidentId, String incidentCode, String incidentStatus,
                             String adoptedTopologyVersion, Long planId, Integer planVersionNo,
                             List<String> planValveCodes, List<String> storedAddressCodes,
                             ImpactService.ImpactResult adoptedVersionEvaluation,
                             boolean reproducesStoredPlan,
                             String latestTopologyVersion,
                             ImpactService.ImpactResult latestVersionEvaluation,
                             boolean latestReproducesStoredPlan,
                             List<String> latestDriftAddressCodes) {
    }

    @Transactional(readOnly = true)
    public ReplayView replayIncident(Long incidentId) {
        Incident inc = incidentRepo.findById(incidentId)
                .orElseThrow(() -> ApiException.notFound("INCIDENT_NOT_FOUND",
                        "事故不存在: " + incidentId));
        String pinned = inc.getTopologyVersionCode() == null
                ? TopologyQueryService.BASELINE_VERSION : inc.getTopologyVersionCode();
        IsolationPlan plan = planRepo
                .findFirstByIncidentIdAndStatusOrderByVersionNoDesc(incidentId, "ACTIVE")
                .or(() -> planRepo.findByIncidentIdOrderByVersionNoAscIdAsc(incidentId).stream()
                        .reduce((a, b) -> b))
                .orElseThrow(() -> ApiException.conflict("NO_PLAN",
                        "该事故没有隔离方案，无法按拓扑版本复演", null));

        ImpactService.ImpactResult pinnedEval = topologyQuery.evaluate(pinned, plan.getValveCodes());
        List<String> stored = plan.getAffectedAddressCodes();
        boolean reproduces = new HashSet<>(pinnedEval.affectedAddressCodes())
                .equals(new HashSet<>(stored));

        String latest = topologyQuery.currentActiveVersionCode();
        ImpactService.ImpactResult latestEval = topologyQuery.evaluate(latest, plan.getValveCodes());
        boolean latestReproduces = new HashSet<>(latestEval.affectedAddressCodes())
                .equals(new HashSet<>(stored));
        List<String> drift = latestEval.affectedAddressCodes().stream()
                .filter(a -> !new HashSet<>(stored).contains(a)).sorted().toList();

        return new ReplayView(inc.getId(), inc.getCode(), inc.getStatus(), pinned,
                plan.getId(), plan.getVersionNo(), plan.getValveCodes(), stored,
                pinnedEval, reproduces, latest, latestEval, latestReproduces, drift);
    }

    // ---------------- helpers ----------------

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("JSON 序列化失败: " + ex.getMessage(), ex);
        }
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            return Map.of("parseError", ex.getMessage());
        }
    }
}
