package com.waterrepair.topology;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 事故维度的拓扑固定版本、重审单与旧图复演端点。
 */
@Tag(name = "04-事故确认、隔离方案与通知")
@RestController
@RequestMapping("/api/incidents")
public class TopologyIncidentController {

    private final TopologyChangeService changeService;

    public TopologyIncidentController(TopologyChangeService changeService) {
        this.changeService = changeService;
    }

    @Operation(summary = "该事故关联的拓扑变更人工重审单")
    @GetMapping("/{incidentId}/topology-reviews")
    public List<TopologyChangeReview> incidentReviews(@PathVariable Long incidentId) {
        return changeService.incidentReviews(incidentId);
    }

    @Operation(summary = "按事故固定的拓扑版本复演隔离影响，并与最新版本试算对比")
    @GetMapping("/{incidentId}/topology-replay")
    public TopologyChangeService.ReplayView replay(@PathVariable Long incidentId) {
        return changeService.replayIncident(incidentId);
    }
}
