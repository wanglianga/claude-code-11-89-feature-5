package com.waterrepair.receipt;

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
 * 现场回执。五类回执按 ARRIVAL → ISOLATION → REPAIR → FLUSH → REPRESSURE 严格有序，
 * 按 (incidentId, cycleNo) 维护一条链；复压失败后重新派工会开启新 cycle，
 * 旧 cycle 的全部回执原样保留，并通过 assignmentId 归属到当时的派工单与班组。
 */
@Entity
@Table(name = "field_receipt")
public class FieldReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long incidentId;

    @Column(nullable = false)
    private Long assignmentId;

    @Column(length = 64)
    private String assignmentCode;

    @Column(length = 64)
    private String crewCode;

    @Column(nullable = false)
    private Integer cycleNo;

    /** 0..4，对应 RECEIPT_SEQUENCE */
    @Column(nullable = false)
    private Integer stepIndex;

    /** ARRIVAL / ISOLATION / REPAIR / FLUSH / REPRESSURE */
    @Column(nullable = false, length = 16)
    private String type;

    /** 现场业务时刻（回放请求显式给出，保证确定） */
    @Column(nullable = false)
    private OffsetDateTime eventAt;

    @Column(length = 1024)
    private String note;

    private String readingCode;
    private String deviceNo;
    private String segmentCode;
    private Double measuredPressureMpa;
    private Double normalMinMpa;
    private Double normalMaxMpa;
    private Long correctedTimeDeltaSeconds;
    private Integer timeToleranceSeconds;

    /** NOT_REQUIRED / PASSED / FAILED */
    @Column(length = 16)
    private String verificationStatus;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "receipt_verify_reason", joinColumns = @JoinColumn(name = "receipt_id"))
    @OrderColumn(name = "idx")
    @Column(name = "reason", length = 512)
    private List<String> verificationReasons = new ArrayList<>();

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

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public String getAssignmentCode() {
        return assignmentCode;
    }

    public void setAssignmentCode(String assignmentCode) {
        this.assignmentCode = assignmentCode;
    }

    public String getCrewCode() {
        return crewCode;
    }

    public void setCrewCode(String crewCode) {
        this.crewCode = crewCode;
    }

    public Integer getCycleNo() {
        return cycleNo;
    }

    public void setCycleNo(Integer cycleNo) {
        this.cycleNo = cycleNo;
    }

    public Integer getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(Integer stepIndex) {
        this.stepIndex = stepIndex;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public OffsetDateTime getEventAt() {
        return eventAt;
    }

    public void setEventAt(OffsetDateTime eventAt) {
        this.eventAt = eventAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getReadingCode() {
        return readingCode;
    }

    public void setReadingCode(String readingCode) {
        this.readingCode = readingCode;
    }

    public String getDeviceNo() {
        return deviceNo;
    }

    public void setDeviceNo(String deviceNo) {
        this.deviceNo = deviceNo;
    }

    public String getSegmentCode() {
        return segmentCode;
    }

    public void setSegmentCode(String segmentCode) {
        this.segmentCode = segmentCode;
    }

    public Double getMeasuredPressureMpa() {
        return measuredPressureMpa;
    }

    public void setMeasuredPressureMpa(Double measuredPressureMpa) {
        this.measuredPressureMpa = measuredPressureMpa;
    }

    public Double getNormalMinMpa() {
        return normalMinMpa;
    }

    public void setNormalMinMpa(Double normalMinMpa) {
        this.normalMinMpa = normalMinMpa;
    }

    public Double getNormalMaxMpa() {
        return normalMaxMpa;
    }

    public void setNormalMaxMpa(Double normalMaxMpa) {
        this.normalMaxMpa = normalMaxMpa;
    }

    public Long getCorrectedTimeDeltaSeconds() {
        return correctedTimeDeltaSeconds;
    }

    public void setCorrectedTimeDeltaSeconds(Long correctedTimeDeltaSeconds) {
        this.correctedTimeDeltaSeconds = correctedTimeDeltaSeconds;
    }

    public Integer getTimeToleranceSeconds() {
        return timeToleranceSeconds;
    }

    public void setTimeToleranceSeconds(Integer timeToleranceSeconds) {
        this.timeToleranceSeconds = timeToleranceSeconds;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public List<String> getVerificationReasons() {
        return verificationReasons;
    }

    public void setVerificationReasons(List<String> verificationReasons) {
        this.verificationReasons = verificationReasons == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(verificationReasons);
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
