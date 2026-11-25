package com.waterrepair.dispatch;

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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "05-派工与资源互斥")
@RestController
public class DispatchController {

    private final DispatchService dispatchService;
    private final ResourceOccupancyRepository occupancyRepo;

    public DispatchController(DispatchService dispatchService,
                              ResourceOccupancyRepository occupancyRepo) {
        this.dispatchService = dispatchService;
        this.occupancyRepo = occupancyRepo;
    }

    @Operation(summary = "派工资格预检：许可、当班窗口、车辆、工具（不写占用）")
    @PostMapping("/api/incidents/{id}/dispatch/eligibility")
    public EligibilityService.EligibilityResult eligibility(
            @PathVariable Long id, @RequestBody DispatchDtos.EligibilityRequest request) {
        return dispatchService.checkEligibility(id, request);
    }

    @Operation(summary = "路线A第一步：先派勘查人员（只占队伍/勘查车/工具，不要求阀门许可）")
    @Roles({"DISPATCHER"})
    @PostMapping("/api/incidents/{id}/dispatch/scout")
    public Assignment scout(@PathVariable Long id,
                            @Valid @RequestBody DispatchDtos.DispatchRequest request,
                            HttpServletRequest http) {
        return dispatchService.dispatchScout(id, request, operator(http));
    }

    @Operation(summary = "派出/暂留维修班组并锁定队伍、阀门操作许可、车辆、工具的时间窗")
    @Roles({"DISPATCHER"})
    @PostMapping("/api/incidents/{id}/dispatch/repair")
    public Assignment repair(@PathVariable Long id,
                             @Valid @RequestBody DispatchDtos.DispatchRequest request,
                             HttpServletRequest http) {
        return dispatchService.dispatchRepair(id, request, operator(http));
    }

    @Operation(summary = "复压失败后重新派工：释放原派工单占用并开启新回执周期")
    @Roles({"DISPATCHER"})
    @PostMapping("/api/incidents/{id}/redispatch")
    public Assignment redispatch(@PathVariable Long id,
                                 @Valid @RequestBody DispatchDtos.DispatchRequest request,
                                 HttpServletRequest http) {
        return dispatchService.redispatch(id, request, operator(http));
    }

    @Operation(summary = "事故下的全部派工单（含历次 attempt）")
    @GetMapping("/api/incidents/{id}/assignments")
    public List<Assignment> assignments(@PathVariable Long id) {
        return dispatchService.listForIncident(id);
    }

    @Operation(summary = "下达 PROVISIONAL 暂留派工单")
    @Roles({"DISPATCHER"})
    @PostMapping("/api/assignments/{assignmentId}/issue")
    public Assignment issue(@PathVariable Long assignmentId) {
        return dispatchService.issue(assignmentId);
    }

    @Operation(summary = "完成/交接派工单并释放其资源占用（如勘查班确认阀门后交接）")
    @Roles({"DISPATCHER", "CREW"})
    @PostMapping("/api/assignments/{assignmentId}/complete")
    public Assignment complete(@PathVariable Long assignmentId,
                               @RequestBody(required = false) DispatchDtos.CancelRequest request) {
        return dispatchService.complete(assignmentId, request == null ? null : request.reason());
    }

    @Operation(summary = "取消派工单并释放其全部资源占用")
    @Roles({"DISPATCHER"})
    @PostMapping("/api/assignments/{assignmentId}/cancel")
    public Assignment cancel(@PathVariable Long assignmentId,
                             @RequestBody(required = false) DispatchDtos.CancelRequest request) {
        return dispatchService.cancel(assignmentId, request == null ? null : request.reason());
    }

    @Operation(summary = "派工单的资源占用记录（含已释放记录与释放原因）")
    @GetMapping("/api/assignments/{assignmentId}/occupancies")
    public List<ResourceOccupancy> occupancies(@PathVariable Long assignmentId) {
        return dispatchService.occupancies(assignmentId);
    }

    @Operation(summary = "资源占用总览（排查互斥用）")
    @GetMapping("/api/occupancies")
    public List<ResourceOccupancy> allOccupancies() {
        return occupancyRepo.findAll();
    }

    private String operator(HttpServletRequest http) {
        return (String) http.getAttribute(AuthService.ATTR_USERNAME);
    }
}
