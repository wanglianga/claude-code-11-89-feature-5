package com.waterrepair.incident;

import com.waterrepair.common.ApiException;
import com.waterrepair.dispatch.Assignment;
import com.waterrepair.dispatch.AssignmentRepository;
import com.waterrepair.dispatch.OccupancyService;
import com.waterrepair.network.ImpactService;
import com.waterrepair.network.ValveSpecRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 压力复核。OVERTURNS 时在同一事务内完成：
 * 旧方案标记 CORRECTED、生成新版本方案、边界更新、地址范围 diff、
 * 阀门操作许可占用的释放/新占、旧通知 SUPERSEDED 与更正/取消通知下发、更正记录落库。
 * 任何一步资源互斥失败都会整体回滚，不会留下“半条更正”。
 */
@Service
public class PressureReviewService {

    private final PressureReviewRepository reviewRepo;
    private final CorrectionRecordRepository correctionRepo;
    private final IncidentRepository incidentRepo;
    private final IsolationPlanRepository planRepo;
    private final NotificationRecordRepository notificationRepo;
    private final AssignmentRepository assignmentRepo;
    private final OccupancyService occupancyService;
    private final ImpactService impactService;
    private final ValveSpecRepository valveRepo;

    public PressureReviewService(PressureReviewRepository reviewRepo,
                                 CorrectionRecordRepository correctionRepo,
                                 IncidentRepository incidentRepo,
                                 IsolationPlanRepository planRepo,
                                 NotificationRecordRepository notificationRepo,
                                 AssignmentRepository assignmentRepo,
                                 OccupancyService occupancyService,
                                 ImpactService impactService,
                                 ValveSpecRepository valveRepo) {
        this.reviewRepo = reviewRepo;
        this.correctionRepo = correctionRepo;
        this.incidentRepo = incidentRepo;
        this.planRepo = planRepo;
        this.notificationRepo = notificationRepo;
        this.assignmentRepo = assignmentRepo;
        this.occupancyService = occupancyService;
        this.impactService = impactService;
        this.valveRepo = valveRepo;
    }

    public record ReviewRequest(String verdict,
                                List<String> evidenceReadingCodes,
                                List<String> correctedBoundarySegmentCodes,
                                List<String> correctedValveCodes,
                                String note,
                                String reviewer) {
    }

    public record ReviewResult(PressureReview review, CorrectionRecord correction) {
    }

