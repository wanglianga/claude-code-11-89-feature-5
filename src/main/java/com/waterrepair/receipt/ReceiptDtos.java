package com.waterrepair.receipt;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public final class ReceiptDtos {

    private ReceiptDtos() {
    }

    public record CreateReceiptRequest(
            @NotNull Long assignmentId,
            @NotNull String type,
            @NotNull OffsetDateTime eventAt,
            String note,
            String readingCode) {
    }
}
