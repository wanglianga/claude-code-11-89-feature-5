package com.waterrepair.receipt;

import com.waterrepair.common.ApiException;
import com.waterrepair.dispatch.Assignment;
import com.waterrepair.dispatch.AssignmentRepository;
import com.waterrepair.dispatch.OccupancyService;
import com.waterrepair.incident.Incident;
import com.waterrepair.incident.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ReceiptService {

    public static final List<String> SEQUENCE =
            List.of("ARRIVAL", "ISOLATION", "REPAIR", "FLUSH", "REPRESSURE");

    private final FieldReceiptRepository receiptRepo;
    private final IncidentRepository incidentRepo;
    private final AssignmentRepository assignmentRepo;
    private final OccupancyService occupancyService;
    private final PressureVerificationService verificationService;

    public ReceiptService(FieldReceiptRepository receiptRepo,
                          IncidentRepository incidentRepo,
                          AssignmentRepository assignmentRepo,
                          OccupancyService occupancyService,
                          PressureVerificationService verificationService) {
        this.receiptRepo = receiptRepo;
        this.incidentRepo = incidentRepo;
        this.assignmentRepo = assignmentRepo;
        this.occupancyService = occupancyService;
        this.verificationService = verificationService;
    }

    @Transactional
    public FieldReceipt post(Long incidentId, ReceiptDtos.CreateReceiptRequest req, String operator) {
        Incident incident = incidentRepo.findById(incidentId)
                .orElseThrow(() -> ApiException.notFound("INCIDENT_NOT_FOUND", "事故不存在: " + incidentId));
        if ("CLOSED".equals(incident.getStatus())) {
            throw ApiException.conflict("INCIDENT_CLOSED", "事故已结案，不能再提交回执", null);
        }
        Assignment assignment = assignmentRepo.findById(req.assignmentId())
                .orElseThrow(() -> ApiException.notFound("ASSIGNMENT_NOT_FOUND",
                        "派工单不存在: " + req.assignmentId()));
        if (!assignment.getIncidentId().equals(incidentId)) {
            throw ApiException.badRequest("ASSIGNMENT_INCIDENT_MISMATCH", "派工单不属于该事故");
        }
        if ("CANCELLED".equals(assignment.getStatus())) {
            throw ApiException.conflict("ASSIGNMENT_CANCELLED", "不能在已取消的派工单上提交回执", null);
        }
        if (!assignment.getCycleNo().equals(incident.getCurrentCycle())) {
            throw ApiException.conflict("RECEIPT_WRONG_CYCLE",
                    "派工单属于回执周期 " + assignment.getCycleNo() + "，当前周期为 "
                            + incident.getCurrentCycle() + "；历史周期回执已封存",
                    Map.of("assignmentCycle", assignment.getCycleNo(),
                            "currentCycle", incident.getCurrentCycle()));
        }

        String type = req.type() == null ? "" : req.type().trim().toUpperCase();
        int requestedIndex = SEQUENCE.indexOf(type);
        if (requestedIndex < 0) {
            throw ApiException.badRequest("UNKNOWN_RECEIPT_TYPE",
                    "回执类型必须是 " + SEQUENCE + " 之一");
        }

        int cycle = incident.getCurrentCycle();
        List<FieldReceipt> chain =
                receiptRepo.findByIncidentIdAndCycleNoOrderByIdAsc(incidentId, cycle);
        if (chain.size() >= SEQUENCE.size()) {
            throw ApiException.conflict("RECEIPT_CHAIN_COMPLETE",
                    "本周期五类回执已齐全；如复压未通过请先重新派工开启新周期", null);
        }
        String expected = SEQUENCE.get(chain.size());
        if (!expected.equals(type)) {
            throw ApiException.conflict("RECEIPT_ORDER_CONFLICT",
                    "回执顺序错误：当前周期第 " + (chain.size() + 1) + " 步应为 " + expected
                            + "，实际提交 " + type,
                    Map.of("cycle", cycle, "expected", expected, "actual", type,
                            "completedTypes", chain.stream().map(FieldReceipt::getType).toList()));
        }

        if (requestedIndex >= 1 && !"REPAIR".equals(assignment.getKind())) {
            throw ApiException.unprocessable("REPAIR_ASSIGNMENT_REQUIRED",
                    type + " 必须由持有阀门/维修许可的 REPAIR 派工单提交，勘查单只能提交 ARRIVAL",
                    Map.of("assignmentKind", assignment.getKind()));
        }

        FieldReceipt receipt = new FieldReceipt();
        receipt.setIncidentId(incidentId);
        receipt.setAssignmentId(assignment.getId());
        receipt.setAssignmentCode(assignment.getCode());
        receipt.setCrewCode(assignment.getCrewCode());
        receipt.setCycleNo(cycle);
        receipt.setStepIndex(requestedIndex);
        receipt.setType(type);
        receipt.setEventAt(req.eventAt());
        receipt.setNote(req.note());
        receipt.setCreatedBy(operator);
        receipt.setCreatedAt(OffsetDateTime.now());
        receipt.setVerificationStatus("NOT_REQUIRED");

        if ("REPRESSURE".equals(type)) {
            applyVerification(receipt, req.readingCode(), req.eventAt(), incident);
        }

        FieldReceipt saved = receiptRepo.save(receipt);

        assignment.setStatus("ACTIVE");
        assignmentRepo.save(assignment);

        switch (type) {
            case "ARRIVAL" -> incident.setStatus("ON_SITE");
            case "ISOLATION" -> incident.setStatus("ISOLATED");
            case "REPAIR" -> incident.setStatus("REPAIRED");
            case "FLUSH" -> incident.setStatus("FLUSHED");
            case "REPRESSURE" -> incident.setStatus(
                    "PASSED".equals(receipt.getVerificationStatus())
                            ? "PRESSURE_RESTORED" : "REPRESSURE_FAILED");
            default -> {
            }
        }
        incidentRepo.save(incident);
        return saved;
    }

    private void applyVerification(FieldReceipt receipt, String readingCode,
                                   OffsetDateTime eventAt, Incident incident) {
        PressureVerificationService.VerificationResult v = verificationService.verify(
                readingCode, eventAt, incident.getBoundarySegmentCodes());
        receipt.setReadingCode(v.readingCode());
        receipt.setDeviceNo(v.deviceNo());
        receipt.setSegmentCode(v.segmentCode());
        receipt.setMeasuredPressureMpa(v.measuredPressureMpa());
        receipt.setNormalMinMpa(v.normalMinMpa());
        receipt.setNormalMaxMpa(v.normalMaxMpa());
        receipt.setCorrectedTimeDeltaSeconds(v.correctedTimeDeltaSeconds());
        receipt.setTimeToleranceSeconds(v.timeToleranceSeconds());
        receipt.setVerificationReasons(v.reasons());
        receipt.setVerificationStatus(v.verificationStatus());
    }

    @Transactional
    public Incident close(Long incidentId, String operator) {
        Incident incident = incidentRepo.findById(incidentId)
                .orElseThrow(() -> ApiException.notFound("INCIDENT_NOT_FOUND", "事故不存在: " + incidentId));
        if ("CLOSED".equals(incident.getStatus())) {
            return incident;
        }
        List<FieldReceipt> chain = receiptRepo
                .findByIncidentIdAndCycleNoOrderByIdAsc(incidentId, incident.getCurrentCycle());
        FieldReceipt last = chain.isEmpty() ? null : chain.get(chain.size() - 1);
        if (last == null || !"REPRESSURE".equals(last.getType())) {
            throw ApiException.conflict("REPRESSURE_NOT_VERIFIED",
                    "缺少复压回执，不能关单", Map.of("completedTypes",
                            chain.stream().map(FieldReceipt::getType).toList()));
        }
        if (!"PASSED".equals(last.getVerificationStatus())) {
            throw ApiException.conflict("REPRESSURE_NOT_PASSED",
                    "复压核验未通过：" + last.getVerificationReasons() + "；必须重新派工并再次通过复压才能关单",
                    Map.of("verificationStatus", last.getVerificationStatus(),
                            "reasons", last.getVerificationReasons()));
        }

        incident.setStatus("CLOSED");
        incident.setClosedAt(OffsetDateTime.now());
        incidentRepo.save(incident);

        List<Assignment> assignments = assignmentRepo.findByIncidentIdOrderByIdAsc(incidentId);
        for (Assignment a : assignments) {
            if (!"CANCELLED".equals(a.getStatus())) {
                occupancyService.releaseByAssignment(a.getId(), "INCIDENT_CLOSED");
                if (!"COMPLETED".equals(a.getStatus())) {
                    a.setStatus("COMPLETED");
                    assignmentRepo.save(a);
                }
            }
        }
        return incident;
    }

    @Transactional(readOnly = true)
    public List<FieldReceipt> list(Long incidentId) {
        return receiptRepo.findByIncidentIdOrderByIdAsc(incidentId);
    }
}