    @Transactional
    public ReviewResult submit(Long incidentId, ReviewRequest req, String operator) {
        Incident incident = incidentRepo.findById(incidentId)
                .orElseThrow(() -> ApiException.notFound("INCIDENT_NOT_FOUND", "事故不存在: " + incidentId));

        PressureReview review = new PressureReview();
        review.setIncidentId(incidentId);
        review.setVerdict(req.verdict() == null ? "SUPPORTS" : req.verdict().trim().toUpperCase());
        review.setEvidenceReadingCodes(req.evidenceReadingCodes());
        review.setCorrectedBoundarySegmentCodes(req.correctedBoundarySegmentCodes());
        review.setCorrectedValveCodes(req.correctedValveCodes());
        review.setNote(req.note());
        review.setReviewer(req.reviewer() != null ? req.reviewer() : operator);
        review.setCreatedAt(OffsetDateTime.now());
        review = reviewRepo.save(review);

        if (!"OVERTURNS".equals(review.getVerdict())) {
            return new ReviewResult(review, null);
        }

        List<String> newBoundary = req.correctedBoundarySegmentCodes();
        List<String> newValves = req.correctedValveCodes();
        if (newBoundary == null || newBoundary.isEmpty() || newValves == null || newValves.isEmpty()) {
            throw ApiException.badRequest("CORRECTION_TARGET_REQUIRED",
                    "OVERTURNS 复核必须提供 correctedBoundarySegmentCodes 与 correctedValveCodes");
        }
        ImpactService.ImpactResult newImpact = impactService.assertIsolatesBoundary(newValves, newBoundary);

        IsolationPlan oldPlan = planRepo
                .findFirstByIncidentIdAndStatusOrderByVersionNoDesc(incidentId, "ACTIVE")
                .orElse(null);

        List<String> oldBoundary = List.copyOf(incident.getBoundarySegmentCodes());
        List<String> oldValves = oldPlan != null ? List.copyOf(oldPlan.getValveCodes()) : List.of();
        List<String> oldAddresses = oldPlan != null ? List.copyOf(oldPlan.getAffectedAddressCodes()) : List.of();
        List<String> newAddresses = newImpact.affectedAddressCodes();

        Set<String> oldSet = new HashSet<>(oldAddresses);
        Set<String> newSet = new HashSet<>(newAddresses);
        List<String> added = newAddresses.stream().filter(a -> !oldSet.contains(a)).sorted().toList();
        List<String> removed = oldAddresses.stream().filter(a -> !newSet.contains(a)).sorted().toList();

        // 更正记录先落库拿到 id，供通知链引用
        CorrectionRecord correction = new CorrectionRecord();
        correction.setIncidentId(incidentId);
        correction.setRevision(incident.getRevision() + 1);
        correction.setTriggerType("PRESSURE_REVIEW");
        correction.setOldBoundarySegments(oldBoundary.stream().sorted().toList());
        correction.setNewBoundarySegments(newBoundary.stream().sorted().toList());
        correction.setOldValveCodes(oldValves.stream().sorted().toList());
        correction.setNewValveCodes(newValves.stream().sorted().toList());
        correction.setOldAddressCodes(oldAddresses.stream().sorted().toList());
        correction.setNewAddressCodes(newAddresses.stream().sorted().toList());
        correction.setAddedAddressCodes(added);
        correction.setRemovedAddressCodes(removed);
        correction.setReason(req.note());
        correction.setCreatedBy(operator);
        correction.setCreatedAt(OffsetDateTime.now());
        correction = correctionRepo.save(correction);

        // 方案版本：旧 ACTIVE 标记 CORRECTED（不删除），新增 ACTIVE
        if (oldPlan != null) {
            oldPlan.setStatus("CORRECTED");
            planRepo.save(oldPlan);
        }
        IsolationPlan newPlan = new IsolationPlan();
        newPlan.setIncidentId(incidentId);
        newPlan.setVersionNo(oldPlan == null ? 1 : oldPlan.getVersionNo() + 1);
        newPlan.setStatus("ACTIVE");
        newPlan.setValveCodes(newValves.stream().sorted().distinct().toList());
        newPlan.setOutageSegmentCodes(newImpact.outageSegmentCodes());
        newPlan.setAffectedAddressCodes(newAddresses);
        newPlan.setCreatedBy(operator);
        newPlan.setCreatedAt(OffsetDateTime.now());
        newPlan = planRepo.save(newPlan);

        incident.setBoundarySegmentCodes(newBoundary.stream().sorted().distinct().toList());
        incident.setRevision(correction.getRevision());
        incidentRepo.save(incident);

        correctResourceOccupancies(incidentId, oldValves, newValves, correction);
        correctNotifications(incident, correction, oldAddresses, newAddresses, added, removed);

        return new ReviewResult(reviewRepo.save(review), correctionRepo.save(correction));
    }

    /**
     * 在最新维修派工单上换阀票：释放不再需要的旧阀门操作许可，占用新许可。
     * 队伍/车辆/工具时间窗不变。新许可若与其他事故重叠，reserve 抛 409，整个更正回滚。
     */
    private void correctResourceOccupancies(Long incidentId, List<String> oldValves,
                                            List<String> newValves, CorrectionRecord correction) {
        List<Assignment> assignments = assignmentRepo.findByIncidentIdOrderByIdAsc(incidentId);
        Assignment repair = assignments.stream()
                .filter(a -> "REPAIR".equals(a.getKind()))
                .filter(a -> !"CANCELLED".equals(a.getStatus()))
                .max((a, b) -> a.getId().compareTo(b.getId()))
                .orElse(null);
        if (repair == null) {
            return;
        }

        List<String> oldPermits = permitsOfValves(oldValves);
        List<String> newPermits = permitsOfValves(newValves);
        List<String> removedPermits = oldPermits.stream().filter(p -> !newPermits.contains(p)).toList();
        List<String> addedPermits = newPermits.stream().filter(p -> !oldPermits.contains(p)).toList();

        List<String> released = occupancyService.releaseResources(
                repair.getId(), OccupancyService.VALVE_PERMIT, removedPermits,
                "PRESSURE_REVIEW_CORRECTION r" + correction.getRevision());
        correction.setReleasedPermitCodes(released);

        List<String> reserved = new ArrayList<>();
        for (String permit : addedPermits.stream().sorted().toList()) {
            occupancyService.reserve(repair, OccupancyService.VALVE_PERMIT, permit);
            reserved.add(permit);
        }
        correction.setReservedPermitCodes(reserved);

        repair.setValveCodes(newValves.stream().sorted().distinct().toList());
        planRepo.findFirstByIncidentIdAndStatusOrderByVersionNoDesc(incidentId, "ACTIVE")
                .ifPresent(p -> repair.setIsolationPlanId(p.getId()));
        assignmentRepo.save(repair);
    }

