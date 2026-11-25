package com.waterrepair.report;

import com.waterrepair.auth.AuthService;
import com.waterrepair.auth.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "03-报修受理与归属判断")
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "登记一条报修/热线线索，自动生成归属判断；重拨单会自动追加到原事故")
    @Roles({"DISPATCHER"})
    @PostMapping
    public ReportDtos.ReportDetail create(@Valid @RequestBody ReportDtos.CreateReportRequest request) {
        return reportService.create(request);
    }

    @Operation(summary = "全部报修线索")
    @GetMapping
    public List<RepairReport> list() {
        return reportService.list();
    }

    @Operation(summary = "线索详情：原文、全部归属判断版本、所属事故、重拨标记")
    @GetMapping("/{id}")
    public ReportDtos.ReportDetail detail(@PathVariable Long id) {
        return reportService.detail(id);
    }

    @Operation(summary = "人工/勘查归属判断（追加新版本，不覆盖自动判断）")
    @Roles({"DISPATCHER", "CREW"})
    @PostMapping("/{id}/judgments")
    public ReportDtos.ReportDetail judge(@PathVariable Long id,
                                         @RequestBody ReportDtos.ManualJudgmentRequest request,
                                         HttpServletRequest http) {
        String operator = (String) http.getAttribute(AuthService.ATTR_USERNAME);
        return reportService.addManualJudgment(id, request, operator);
    }
}
