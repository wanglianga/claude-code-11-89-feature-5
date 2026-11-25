package com.waterrepair.report;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record CreateReportRequest(
            String hotlineCallId,
            Long redialOfReportId,
            String redialOfCode,
            String callerPhone,
            @NotBlank(message = "rawAddress 不能为空") String rawAddress,
            String description,
            OffsetDateTime receivedAt,
            List<String> photoRefs,
            Double lng,
            Double lat) {
    }

    public record ManualJudgmentRequest(
            List<String> segmentCodes,
            String status,
            String reason) {
    }

    public record ReportDetail(RepairReport report,
                              List<AttributionJudgment> judgments,
                              AttributionJudgment currentJudgment,
                              List<Long> incidentIds,
                              Long autoAttachedIncidentId,
                              boolean redial) {
    }
}
