package com.waterrepair.report;

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
 * 一次归属判断。自动判断与调度/复核的人工判断都追加新行，历史不覆盖，
 * 以便核对每条线索的归属是如何变化的。
 */
@Entity
@Table(name = "attribution_judgment")
public class AttributionJudgment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reportId;

    @Column(nullable = false)
    private Integer versionNo;

    /** AUTO / MANUAL */
    @Column(nullable = false, length = 16)
    private String source;

    /** MATCHED / AMBIGUOUS / UNMATCHED / CONFIRMED */
    @Column(nullable = false, length = 16)
    private String status;

    /** ADDRESS_ALIAS / LANDMARK / COORDINATE / BUILDING / MANUAL */
    @Column(length = 32)
    private String method;

    @Column(length = 32)
    private String matchedAddressCode;

    @Column(length = 32)
    private String nearestAddressCode;

    private Double nearestDistanceM;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "judgment_candidate_segment",
            joinColumns = @JoinColumn(name = "judgment_id"))
    @OrderColumn(name = "idx")
    @Column(name = "segment_code", length = 32)
    private List<String> candidateSegmentCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "judgment_selected_segment",
            joinColumns = @JoinColumn(name = "judgment_id"))
    @OrderColumn(name = "idx")
    @Column(name = "segment_code", length = 32)
    private List<String> selectedSegmentCodes = new ArrayList<>();

    @Column(length = 1024)
    private String reason;

    @Column(length = 64)
    private String judgedBy;

    @Column(nullable = false)
    private OffsetDateTime judgedAt;

    public AttributionJudgment() {
    }

    public Long getId() {
        return id;
    }

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getMatchedAddressCode() {
        return matchedAddressCode;
    }

    public void setMatchedAddressCode(String matchedAddressCode) {
        this.matchedAddressCode = matchedAddressCode;
    }

    public String getNearestAddressCode() {
        return nearestAddressCode;
    }

    public void setNearestAddressCode(String nearestAddressCode) {
        this.nearestAddressCode = nearestAddressCode;
    }

    public Double getNearestDistanceM() {
        return nearestDistanceM;
    }

    public void setNearestDistanceM(Double nearestDistanceM) {
        this.nearestDistanceM = nearestDistanceM;
    }

    public List<String> getCandidateSegmentCodes() {
        return candidateSegmentCodes;
    }

    public void setCandidateSegmentCodes(List<String> candidateSegmentCodes) {
        this.candidateSegmentCodes = candidateSegmentCodes == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(candidateSegmentCodes);
    }

    public List<String> getSelectedSegmentCodes() {
        return selectedSegmentCodes;
    }

    public void setSelectedSegmentCodes(List<String> selectedSegmentCodes) {
        this.selectedSegmentCodes = selectedSegmentCodes == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(selectedSegmentCodes);
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getJudgedBy() {
        return judgedBy;
    }

    public void setJudgedBy(String judgedBy) {
        this.judgedBy = judgedBy;
    }

    public OffsetDateTime getJudgedAt() {
        return judgedAt;
    }

    public void setJudgedAt(OffsetDateTime judgedAt) {
        this.judgedAt = judgedAt;
    }
}
