package com.waterrepair.incident;

import com.waterrepair.dispatch.Assignment;
import com.waterrepair.dispatch.AssignmentRepository;
import com.waterrepair.dispatch.ResourceOccupancy;
import com.waterrepair.dispatch.ResourceOccupancyRepository;
import com.waterrepair.receipt.FieldReceipt;
import com.waterrepair.receipt.FieldReceiptRepository;
import com.waterrepair.report.ReportDtos;
import com.waterrepair.report.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 事故全状态聚合：原文线索与归属判断、方案版本、通知链、更正链、
 * 派工单、占用记录、历史回执，一次返回供回放核对。
 */
@Service
public class IncidentQueryService {

    private final IncidentRepository incidentRepo;
    private final IncidentSourceRepository sourceRepo;
    private final IsolationPlanRepository planRepo;
    private final NotificationRecordRepository notificationRepo;
    private final CorrectionRecordRepository correctionRepo;
    private final PressureReviewRepository reviewRepo;
    private final AssignmentRepository assignmentRepo;
    private final ResourceOccupancyRepository occupancyRepo;
    private final FieldReceiptRepository receiptRepo;
    private final ReportService reportService;

    public IncidentQueryService(IncidentRepository incidentRepo,
                                IncidentSourceRepository sourceRepo,
                                IsolationPlanRepository planRepo,
                                NotificationRecordRepository notificationRepo,
                                CorrectionRecordRepository correctionRepo,
                                PressureReviewRepository reviewRepo,
                                AssignmentRepository assignmentRepo,
                                ResourceOccupancyRepository occupancyRepo,
                                FieldReceiptRepository receiptRepo,
                                ReportService reportService) {
        this.incidentRepo = incidentRepo;
        this.sourceRepo = sourceRepo;
        this.planRepo = planRepo;
        this.notificationRepo = notificationRepo;
        this.correctionRepo = correctionRepo;
        this.reviewRepo = reviewRepo;
        this.assignmentRepo = assignmentRepo;
        this.occupancyRepo = occupancyRepo;
        this.receiptRepo = receiptRepo;
        this.reportService = reportService;
    }

    public record IncidentDetail(Incident incident,
                                 List<IncidentSource> sources,
                                 List<ReportDtos.ReportDetail> reportDetails,
                                 List<IsolationPlan> plans,
                                 List<NotificationRecord> notifications,
                                 List<CorrectionRecord> corrections,
                                 List<PressureReview> pressureReviews,
                                 List<Assignment> assignments,
                                 List<ResourceOccupancy> occupancies,
                                 List<FieldReceipt> receipts) {
    }

    @Transactional(readOnly = true)
    public IncidentDetail detail(Long id) {
        Incident incident = incidentRepo.findById(id)
                .orElseThrow(() -> new com.waterrepair.common.ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "INCIDENT_NOT_FOUND", "事故不存在: " + id));
        List<IncidentSource> sources = sourceRepo.findByIncidentId(id);
        List<ReportDtos.ReportDetail> reports = new ArrayList<>();
        for (IncidentSource s : sources) {
            reports.add(reportService.detail(s.getReportId()));
        }
        List<Assignment> assignments = assignmentRepo.findByIncidentIdOrderByIdAsc(id);
        List<ResourceOccupancy> occupancies = occupancyRepo.findByIncidentIdOrderByIdAsc(id);
        List<FieldReceipt> receipts = receiptRepo.findByIncidentIdOrderByIdAsc(id);
        return new IncidentDetail(
                incident,
                sources,
                reports,
                planRepo.findByIncidentIdOrderByVersionNoAscIdAsc(id),
                notificationRepo.findByIncidentIdOrderByIdAsc(id),
                correctionRepo.findByIncidentIdOrderByRevisionAscIdAsc(id),
                reviewRepo.findByIncidentIdOrderByIdAsc(id),
                assignments,
                occupancies,
                receipts);
    }
}
