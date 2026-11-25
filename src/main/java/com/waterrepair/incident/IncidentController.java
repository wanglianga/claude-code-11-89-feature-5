package com.waterrepair.incident;

import com.waterrepair.auth.AuthService;
import com.waterrepair.auth.Roles;
import com.waterrepair.network.ImpactService;
import com.waterrepair.topology.TopologyQueryService;
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

@Tag(name = "04-事故确认、隔离方案与通知")
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentQueryService queryService;
    private final PressureReviewService reviewService;
    private final ImpactService impactService;
    private final TopologyQueryService topologyQuery;

    /** 兼容既有 4 参构造器；事故候选将直接按在线现图计算。 */
    public IncidentController(IncidentService incidentService,
                              IncidentQueryService queryService,
                              PressureReviewService reviewService,
                              ImpactService impactService) {
        this(incidentService, queryService, reviewService, impactService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public IncidentController(IncidentService incidentService,
                              IncidentQueryService queryService,
                              PressureReviewService reviewService,
                              ImpactService impactService,
                              TopologyQueryService topologyQuery) {
        this.incidentService = incidentService;
        this.queryService = queryService;
        this.reviewService = reviewService;
        this.impactService = impactService;
        this.topologyQuery = topologyQuery;
    }

    @Operation(summary = "调度确认线索形成事故（边界必须显式或来自已确认归属）")
    @Roles({"DISPATCHER"})
    @PostMapping
    public Incident create(@Valid @RequestBody IncidentDtos.CreateIncidentRequest request,
                           HttpServletRequest http) {
        return incidentService.create(request, operator(http));
    }

    @Operation(summary = "事故列表")
    @GetMapping
    public List<Incident> list() {
        return incidentService.list();
    }

    @Operation(summary = "事故全状态聚合详情（核对边界/通知/占用/回执归属）")
    @GetMapping("/{id}")
    public IncidentQueryService.IncidentDetail detail(@PathVariable Long id) {
        return queryService.detail(id);
    }

    @Operation(summary = "向事故追加线索来源（同段重拨/补充线索，幂等）")
    @Roles({"DISPATCHER"})
    @PostMapping("/{id}/sources")
    public IncidentSource addSource(@PathVariable Long id,
                                    @RequestBody IncidentDtos.AddSourceRequest request) {
        return incidentService.addSource(id, request.reportId(), request.sourceType());
    }

    @Operation(summary = "枚举当前事故边界在其固定拓扑版本下的候选关阀组合与精确影响地址")
    @GetMapping("/{id}/candidate-isolations")
    public List<ImpactService.CandidateCombo> candidates(@PathVariable Long id) {
        Incident incident = incidentService.mustGet(id);
        if (topologyQuery == null || incident.getTopologyVersionCode() == null) {
            return impactService.candidateCombos(incident.getBoundarySegmentCodes());
        }
        return topologyQuery.candidates(incident.getTopologyVersionCode(),
                incident.getBoundarySegmentCodes());
    }

    @Operation(summary = "确认隔离方案（校验组合确实能隔离边界，并快照影响集合）")
    @Roles({"DISPATCHER"})
    @PostMapping("/{id}/isolation-plans")
    public IsolationPlan createPlan(@PathVariable Long id,
                                    @Valid @RequestBody IncidentDtos.CreatePlanRequest request,
                                    HttpServletRequest http) {
        return incidentService.createPlan(id, request.valveCodes(), operator(http));
    }

    @Operation(summary = "事故全部隔离方案版本")
    @GetMapping("/{id}/isolation-plans")
    public List<IsolationPlan> plans(@PathVariable Long id) {
        return incidentService.plans(id);
    }

    @Operation(summary = "按当前 ACTIVE 方案给全部受影响地址发模拟短信通知（按方案幂等）")
    @Roles({"DISPATCHER"})
    @PostMapping("/{id}/notify")
    public IncidentDtos.NotifyResult notify(@PathVariable Long id) {
        return incidentService.notifyActivePlan(id);
    }

    @Operation(summary = "事故通知记录（含被更正通知）")
    @GetMapping("/{id}/notifications")
    public List<NotificationRecord> notifications(@PathVariable Long id) {
        return incidentService.notifications(id);
    }

    @Operation(summary = "提交压力复核；OVERTURNS 时同步更正范围、资源占用与通知链")
    @Roles({"DISPATCHER"})
    @PostMapping("/{id}/pressure-reviews")
    public PressureReviewService.ReviewResult review(
            @PathVariable Long id,
            @RequestBody PressureReviewService.ReviewRequest request,
            HttpServletRequest http) {
        return reviewService.submit(id, request, operator(http));
    }

    @Operation(summary = "压力复核记录")
    @GetMapping("/{id}/pressure-reviews")
    public List<PressureReview> reviews(@PathVariable Long id) {
        return reviewService.reviews(id);
    }

    @Operation(summary = "更正记录（旧/新范围、地址 diff、许可释放与新占）")
    @GetMapping("/{id}/corrections")
    public List<CorrectionRecord> corrections(@PathVariable Long id) {
        return reviewService.corrections(id);
    }

    private String operator(HttpServletRequest http) {
        return (String) http.getAttribute(AuthService.ATTR_USERNAME);
    }
}
