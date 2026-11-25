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

/**
 * 隔离方案及其影响快照。每次选择/更正都新增版本，旧版本保留为 SUPERSEDED/CORRECTED。
 */
@Entity
@Table(name = "isolation_plan")
public class IsolationPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long incidentId;

    @Column(nullable = false)
    private Integer versionNo;

    /** ACTIVE / SUPERSEDED / CORRECTED */
    @Column(nullable = false, length = 16)
    private String status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_valve", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "idx")
    @Column(name = "valve_code", length = 32)
    private List<String> valveCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_outage_segment", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "idx")
    @Column(name = "segment_code", length = 32)
    private List<String> outageSegmentCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_affected_address", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "idx")
    @Column(name = "address_code", length = 32)
    private List<String> affectedAddressCodes = new ArrayList<>();

    @Column(length = 64)
    private String createdBy;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getValveCodes() {
        return valveCodes;
    }

    public void setValveCodes(List<String> valveCodes) {
        this.valveCodes = valveCodes == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(valveCodes);
    }

    public List<String> getOutageSegmentCodes() {
        return outageSegmentCodes;
    }

    public void setOutageSegmentCodes(List<String> outageSegmentCodes) {
        this.outageSegmentCodes = outageSegmentCodes == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(outageSegmentCodes);
    }

    public List<String> getAffectedAddressCodes() {
        return affectedAddressCodes;
    }

    public void setAffectedAddressCodes(List<String> affectedAddressCodes) {
        this.affectedAddressCodes = affectedAddressCodes == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(affectedAddressCodes);
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
