package com.waterrepair.topology;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 拓扑版本快照与拓扑修订相关 DTO。
 *
 * <p>{@link Snapshot} 是某一时刻管网图的不可变完整副本，随拓扑版本以 JSON 落库，
 * 使已结案事故能够按“当时采用的旧图”精确复演，而不受后续发布影响。</p>
 */
public final class TopologyDtos {

    private TopologyDtos() {
    }

    public record Snapshot(List<SnapNode> nodes,
                           List<SnapSegment> segments,
                           List<SnapValve> valves,
                           List<SnapAddress> addresses,
                           List<UnknownBranch> unknownBranches) {
    }

    public record SnapNode(String code, String name, String type, Double lng, Double lat) {
    }

    public record SnapSegment(String code, String name, String fromNodeCode, String toNodeCode,
                              String topologyStatus,
                              Double normalPressureMinMpa, Double normalPressureMaxMpa,
                              Integer pressureTimeToleranceSeconds) {
    }

    public record SnapValve(String code, String name, String nodeCode,
                            String qualification, String permitCode) {
    }

    public record SnapAddress(String code, String address, String segmentCode, String building,
                              Double lng, Double lat, List<String> aliases) {
    }

    /**
     * 相邻未测支管：只作为未知风险提示，不进入权威连通/影响计算，
     * 其 candidateAddressCodes 也不得被自动纳入通知范围。
     */
    public record UnknownBranch(String code,
                                String attachedNodeCode,
                                String direction,
                                String suspectedSegmentCode,
                                String status,
                                String note,
                                List<String> candidateAddressCodes) {
    }

    // ---------------- 现场证据 ----------------

    public record EvidenceRequest(
            String code,
            String title,
            Long incidentId,
            String sketchRef,
            String sketchDescription,
            String endANodeCode,
            String endAIdentifier,
            String endAMarker,
            Double endALng,
            Double endALat,
            String endBNodeCode,
            String endBIdentifier,
            String endBMarker,
            Double endBLng,
            Double endBLat,
            Double measuredDistanceM,
            Double measurementErrorM,
            String measurementMethod,
            @NotBlank(message = "surveyorName 测量/探测人不能为空") String surveyorName,
            OffsetDateTime surveyedAt,
            String reviewerName,
            OffsetDateTime reviewedAt,
            String reviewNote) {
    }

    public record DualCheckRequest(
            @NotBlank(message = "reviewerName 复核人不能为空") String reviewerName,
            String reviewNote) {
    }

    // ---------------- 拓扑版本发布 ----------------

    public record AddConnection(String segmentCode,
                                String name,
                                @NotBlank String fromNodeCode,
                                @NotBlank String toNodeCode,
                                Double normalPressureMinMpa,
                                Double normalPressureMaxMpa,
                                Integer pressureTimeToleranceSeconds) {
    }

    public record PublishRevisionRequest(
            /** PARTIAL=双人核验后先发布局部修订（相邻未测支管标未知）；FULL=整段测完后整块替换。 */
            String mode,
            String versionCode,
            /** 资产工程师姓名（留痕）；登录账号角色必须是 ASSET_ENGINEER。 */
            String confirmerName,
            String changeSummary,
            /** 显式生效时刻；不传则取服务当前时刻。回放使用固定时间以核对“何时生效”。 */
            OffsetDateTime effectiveAt,
            List<Long> evidenceIds,
            List<String> evidenceCodes,
            List<AddConnection> addConnections,
            List<String> confirmSegmentCodes,
            List<UnknownBranch> unknownBranches) {
    }

    // ---------------- 开放工单复核 ----------------

    public record ConfirmReviewRequest(List<String> valveCodes, String note) {
    }

    public record DismissReviewRequest(@NotBlank(message = "reason 必须填写人工排除/维持旧图的理由") String reason) {
    }
}
