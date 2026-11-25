package com.waterrepair.dispatch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 资源占用记录。resourceType ∈ CREW / VALVE_PERMIT / VEHICLE / TOOL。
 * 互斥判定：同 resourceType+resourceCode 存在状态 HELD 且时间窗半开区间重叠的记录即冲突。
 * 释放不删除行：状态置 RELEASED 并记录原因，满足“占用释放可追溯”。
 */
@Entity
@Table(name = "resource_occupancy", indexes = {
        @Index(name = "idx_occ_resource", columnList = "resourceType,resourceCode,status")
})
public class ResourceOccupancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 24)
    private String resourceType;

    @Column(nullable = false, length = 64)
    private String resourceCode;

    @Column(nullable = false)
    private Long assignmentId;

    @Column(nullable = false)
    private Long incidentId;

    @Column(nullable = false)
    private OffsetDateTime windowStart;

    @Column(nullable = false)
    private OffsetDateTime windowEnd;

    /** HELD / RELEASED */
    @Column(nullable = false, length = 16)
    private String status = "HELD";

    @Column(length = 64)
    private String releaseReason;

    private OffsetDateTime releasedAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceCode() {
        return resourceCode;
    }

    public void setResourceCode(String resourceCode) {
        this.resourceCode = resourceCode;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public OffsetDateTime getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(OffsetDateTime windowStart) {
        this.windowStart = windowStart;
    }

    public OffsetDateTime getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(OffsetDateTime windowEnd) {
        this.windowEnd = windowEnd;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReleaseReason() {
        return releaseReason;
    }

    public void setReleaseReason(String releaseReason) {
        this.releaseReason = releaseReason;
    }

    public OffsetDateTime getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(OffsetDateTime releasedAt) {
        this.releasedAt = releasedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
