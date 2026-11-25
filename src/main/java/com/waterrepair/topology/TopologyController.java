package com.waterrepair.topology;

import com.waterrepair.auth.AuthService;
import com.waterrepair.auth.Roles;
import com.waterrepair.network.ImpactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 现场证据、拓扑版本发布、版本化影响计算与开放工单拓扑重审。
 * 同时挂载 /api/network/topology 别名，便于按“管网拓扑”资源发现。
 */
@Tag(name = "07-拓扑修订证据与版本")
@RestController
@RequestMapping({"/api/topology", "/api/network/topology"})
public class TopologyController {

    private final TopologyVersionService versionService;
    private final TopologyQueryService queryService;
    private final TopologyChangeService changeService;

    public TopologyController(TopologyVersionService versionService,
                              TopologyQueryService queryService,
                              TopologyChangeService changeService) {
        this.versionService = versionService;
        this.queryService = queryService;
        this.changeService = changeService;
    }

    // ---------------- 证据 ----------------

    @Operation(summary = "登记现场草图/两端标识/测距/误差/测量人证据（可同时带复核人直接双人核验）")
    @Roles({"DISPATCHER", "CREW", "ASSET_ENGINEER"})
    @PostMapping("/evidence")
    public TopologyEvidence createEvidence(@Valid @RequestBody TopologyDtos.EvidenceRequest request,
                                           HttpServletRequest http) {
        return versionService.createEvidence(request, operator(http));
    }

    @Operation(summary = "全部拓扑修订现场证据")
    @GetMapping("/evidence")
    public List<TopologyEvidence> evidenceList() {
        return versionService.listEvidence();
    }

    @Operation(summary = "证据详情（按 id 或证据编号，可审查草图/测距/误差/复核人）")
    @GetMapping("/evidence/{idOrCode}")
    public TopologyEvidence evidence(@PathVariable String idOrCode) {
        return versionService.getEvidenceByIdOrCode(idOrCode);
    }

    @Operation(summary = "双人核验：第二名复核人确认（复核人不得与测量人为同一人）")
    @Roles({"DISPATCHER", "CREW", "ASSET_ENGINEER"})
    @PostMapping("/evidence/{id}/dual-check")
    public TopologyEvidence dualCheck(@PathVariable Long id,
                                      @Valid @RequestBody TopologyDtos.DualCheckRequest request,
                                      HttpServletRequest http) {
        return versionService.dualCheck(id, request, operator(http));
    }

    // ---------------- 版本发布 ----------------

    @Operation(summary = "资产工程师确认证据后发布新拓扑版本（PARTIAL 局部修订 / FULL 整段替换）")
    @Roles({"ASSET_ENGINEER"})
    @PostMapping("/revisions")
    public TopologyVersionService.PublishResult publish(
            @RequestBody TopologyDtos.PublishRevisionRequest request,
            HttpServletRequest http) {
        return versionService.publish(request, operator(http));
    }

    @Operation(summary = "全部拓扑版本（含生效时刻与修订模式）")
    @GetMapping("/versions")
    public List<TopologyQueryService.VersionView> versions() {
        return queryService.versionViews();
    }

    @Operation(summary = "拓扑版本详情（含不可变管网快照与未知支管标记）")
    @GetMapping("/versions/{versionCode}")
    public TopologyQueryService.VersionView version(@PathVariable String versionCode) {
        return queryService.versionView(versionCode);
    }

    public record VersionImpactRequest(List<String> valveCodes) {
    }

    @Operation(summary = "按指定拓扑版本计算关阀影响（旧案复演用 TOPO-V1，新版本用其版本号）")
    @PostMapping("/versions/{versionCode}/impact")
    public ImpactService.ImpactResult versionImpact(
            @PathVariable String versionCode,
            @RequestBody VersionImpactRequest request) {
        return queryService.evaluate(versionCode, request.valveCodes());
    }

    public record VersionCandidateRequest(List<String> boundarySegmentCodes) {
    }

    @Operation(summary = "按指定拓扑版本枚举候选隔离阀门组合")
    @PostMapping("/versions/{versionCode}/candidate-isolations")
    public List<ImpactService.CandidateCombo> versionCandidates(
            @PathVariable String versionCode,
            @RequestBody VersionCandidateRequest request) {
        return queryService.candidates(versionCode, request.boundarySegmentCodes());
    }

    // ---------------- 开放工单重审 ----------------

    @Operation(summary = "拓扑变更产生的开放工单人工重审单（可按 status=PENDING_CONFIRMATION 过滤）")
    @GetMapping("/change-reviews")
    public List<TopologyChangeService.ReviewView> changeReviews(
            @RequestParam(required = false) String status) {
        return changeService.reviewViews(status);
    }

    @Operation(summary = "重审单详情（含候选组合与已通知对象快照）")
    @GetMapping("/change-reviews/{id}")
    public TopologyChangeService.ReviewView changeReview(@PathVariable Long id) {
        return changeService.reviewView(id);
    }

    @Operation(summary = "调度确认新阀门组合并生成新计划版本；不会自动改动任何旧通知")
    @Roles({"DISPATCHER"})
    @PostMapping("/change-reviews/{id}/confirm")
    public TopologyChangeService.ReviewView confirmReview(
            @PathVariable Long id,
            @RequestBody(required = false) TopologyDtos.ConfirmReviewRequest request,
            HttpServletRequest http) {
        List<String> valves = request == null ? null : request.valveCodes();
        String note = request == null ? null : request.note();
        return changeService.confirm(id, valves, note, operator(http));
    }

    @Operation(summary = "人工重审后排除本次拓扑影响/维持旧计划（必须填写理由）")
    @Roles({"DISPATCHER"})
    @PostMapping("/change-reviews/{id}/dismiss")
    public TopologyChangeService.ReviewView dismissReview(
            @PathVariable Long id,
            @Valid @RequestBody TopologyDtos.DismissReviewRequest request,
            HttpServletRequest http) {
        return changeService.dismiss(id, request.reason(), operator(http));
    }

    @Operation(summary = "显式向确认后新增的地址发扩大范围通知（幂等；绝不自动发生）")
    @Roles({"DISPATCHER"})
    @PostMapping("/change-reviews/{id}/notify-added")
    public TopologyChangeService.DeltaNotifyResult notifyAdded(
            @PathVariable Long id, HttpServletRequest http) {
        return changeService.notifyAddedAddresses(id, operator(http));
    }

    private String operator(HttpServletRequest http) {
        return (String) http.getAttribute(AuthService.ATTR_USERNAME);
    }
}
