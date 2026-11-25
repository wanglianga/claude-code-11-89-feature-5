package com.waterrepair.receipt;

import com.waterrepair.auth.AuthService;
import com.waterrepair.auth.Roles;
import com.waterrepair.incident.Incident;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "06-现场回执序列与复压核验")
@RestController
public class ReceiptController {

    private final ReceiptService receiptService;
    private final PressureVerificationService verificationService;

    public ReceiptController(ReceiptService receiptService,
                             PressureVerificationService verificationService) {
        this.receiptService = receiptService;
        this.verificationService = verificationService;
    }

    public record VerifyRequest(String readingCode,
                                java.time.OffsetDateTime eventAt,
                                java.util.List<String> boundarySegmentCodes) {
    }

    @Operation(summary = "复压核验试算：正常区间 + 校时后采样时刻容差 + 边界一致性，不落库")
    @PostMapping("/api/pressure/verify-repressure")
    public PressureVerificationService.VerificationResult verify(@RequestBody VerifyRequest request) {
        return verificationService.verify(request.readingCode(), request.eventAt(),
                request.boundarySegmentCodes());
    }

    @Operation(summary = "提交现场回执（必须按 到场→隔离→修复→冲洗→复压 顺序）")
    @Roles({"DISPATCHER", "CREW"})
    @PostMapping("/api/incidents/{id}/receipts")
    public FieldReceipt post(@PathVariable Long id,
                             @Valid @RequestBody ReceiptDtos.CreateReceiptRequest request,
                             HttpServletRequest http) {
        return receiptService.post(id, request,
                (String) http.getAttribute(AuthService.ATTR_USERNAME));
    }

    @Operation(summary = "事故全部历史回执（含失败周期，按时间顺序）")
    @GetMapping("/api/incidents/{id}/receipts")
    public List<FieldReceipt> list(@PathVariable Long id) {
        return receiptService.list(id);
    }

    @Operation(summary = "复压通过后关单；未通过或缺少回执时返回 409")
    @Roles({"DISPATCHER"})
    @PostMapping("/api/incidents/{id}/close")
    public Incident close(@PathVariable Long id, HttpServletRequest http) {
        return receiptService.close(id, (String) http.getAttribute(AuthService.ATTR_USERNAME));
    }
}
