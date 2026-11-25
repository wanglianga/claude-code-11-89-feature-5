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
 * 一次“推翻原判断”的更正记录：完整保留旧边界/旧方案/旧影响集合，
 * 记录新集合、地址增减、阀门许可占用的释放与新占，以及通知更正批次。
 */
@Entity
@Table(name = "correction_record")
public class CorrectionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long incidentId;

    @Column(nullable = false)
    private Integer revision;

    /** PRESSURE_REVIEW / MANUAL_REVIEW */
    @Column(nullable = false, length = 32)
    private String triggerType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "correction_old_boundary", joinColumns = @JoinColumn(name = "correction_id"))
    @OrderColumn(name = "idx")
    @Column(name = "segment_code", length = 32)
    private List<String> oldBoundarySegments = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "correction_new_boundary", joinColumns = @JoinColumn(name = "correction_id"))
    @OrderColumn(name = "idx")
    @Column(name = "segment_code", length = 32)
    private List<String> newBoundarySegments = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "correction_old_valve", joinColumns = @JoinColumn(name = "correction_id"))
    @OrderColumn(name = "idx")
    @Column(name = "valve_code", length = 32)
    private List<String> oldValveCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "correction_new_valve", joinColumns = @JoinColumn(name = "correction_id"))
    @OrderColumn(name = "idx")
    @Column(name = "valve_code", length = 32)
    private List<String> newValveCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "correction_old_address", joinColumns = @JoinColumn(name = "correction_id"))
    @OrderColumn(name = "idx")
    @Column(name = "address_code", length = 32)
    private List<String> oldAddressCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "correction_new_address", joinColumns = @JoinColumn(name = "correction_id"))
    @OrderColumn(name = "idx")
    @Column(name = "address_code", length = 32)
    private List<String> newAddressCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "correction_added_address", joinColumns = @JoinColumn(name = "correction_id"))
    @OrderColumn(name = "idx")
    @Column(name = "address_code", length = 32)
    private List<String> addedAddressCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "correction_removed_address", joinColumns = @JoinColumn(name = "correction_id"))
    @OrderColumn(name = "idx")
    @Column(name = "address_code", length = 32)
    private List<String> removedAddressCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "correction_released_permit", joinColumns = @JoinColumn(name = "correction_id"))
    @OrderColumn(name = "idx")
    @Column(name = "permit_code", length = 64)
    private List<String> releasedPermitCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "correction_reserved_permit", joinColumns = @JoinColumn(name = "correction_id"))
    @OrderColumn(name = "idx")
    @Column(name = "permit_code", length = 64)
    private List<String> reservedPermitCodes = new ArrayList<>();

    @Column(length = 1024)
    private String reason;

    @Column(length = 64)
    private String createdBy;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public List<String> getOldBoundarySegments() {
        return oldBoundarySegments;
    }

    public void setOldBoundarySegments(List<String> v) {
        this.oldBoundarySegments = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
    }

    public List<String> getNewBoundarySegments() {
        return newBoundarySegments;
    }

    public void setNewBoundarySegments(List<String> v) {
        this.newBoundarySegments = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
    }

    public List<String> getOldValveCodes() {
        return oldValveCodes;
    }

    public void setOldValveCodes(List<String> v) {
        this.oldValveCodes = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
    }

    public List<String> getNewValveCodes() {
        return newValveCodes;
    }

    public void setNewValveCodes(List<String> v) {
        this.newValveCodes = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
    }

    public List<String> getOldAddressCodes() {
        return oldAddressCodes;
    }

    public void setOldAddressCodes(List<String> v) {
        this.oldAddressCodes = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
    }

    public List<String> getNewAddressCodes() {
        return newAddressCodes;
    }

    public void setNewAddressCodes(List<String> v) {
        this.newAddressCodes = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
    }

    public List<String> getAddedAddressCodes() {
        return addedAddressCodes;
    }

    public void setAddedAddressCodes(List<String> v) {
        this.addedAddressCodes = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
    }

    public List<String> getRemovedAddressCodes() {
        return removedAddressCodes;
    }

    public void setRemovedAddressCodes(List<String> v) {
        this.removedAddressCodes = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
    }

    public List<String> getReleasedPermitCodes() {
        return releasedPermitCodes;
    }

    public void setReleasedPermitCodes(List<String> v) {
        this.releasedPermitCodes = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
    }

    public List<String> getReservedPermitCodes() {
        return reservedPermitCodes;
    }

    public void setReservedPermitCodes(List<String> v) {
        this.reservedPermitCodes = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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
