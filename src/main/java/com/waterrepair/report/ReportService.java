package com.waterrepair.report;

import com.waterrepair.common.ApiException;
import com.waterrepair.network.PipeSegmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ReportService {

    private final RepairReportRepository reportRepo;
    private final AttributionJudgmentRepository judgmentRepo;
    private final AttributionService attributionService;
    private final PipeSegmentRepository segmentRepo;
    private final IncidentLinkPort incidentLinkPort;

    public ReportService(RepairReportRepository reportRepo,
                         AttributionJudgmentRepository judgmentRepo,
                         AttributionService attributionService,
                         PipeSegmentRepository segmentRepo,
                         IncidentLinkPort incidentLinkPort) {
        this.reportRepo = reportRepo;
        this.judgmentRepo = judgmentRepo;
        this.attributionService = attributionService;
        this.segmentRepo = segmentRepo;
        this.incidentLinkPort = incidentLinkPort;
    }

    @Transactional
    public ReportDtos.ReportDetail create(ReportDtos.CreateReportRequest req) {
        Long redialOfId = req.redialOfReportId();
        if (redialOfId == null && req.redialOfCode() != null && !req.redialOfCode().isBlank()) {
            RepairReport original = reportRepo.findAll().stream()
                    .filter(r -> req.redialOfCode().equals(r.getCode()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.notFound("REPORT_NOT_FOUND",
                            "redialOfCode 对应报修单不存在: " + req.redialOfCode()));
            redialOfId = original.getId();
        }

        RepairReport redialOriginal = null;
        if (redialOfId != null) {
            final Long originalId = redialOfId;
            redialOriginal = reportRepo.findById(redialOfId)
                    .orElseThrow(() -> ApiException.notFound("REPORT_NOT_FOUND",
                            "重拨关联的原报修单不存在: " + originalId));
        }

        RepairReport report = new RepairReport();
        report.setHotlineCallId(req.hotlineCallId() != null ? req.hotlineCallId()
                : redialOriginal != null ? redialOriginal.getHotlineCallId() : null);
        report.setRedialOfId(redialOfId);
        report.setCallerPhone(req.callerPhone());
        report.setRawAddress(req.rawAddress());
        report.setDescription(req.description());
        report.setReceivedAt(req.receivedAt() != null ? req.receivedAt() : OffsetDateTime.now());
        report.setPhotoRefs(req.photoRefs());
        report.setLng(req.lng());
        report.setLat(req.lat());
        report.setCreatedAt(OffsetDateTime.now());
        report = reportRepo.saveAndFlush(report);
        report.setCode(String.format("R-%04d", report.getId()));
        report = reportRepo.save(report);

        AttributionJudgment judgment = attributionService.judgeAutomatically(
                report.getId(), req.rawAddress(), req.lng(), req.lat());
        judgmentRepo.save(judgment);

        Long autoIncidentId = null;
        if (redialOfId != null) {
            autoIncidentId = incidentLinkPort.autoAttachRedial(report.getId(), redialOfId);
        }
        return detail(report.getId(), autoIncidentId);
    }

    @Transactional(readOnly = true)
    public List<RepairReport> list() {
        return reportRepo.findAll();
    }

    @Transactional(readOnly = true)
    public ReportDtos.ReportDetail detail(Long id) {
        return detail(id, null);
    }

    private ReportDtos.ReportDetail detail(Long id, Long justAttachedIncidentId) {
        RepairReport report = reportRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("REPORT_NOT_FOUND", "报修线索不存在: " + id));
        List<AttributionJudgment> judgments =
                judgmentRepo.findByReportIdOrderByVersionNoAscIdAsc(id);
        AttributionJudgment current = judgments.isEmpty() ? null : judgments.get(judgments.size() - 1);
        List<Long> incidentIds = incidentLinkPort.findIncidentIdsForReport(id);
        Long attached = justAttachedIncidentId != null ? justAttachedIncidentId
                : (incidentIds.isEmpty() ? null : incidentIds.get(0));
        return new ReportDtos.ReportDetail(report, judgments, current, incidentIds,
                attached, report.getRedialOfId() != null);
    }

    @Transactional
    public ReportDtos.ReportDetail addManualJudgment(Long reportId,
                                                     ReportDtos.ManualJudgmentRequest req,
                                                     String operator) {
        RepairReport report = reportRepo.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("REPORT_NOT_FOUND", "报修线索不存在: " + reportId));
        if (req.segmentCodes() == null || req.segmentCodes().isEmpty()) {
            throw ApiException.badRequest("SEGMENT_REQUIRED", "人工归属判断至少需要一个 segmentCodes");
        }
        for (String code : req.segmentCodes()) {
            if (segmentRepo.findById(code).isEmpty()) {
                throw ApiException.badRequest("UNKNOWN_SEGMENT", "管段不存在: " + code);
            }
        }
        List<AttributionJudgment> existing =
                judgmentRepo.findByReportIdOrderByVersionNoAscIdAsc(reportId);
        int nextVersion = existing.size() + 1;
        AttributionJudgment j = attributionService.judgeManually(
                reportId, nextVersion, req.segmentCodes(), req.status(), req.reason(), operator);
        judgmentRepo.save(j);
        return detail(report.getId());
    }
}
