package com.waterrepair.topology;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 拓扑新版本发布后，对引用旧版本的开放事故/工单生成的人工重审单。
 *
 * <p>它只“指出”差异：旧阀门组合/地址集合/已通知对象，与新版本下的候选组合；
 * 调度确认后才会生成新的隔离计划版本。发布与确认都不会改写或撤销旧通知。</p>
 */
@Entity
@Table(name = "topology_change_review")
public class TopologyChangeReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 40)
    private String topologyVersionCode;

    @Column(nullable = false)
    private Long incidentId;

    @Column(length = 40)
    private String incidentCode;

    @Column(nullable = false, length = 40)
    private String oldTopologyVersionCode;

    /** PENDING_CONFIRMATION / CONFIRMED / DISMISSED */
    @Column(nullable = false, length = 32)
    private String status = "PENDING_CONFIRMATION";

    @Column(nullable = false)
    private Boolean affected = Boolean.TRUE;

    private Long oldPlanId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tcr_old_valve", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "idx")
    @Column(name = "valve_code", length = 32)
    private List<String> oldValveCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tcr_old_segment", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "idx")
    @Column(name = "segment_code", length = 32)
    private List<String> oldOutageSegmentCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tcr_old_addr", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "idx")
    @Column(name = "address_code", length = 32)
    private List<String> oldAddressCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tcr_rec_valve", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "idx")
    @Column(name = "valve_code", length = 32)
    private List<String> recommendedValveCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tcr_added_addr", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "idx")
    @Column(name = "address_code", length = 32)
    private List<String> addedAddressCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tcr_removed_addr", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "idx")
    @Column(name = "address_code", length = 32)
    private List<String> removedAddressCodes = new ArrayList<>();

    /** 未知支管上的候选地址：只能提示人工核查，不得自动纳入新范围/通知。 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tcr_uncertain_addr", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "idx")
    @Column(name = "address_code", length = 32)
    private List<String> uncertainAddressCodes = new ArrayList<>();

    /** 新版本下全部候选阀门组合（含各自失压管段与地址集合）的 JSON。 */
    @Lob
    @Column(columnDefinition = "text")
    private String candidateCombosJson;

    /** 发布时点“已通知对象”的快照 JSON（通知 id/地址/类型/状态/渠道/时刻）。 */
    @Lob
    @Column(columnDefinition = "text")
    private String notifiedObjectsJson;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tcr_conf_valve", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "idx")
    @Column(name = "valve_code", length = 32)
    private List<String> confirmedValveCodes = new ArrayList<>();

    private Long newPlanId;

    @Column(length = 64)
    private String confirmedBy;
    private OffsetDateTime confirmedAt;
    @Column(length = 1024)
    private String confirmationNote;

    @Column(length = 64)
    private String dismissedBy;
    private OffsetDateTime dismissedAt;
    @Column(length = 1024)
    private String dismissReason;

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

    public String getTopologyVersionCode() {
        return topologyVersionCode;
    }

    public void setTopologyVersionCode(String topologyVersionCode) {
        this.topologyVersionCode = topologyVersionCode;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public String getIncidentCode() {
        return incidentCode;
    }

    public void setIncidentCode(String incidentCode) {
        this.incidentCode = incidentCode;
    }

    public String getOldTopologyVersionCode() {
        return oldTopologyVersionCode;
    }

    public void setOldTopologyVersionCode(String oldTopologyVersionCode) {
        this.oldTopologyVersionCode = oldTopologyVersionCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getAffected() {
        return affected;
    }

    public void setAffected(Boolean affected) {
        this.affected = affected;
    }

    public Long getOldPlanId() {
        return oldPlanId;
    }

    public void setOldPlanId(Long oldPlanId) {
        this.oldPlanId = oldPlanId;
    }

    public List<String> getOldValveCodes() {
        return oldValveCodes;
    }

    public void setOldValveCodes(List<String> oldValveCodes) {
        this.oldValveCodes = oldValveCodes == null ? new ArrayList<>() : new ArrayList<>(oldValveCodes);
    }

    public List<String> getOldOutageSegmentCodes() {
        return oldOutageSegmentCodes;
    }

    public void setOldOutageSegmentCodes(List<String> oldOutageSegmentCodes) {
        this.oldOutageSegmentCodes = oldOutageSegmentCodes == null ? new ArrayList<>() : new ArrayList<>(oldOutageSegmentCodes);
    }

    public List<String> getOldAddressCodes() {
        return oldAddressCodes;
    }

    public void setOldAddressCodes(List<String> oldAddressCodes) {
        this.oldAddressCodes = oldAddressCodes == null ? new ArrayList<>() : new ArrayList<>(oldAddressCodes);
    }

    public List<String> getRecommendedValveCodes() {
        return recommendedValveCodes;
    }

    public void setRecommendedValveCodes(List<String> recommendedValveCodes) {
        this.recommendedValveCodes = recommendedValveCodes == null ? new ArrayList<>() : new ArrayList<>(recommendedValveCodes);
    }

    public List<String> getAddedAddressCodes() {
        return addedAddressCodes;
    }

    public void setAddedAddressCodes(List<String> addedAddressCodes) {
        this.addedAddressCodes = addedAddressCodes == null ? new ArrayList<>() : new ArrayList<>(addedAddressCodes);
    }

    public List<String> getRemovedAddressCodes() {
        return removedAddressCodes;
    }

    public void setRemovedAddressCodes(List<String> removedAddressCodes) {
        this.removedAddressCodes = removedAddressCodes == null ? new ArrayList<>() : new ArrayList<>(removedAddressCodes);
    }

    public List<String> getUncertainAddressCodes() {
        return uncertainAddressCodes;
    }

    public void setUncertainAddressCodes(List<String> uncertainAddressCodes) {
        this.uncertainAddressCodes = uncertainAddressCodes == null ? new ArrayList<>() : new ArrayList<>(uncertainAddressCodes);
    }

    public String getCandidateCombosJson() {
        return candidateCombosJson;
    }

    public void setCandidateCombosJson(String candidateCombosJson) {
        this.candidateCombosJson = candidateCombosJson;
    }

    public String getNotifiedObjectsJson() {
        return notifiedObjectsJson;
    }

    public void setNotifiedObjectsJson(String notifiedObjectsJson) {
        this.notifiedObjectsJson = notifiedObjectsJson;
    }

    public List<String> getConfirmedValveCodes() {
        return confirmedValveCodes;
    }

    public void setConfirmedValveCodes(List<String> confirmedValveCodes) {
        this.confirmedValveCodes = confirmedValveCodes == null ? new ArrayList<>() : new ArrayList<>(confirmedValveCodes);
    }

    public Long getNewPlanId() {
        return newPlanId;
    }

    public void setNewPlanId(Long newPlanId) {
        this.newPlanId = newPlanId;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(String confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getConfirmationNote() {
        return confirmationNote;
    }

    public void setConfirmationNote(String confirmationNote) {
        this.confirmationNote = confirmationNote;
    }

    public String getDismissedBy() {
        return dismissedBy;
    }

    public void setDismissedBy(String dismissedBy) {
        this.dismissedBy = dismissedBy;
    }

    public OffsetDateTime getDismissedAt() {
        return dismissedAt;
    }

    public void setDismissedAt(OffsetDateTime dismissedAt) {
        this.dismissedAt = dismissedAt;
    }

    public String getDismissReason() {
        return dismissReason;
    }

    public void setDismissReason(String dismissReason) {
        this.dismissReason = dismissReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
