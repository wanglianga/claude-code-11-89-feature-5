package com.waterrepair.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 事故-线索来源关系。一条报修线索只能归属一个事故；
 * 热线重拨会产生新的 Report 行并新增一条 source 关系（只添来源）。
 */
@Entity
@Table(name = "incident_source")
public class IncidentSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long incidentId;

    @Column(nullable = false, unique = true)
    private Long reportId;

    /** INITIAL / REDIAL / MANUAL */
    @Column(nullable = false, length = 16)
    private String sourceType;

    @Column(nullable = false)
    private OffsetDateTime addedAt;

    public IncidentSource() {
    }

    public IncidentSource(Long incidentId, Long reportId, String sourceType, OffsetDateTime addedAt) {
        this.incidentId = incidentId;
        this.reportId = reportId;
        this.sourceType = sourceType;
        this.addedAt = addedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public Long getReportId() {
        return reportId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public OffsetDateTime getAddedAt() {
        return addedAt;
    }
}
