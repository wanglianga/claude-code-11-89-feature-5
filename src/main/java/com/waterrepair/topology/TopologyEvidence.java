package com.waterrepair.topology;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 拓扑修订的可审查现场证据：现场草图、管段两端标识（节点/标记桩/坐标）、
 * 测距结果、测量误差、测量人与复核人。测量人与复核人不能为同一人（双人核验）。
 */
@Entity
@Table(name = "topology_evidence")
public class TopologyEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 40)
    private String code;

    @Column(length = 256)
    private String title;

    private Long incidentId;

    /** 现场草图的附件/图号引用，如 evidence/sketch-S7-S8.png。 */
    @Column(length = 256)
    private String sketchRef;

    /** 草图内容的文字转录/示意，保证无图片存储时证据仍可审查。 */
    @Column(length = 4000)
    private String sketchDescription;

    // ---- 管段/连接两端标识 ----
    @Column(length = 32)
    private String endANodeCode;
    @Column(length = 200)
    private String endAIdentifier;
    @Column(length = 64)
    private String endAMarker;
    private Double endALng;
    private Double endALat;

    @Column(length = 32)
    private String endBNodeCode;
    @Column(length = 200)
    private String endBIdentifier;
    @Column(length = 64)
    private String endBMarker;
    private Double endBLng;
    private Double endBLat;

    /** 测距结果（米）。 */
    private Double measuredDistanceM;

    /** 测量误差（米），样例 S7-S8 为 ±0.2m。 */
    private Double measurementErrorM;

    @Column(length = 128)
    private String measurementMethod;

    @Column(nullable = false, length = 64)
    private String surveyorName;

    private OffsetDateTime surveyedAt;

    @Column(length = 64)
    private String reviewerName;

    private OffsetDateTime reviewedAt;

    @Column(length = 1024)
    private String reviewNote;

    /** REGISTERED（待双人复核）/ DUAL_CHECKED（双人核验通过，可送资产工程师确认）。 */
    @Column(nullable = false, length = 24)
    private String status = "REGISTERED";

    @Column(length = 64)
    private String createdBy;

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

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public String getSketchRef() {
        return sketchRef;
    }

    public void setSketchRef(String sketchRef) {
        this.sketchRef = sketchRef;
    }

    public String getSketchDescription() {
        return sketchDescription;
    }

    public void setSketchDescription(String sketchDescription) {
        this.sketchDescription = sketchDescription;
    }

    public String getEndANodeCode() {
        return endANodeCode;
    }

    public void setEndANodeCode(String endANodeCode) {
        this.endANodeCode = endANodeCode;
    }

    public String getEndAIdentifier() {
        return endAIdentifier;
    }

    public void setEndAIdentifier(String endAIdentifier) {
        this.endAIdentifier = endAIdentifier;
    }

    public String getEndAMarker() {
        return endAMarker;
    }

    public void setEndAMarker(String endAMarker) {
        this.endAMarker = endAMarker;
    }

    public Double getEndALng() {
        return endALng;
    }

    public void setEndALng(Double endALng) {
        this.endALng = endALng;
    }

    public Double getEndALat() {
        return endALat;
    }

    public void setEndALat(Double endALat) {
        this.endALat = endALat;
    }

    public String getEndBNodeCode() {
        return endBNodeCode;
    }

    public void setEndBNodeCode(String endBNodeCode) {
        this.endBNodeCode = endBNodeCode;
    }

    public String getEndBIdentifier() {
        return endBIdentifier;
    }

    public void setEndBIdentifier(String endBIdentifier) {
        this.endBIdentifier = endBIdentifier;
    }

    public String getEndBMarker() {
        return endBMarker;
    }

    public void setEndBMarker(String endBMarker) {
        this.endBMarker = endBMarker;
    }

    public Double getEndBLng() {
        return endBLng;
    }

    public void setEndBLng(Double endBLng) {
        this.endBLng = endBLng;
    }

    public Double getEndBLat() {
        return endBLat;
    }

    public void setEndBLat(Double endBLat) {
        this.endBLat = endBLat;
    }

    public Double getMeasuredDistanceM() {
        return measuredDistanceM;
    }

    public void setMeasuredDistanceM(Double measuredDistanceM) {
        this.measuredDistanceM = measuredDistanceM;
    }

    public Double getMeasurementErrorM() {
        return measurementErrorM;
    }

    public void setMeasurementErrorM(Double measurementErrorM) {
        this.measurementErrorM = measurementErrorM;
    }

    public String getMeasurementMethod() {
        return measurementMethod;
    }

    public void setMeasurementMethod(String measurementMethod) {
        this.measurementMethod = measurementMethod;
    }

    public String getSurveyorName() {
        return surveyorName;
    }

    public void setSurveyorName(String surveyorName) {
        this.surveyorName = surveyorName;
    }

    public OffsetDateTime getSurveyedAt() {
        return surveyedAt;
    }

    public void setSurveyedAt(OffsetDateTime surveyedAt) {
        this.surveyedAt = surveyedAt;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName) {
        this.reviewerName = reviewerName;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
