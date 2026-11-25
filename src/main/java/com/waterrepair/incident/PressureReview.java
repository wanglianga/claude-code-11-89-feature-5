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
 * 压力复核结论。OVERTURNS 时携带更正后的边界与阀门，触发范围/占用/通知更正。
 */
@Entity
@Table(name = "pressure_review")
public class PressureReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long incidentId;

    /** SUPPORTS / OVERTURNS */
    @Column(nullable = false, length = 16)
    private String verdict;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "review_evidence_reading", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "idx")
    @Column(name = "reading_code", length = 64)
    private List<String> evidenceReadingCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "review_corrected_boundary", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "idx")
    @Column(name = "segment_code", length = 32)
    private List<String> correctedBoundarySegmentCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "review_corrected_valve", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "idx")
    @Column(name = "valve_code", length = 32)
    private List<String> correctedValveCodes = new ArrayList<>();

    @Column(length = 1024)
    private String note;

    @Column(length = 64)
    private String reviewer;

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

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public List<String> getEvidenceReadingCodes() {
        return evidenceReadingCodes;
    }

    public void setEvidenceReadingCodes(List<String> v) {
        this.evidenceReadingCodes = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
    }

    public List<String> getCorrectedBoundarySegmentCodes() {
        return correctedBoundarySegmentCodes;
    }

    public void setCorrectedBoundarySegmentCodes(List<String> v) {
        this.correctedBoundarySegmentCodes = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
    }

    public List<String> getCorrectedValveCodes() {
        return correctedValveCodes;
    }

    public void setCorrectedValveCodes(List<String> v) {
        this.correctedValveCodes = v == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(v);
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
