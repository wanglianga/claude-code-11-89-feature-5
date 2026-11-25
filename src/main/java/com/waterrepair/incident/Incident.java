package com.waterrepair.incident;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 256)
    private String title;

    /** CONFIRMED / SCOUT_DISPATCHED / REPAIR_DISPATCHED / ON_SITE / ISOLATED /
     *  REPAIRED / FLUSHED / PRESSURE_RESTORED / REPRESSURE_FAILED / CLOSED */
    @Column(nullable = false, length = 32)
    private String status = "CONFIRMED";

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "incident_boundary_segment", joinColumns = @JoinColumn(name = "incident_id"))
    @OrderColumn(name = "idx")
    @Column(name = "segment_code", length = 32)
    private List<String> boundarySegmentCodes = new ArrayList<>();

    /** 回执周期：复压失败后重新派工会 +1，新周期重新按五段顺序提交回执 */
    @Column(nullable = false)
    private Integer currentCycle = 1;

    @Column(nullable = false)
    private Integer revision = 1;

    /**
     * 成案时采用并固定的拓扑版本。新事故取当前 ACTIVE 版本；
     * 拓扑发布不回写未确认事故的该字段，只有调度确认重审后才升级。
     * 已结案事故始终按此版本旧图复演。
     */
    @Column(length = 40)
    private String topologyVersionCode;

    @Column(length = 64)
    private String confirmedBy;

    @Column(length = 1024)
    private String confirmationNote;

    private OffsetDateTime confirmedAt;
    private OffsetDateTime closedAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getBoundarySegmentCodes() {
        return boundarySegmentCodes;
    }

    public void setBoundarySegmentCodes(List<String> codes) {
        this.boundarySegmentCodes = codes == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(codes);
    }

    public Integer getCurrentCycle() {
        return currentCycle;
    }

    public void setCurrentCycle(Integer currentCycle) {
        this.currentCycle = currentCycle;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    public String getTopologyVersionCode() {
        return topologyVersionCode;
    }

    public void setTopologyVersionCode(String topologyVersionCode) {
        this.topologyVersionCode = topologyVersionCode;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(String confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public String getConfirmationNote() {
        return confirmationNote;
    }

    public void setConfirmationNote(String confirmationNote) {
        this.confirmationNote = confirmationNote;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