    private List<String> permitsOfValves(List<String> valves) {
        List<String> permits = new ArrayList<>();
        for (String valve : valves) {
            valveRepo.findById(valve).ifPresent(v -> permits.add(v.getExclusivePermitCode()));
        }
        return permits;
    }

    /**
     * 旧通知全部置 SUPERSEDED 并挂更正 id；向新范围全体地址发 CORRECTION，
     * 向被移出范围的地址发 CORRECTION_CANCEL（停水取消/无需停水）。通知只追加不删除。
     */
    private void correctNotifications(Incident incident, CorrectionRecord correction,
                                      List<String> oldAddresses, List<String> newAddresses,
                                      List<String> added, List<String> removed) {
        List<NotificationRecord> all = notificationRepo.findByIncidentIdOrderByIdAsc(incident.getId());
        OffsetDateTime now = OffsetDateTime.now();

        // 每个地址最近一条仍然 SENT 的通知，作为被替代对象
        java.util.Map<String, NotificationRecord> latestSentByAddress = new java.util.LinkedHashMap<>();
        for (NotificationRecord n : all) {
            if ("SENT".equals(n.getStatus())) {
                n.setStatus("SUPERSEDED");
                n.setSupersededByCorrectionId(correction.getId());
                latestSentByAddress.put(n.getAddressCode(), n);
            }
        }
        notificationRepo.saveAll(all);

        for (String code : newAddresses) {
            NotificationRecord n = new NotificationRecord();
            n.setIncidentId(incident.getId());
            n.setCorrectionId(correction.getId());
            n.setAddressCode(code);
            n.setKind("CORRECTION");
            n.setStatus("SENT");
            n.setChannel("SIMULATED_SMS");
            NotificationRecord prev = latestSentByAddress.get(code);
            if (prev != null) {
                n.setReplacesNotificationId(prev.getId());
            }
            boolean isAdded = added.contains(code);
            n.setContent("【停水范围更正通知】" + incident.getCode()
                    + " 经压力复核，隔离边界更正为 " + correction.getNewBoundarySegments()
                    + "，阀门 " + correction.getNewValveCodes()
                    + "；您的地址 " + code + (isAdded ? " 新纳入停水范围" : " 仍在更正后的停水范围")
                    + "。此前通知编号 " + (prev == null ? "无" : prev.getId()) + " 同时作废，请以本通知为准。");
            n.setSentAt(now);
            notificationRepo.save(n);
        }

        for (String code : removed) {
            NotificationRecord n = new NotificationRecord();
            n.setIncidentId(incident.getId());
            n.setCorrectionId(correction.getId());
            n.setAddressCode(code);
            n.setKind("CORRECTION_CANCEL");
            n.setStatus("SENT");
            n.setChannel("SIMULATED_SMS");
            NotificationRecord prev = latestSentByAddress.get(code);
            if (prev != null) {
                n.setReplacesNotificationId(prev.getId());
            }
            n.setContent("【停水更正-取消】" + incident.getCode()
                    + " 压力复核推翻了原判断，原计划关闭 " + correction.getOldValveCodes()
                    + " 影响您地址 " + code + " 的安排有误，您所在地址无需按原计划停水；"
                    + "原通知编号 " + (prev == null ? "无" : prev.getId()) + " 作废，特此更正。");
            n.setSentAt(now);
            notificationRepo.save(n);
        }
    }

    @Transactional(readOnly = true)
    public List<PressureReview> reviews(Long incidentId) {
        return reviewRepo.findByIncidentIdOrderByIdAsc(incidentId);
    }

    @Transactional(readOnly = true)
    public List<CorrectionRecord> corrections(Long incidentId) {
        return correctionRepo.findByIncidentIdOrderByRevisionAscIdAsc(incidentId);
    }
}
