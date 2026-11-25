package com.waterrepair.dispatch;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public final class DispatchDtos {

    private DispatchDtos() {
    }

    public record DispatchRequest(
            String crewCode,
            @NotNull OffsetDateTime windowStart,
            @NotNull OffsetDateTime windowEnd,
            String strategy,
            Long parentAssignmentId,
            String note,
            Boolean holdOnly) {
    }

    public record EligibilityRequest(
            String kind,
            String crewCode,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd) {
    }

    public record CancelRequest(String reason) {
    }
}
