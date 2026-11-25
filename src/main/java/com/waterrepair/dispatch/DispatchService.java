package com.waterrepair.dispatch;

import com.waterrepair.common.ApiException;
import com.waterrepair.incident.Incident;
import com.waterrepair.incident.IncidentRepository;
import com.waterrepair.incident.IsolationPlan;
import com.waterrepair.incident.IsolationPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class DispatchService {

    private final AssignmentRepository assignmentRepo;
    private final ResourceOccupancyRepository occupancyRepo;
    private final OccupancyService occupancyService;
    private final EligibilityService eligibilityService;
    private final IncidentRepository incidentRepo;
    private final IsolationPlanRepository planRepo;

    public DispatchService(AssignmentRepository assignmentRepo,
                           ResourceOccupancyRepository occupancyRepo,
                           OccupancyService occupancyService,
                           EligibilityService eligibilityService,
                           IncidentRepository incidentRepo,
                           IsolationPlanRepository planRepo) {
        this.assignmentRepo = assignmentRepo;
        this.occupancyRepo = occupancyRepo;
        this.occupancyService = occupancyService;
        this.eligibilityService = eligibilityService;
        this.incidentRepo = incidentRepo;
        this.planRepo = planRepo;
    }

    @Transactional(readOnly = true)
    public EligibilityService.EligibilityResult checkEligibility(Long incidentId,
                                                                 DispatchDtos.EligibilityRequest req) {
        Incident incident = mustGetIncident(incidentId);
        String kind = req.kind() == null ? "REPAIR" : req.kind();
        List<String> valves = "REPAIR".equals(kind) ? activePlanValves(incident) : List.of();
        return eligibilityService.evaluate(req.crewCode(), kind, req.windowStart(), req.windowEnd(), valves);
    }

    @Transactional
    public Assignment dispatchScout(Long incidentId, DispatchDtos.DispatchRequest req, String operator) {
        Incident incident = mustGetIncident(incidentId);
        validateWindow(req);

        EligibilityService.EligibilityResult elig = eligibilityService.evaluate(
                req.crewCode(), "SCOUT", req.windowStart(), req.windowEnd(), List.of());
        if (!elig.eligible()) {
            throw ApiException.unprocessable("DISPATCH_ELIGIBILITY_FAILED",
                    "勘查班组不满足接单条件", java.util.Map.of("checks", elig.checks()));
        }

        Assignment a = newAssignment(incident, "SCOUT",
                req.strategy() == null ? "SCOUT_FIRST" : req.strategy(), req, operator);
        a.setStatus("DISPATCHED");
        assignmentRepo.saveAndFlush(a);
        a.setCode(String.format("ASG-%04d", a.getId()));

        occupancyService.reserve(a, OccupancyService.CREW, elig.crewCode());
        if (elig.vehicleCode() != null) {
            occupancyService.reserve(a, OccupancyService.VEHICLE, elig.vehicleCode());
        }
        for (String tool : elig.toolCodes()) {
            occupancyService.reserve(a, OccupancyService.TOOL, tool);
        }

        incident.setStatus("SCOUT_DISPATCHED");
        incidentRepo.save(incident);
        return assignmentRepo.save(a);
    }

    @Transactional
    public Assignment dispatchRepair(Long incidentId, DispatchDtos.DispatchRequest req, String operator) {
        Incident incident = mustGetIncident(incidentId);
        validateWindow(req);
        IsolationPlan plan = activePlan(incident);

        EligibilityService.EligibilityResult elig = eligibilityService.evaluate(
                req.crewCode(), "REPAIR", req.windowStart(), req.windowEnd(), plan.getValveCodes());
        if (!elig.eligible()) {
            throw ApiException.unprocessable("DISPATCH_ELIGIBILITY_FAILED",
                    "维修班组不满足许可/当班/车辆/工具条件", java.util.Map.of("checks", elig.checks()));
        }

        long repairCount = assignmentRepo.findByIncidentIdOrderByIdAsc(incidentId).stream()
                .filter(a -> "REPAIR".equals(a.getKind()) && !"CANCELLED".equals(a.getStatus()))
                .count();

        Assignment a = newAssignment(incident, "REPAIR",
                req.strategy() == null ? "LOCK_AFTER_SCOUT" : req.strategy(), req, operator);
        a.setAttemptNo((int) repairCount + 1);
        a.setCycleNo(incident.getCurrentCycle());
        a.setIsolationPlanId(plan.getId());
        a.setValveCodes(plan.getValveCodes());
        boolean holdOnly = Boolean.TRUE.equals(req.holdOnly());
        a.setStatus(holdOnly ? "PROVISIONAL" : "DISPATCHED");
        assignmentRepo.saveAndFlush(a);
        a.setCode(String.format("ASG-%04d", a.getId()));

        reserveRepairResources(a, elig);

        incident.setStatus(holdOnly ? "REPAIR_RESERVED" : "REPAIR_DISPATCHED");
        incidentRepo.save(incident);
        return assignmentRepo.save(a);
    }

    /**
     * 复压失败后的重新派工：释放上一维修派工单的全部 HELD 占用（历史派工单与回执保留），
     * 事故进入新回执周期，创建新的 REPAIR 派工单并重新做资格与互斥校验。
     */
    @Transactional
    public Assignment redispatch(Long incidentId, DispatchDtos.DispatchRequest req, String operator) {
        Incident incident = mustGetIncident(incidentId);
        validateWindow(req);
        IsolationPlan plan = activePlan(incident);

        List<Assignment> assignments = assignmentRepo.findByIncidentIdOrderByIdAsc(incidentId);
        Assignment previousRepair = assignments.stream()
                .filter(a -> "REPAIR".equals(a.getKind()))
                .max(Comparator.comparing(Assignment::getId))
                .orElseThrow(() -> ApiException.conflict("NO_REPAIR_ASSIGNMENT",
                        "该事故还没有维修派工单，不能重新派工", null));

        int released = occupancyService.releaseByAssignment(previousRepair.getId(),
                "REDISPATCH_AFTER_REPRESSURE_FAILURE");

        EligibilityService.EligibilityResult elig = eligibilityService.evaluate(
                req.crewCode(), "REPAIR", req.windowStart(), req.windowEnd(), plan.getValveCodes());
        if (!elig.eligible()) {
            throw ApiException.unprocessable("DISPATCH_ELIGIBILITY_FAILED",
                    "重新派工的班组不满足条件", java.util.Map.of("checks", elig.checks()));
        }

        int nextCycle = incident.getCurrentCycle() + 1;
        incident.setCurrentCycle(nextCycle);

        long repairCount = assignments.stream()
                .filter(a -> "REPAIR".equals(a.getKind()) && !"CANCELLED".equals(a.getStatus()))
                .count();
        Assignment a = newAssignment(incident, "REPAIR", "REDISPATCH", req, operator);
        a.setAttemptNo((int) repairCount + 1);
        a.setCycleNo(nextCycle);
        a.setParentAssignmentId(previousRepair.getId());
        a.setIsolationPlanId(plan.getId());
        a.setValveCodes(plan.getValveCodes());
        a.setStatus(Boolean.TRUE.equals(req.holdOnly()) ? "PROVISIONAL" : "DISPATCHED");
        a.setNote((req.note() == null ? "" : req.note())
                + " [redispatch after assignment " + previousRepair.getCode()
                + ", releasedOccupancies=" + released + "]");
        assignmentRepo.saveAndFlush(a);
        a.setCode(String.format("ASG-%04d", a.getId()));

        reserveRepairResources(a, elig);

        incident.setStatus("REPAIR_DISPATCHED");
        incidentRepo.save(incident);
        return assignmentRepo.save(a);
    }

    private void reserveRepairResources(Assignment a, EligibilityService.EligibilityResult elig) {
        // 队伍先锁定：同一唯一持证班组的并发派单在此确定性冲突
        occupancyService.reserve(a, OccupancyService.CREW, elig.crewCode());
        if (elig.vehicleCode() != null) {
            occupancyService.reserve(a, OccupancyService.VEHICLE, elig.vehicleCode());
        }
        for (String tool : elig.toolCodes().stream().sorted().toList()) {
            occupancyService.reserve(a, OccupancyService.TOOL, tool);
        }
        for (String permit : elig.permitCodes().stream().sorted().toList()) {
            occupancyService.reserve(a, OccupancyService.VALVE_PERMIT, permit);
        }
    }

    @Transactional
    public Assignment issue(Long assignmentId) {
        Assignment a = mustGetAssignment(assignmentId);
        if (!"PROVISIONAL".equals(a.getStatus())) {
            throw ApiException.conflict("ASSIGNMENT_NOT_PROVISIONAL",
                    "只有 PROVISIONAL 暂留派工单可以下达，当前状态: " + a.getStatus(), null);
        }
        a.setStatus("DISPATCHED");
        return assignmentRepo.save(a);
    }

    @Transactional
    public Assignment complete(Long assignmentId, String reason) {
        Assignment a = mustGetAssignment(assignmentId);
        if ("CANCELLED".equals(a.getStatus())) {
            throw ApiException.conflict("ASSIGNMENT_CANCELLED", "已取消的派工单不能完成", null);
        }
        occupancyService.releaseByAssignment(assignmentId,
                "ASSIGNMENT_COMPLETED" + (reason == null ? "" : ": " + reason));
        a.setStatus("COMPLETED");
        return assignmentRepo.save(a);
    }

    @Transactional
    public Assignment cancel(Long assignmentId, String reason) {        Assignment a = mustGetAssignment(assignmentId);
        if ("CANCELLED".equals(a.getStatus()) || "COMPLETED".equals(a.getStatus())) {
            throw ApiException.conflict("ASSIGNMENT_TERMINAL",
                    "派工单已处于终态 " + a.getStatus() + "，不能取消", null);
        }
        occupancyService.releaseByAssignment(assignmentId,
                "CANCELLED" + (reason == null ? "" : ": " + reason));
        a.setStatus("CANCELLED");
        return assignmentRepo.save(a);
    }

    @Transactional(readOnly = true)
    public List<Assignment> listForIncident(Long incidentId) {
        mustGetIncident(incidentId);
        return assignmentRepo.findByIncidentIdOrderByIdAsc(incidentId);
    }

    @Transactional(readOnly = true)
    public Assignment mustGetAssignment(Long id) {
        return assignmentRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("ASSIGNMENT_NOT_FOUND", "派工单不存在: " + id));
    }

    @Transactional(readOnly = true)
    public List<ResourceOccupancy> occupancies(Long assignmentId) {
        mustGetAssignment(assignmentId);
        return occupancyRepo.findByAssignmentIdOrderByIdAsc(assignmentId);
    }

    // ---------- helpers ----------

    private Assignment newAssignment(Incident incident, String kind, String strategy,
                                     DispatchDtos.DispatchRequest req, String operator) {
        Assignment a = new Assignment();
        a.setIncidentId(incident.getId());
        a.setKind(kind);
        a.setStrategy(strategy);
        a.setCrewCode(req.crewCode());
        a.setWindowStart(req.windowStart());
        a.setWindowEnd(req.windowEnd());
        a.setParentAssignmentId(req.parentAssignmentId());
        a.setNote(req.note());
        a.setCreatedBy(operator);
        a.setCreatedAt(OffsetDateTime.now());
        a.setAttemptNo(1);
        a.setCycleNo(incident.getCurrentCycle());
        return a;
    }

    private void validateWindow(DispatchDtos.DispatchRequest req) {
        if (req.crewCode() == null || req.crewCode().isBlank()) {
            throw ApiException.badRequest("CREW_REQUIRED", "crewCode 不能为空");
        }
        if (req.windowStart() == null || req.windowEnd() == null
                || !req.windowEnd().isAfter(req.windowStart())) {
            throw ApiException.badRequest("INVALID_WINDOW", "windowEnd 必须晚于 windowStart");
        }
    }

    private Incident mustGetIncident(Long id) {
        return incidentRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("INCIDENT_NOT_FOUND", "事故不存在: " + id));
    }

    private IsolationPlan activePlan(Incident incident) {
        return planRepo.findFirstByIncidentIdAndStatusOrderByVersionNoDesc(incident.getId(), "ACTIVE")
                .orElseThrow(() -> ApiException.conflict("NO_ACTIVE_PLAN",
                        "事故尚未确认隔离方案，不能派出维修力量", null));
    }

    private List<String> activePlanValves(Incident incident) {
        return activePlan(incident).getValveCodes();
    }
}
