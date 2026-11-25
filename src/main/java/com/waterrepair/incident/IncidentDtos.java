package com.waterrepair.incident;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public final class IncidentDtos {

    private IncidentDtos() {
    }

    public record CreateIncidentRequest(
            String title,
            @NotEmpty(message = "reportIds 至少包含一条线索") List<Long> reportIds,
            List<String> boundarySegmentCodes,
            String note) {
    }

    public record AddSourceRequest(Long reportId, String sourceType) {
    }

    public record CreatePlanRequest(@NotEmpty(message = "valveCodes 不能为空") List<String> valveCodes) {
    }

    public record NotifyResult(int sentCount, List<NotificationRecord> notifications, boolean alreadyNotified) {
    }
}
