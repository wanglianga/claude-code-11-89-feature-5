package com.waterrepair.dispatch;

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
 * 一次派工/资源暂留。勘查派工与维修派工分别成行；复压失败后的重新派工产生新的 attemptNo 行，
 * 历史回执通过 assignmentId 永久归属到当时的队伍与派工单。
 */
@Entity
@Table(name = "work_assignment")
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 32)
    private String code;

    @Column(nullable = false)
    private Long incidentId;

    /** SCOUT / REPAIR */
    @Column(nullable = false, length = 16)
    private String kind;

    /** SCOUT_FIRST / RESERVE_BUNDLE / REDISPATCH / DIRECT */
    @Column(nullable = false, length = 24)
    private String strategy;

    @Column(nullable = false, length = 64)
    private String crewCode;

    @Column(nullable = false)
    private OffsetDateTime windowStart;

    @Column(nullable = false)
    private OffsetDateTime windowEnd;

    /** PROVISIONAL（已暂留未出动）/ DISPATCHED / ACTIVE / COMPLETED /
     *  FAILED_PRESSURE / CANCELLED / SUPERSEDED */
    @Column(nullable = false, length = 24)
    private String status;

    @Column(nullable = false)
    private Integer attemptNo = 1;

    @Column(nullable = false)
    private Integer cycleNo = 1;

    private Long parentAssignmentId;
    private Long isolationPlanId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "assignment_valve", joinColumns = @JoinColumn(name = "assignment_id"))
    @OrderColumn(name = "idx")
    @Column(name = "valve_code", length = 32)
    private List<String> valveCodes = new ArrayList<>();

    @Column(length = 512)
    private String note;

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

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getCrewCode() {
        return crewCode;
    }

    public void setCrewCode(String crewCode) {
        this.crewCode = crewCode;
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

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(Integer attemptNo) {
        this.attemptNo = attemptNo;
    }

    public Integer getCycleNo() {
        return cycleNo;
    }

    public void setCycleNo(Integer cycleNo) {
        this.cycleNo = cycleNo;
    }

    public Long getParentAssignmentId() {
        return parentAssignmentId;
    }

    public void setParentAssignmentId(Long parentAssignmentId) {
        this.parentAssignmentId = parentAssignmentId;
    }

    public Long getIsolationPlanId() {
        return isolationPlanId;
    }

    public void setIsolationPlanId(Long isolationPlanId) {
        this.isolationPlanId = isolationPlanId;
    }

    public List<String> getValveCodes() {
        return valveCodes;
    }

    public void setValveCodes(List<String> valveCodes) {
        // 必须拷贝：入参可能是 IsolationPlan 的 Hibernate 持久化集合或不可变 List，
        // 直接引用会触发 "Found shared references to a collection"。
        this.valveCodes = valveCodes == null ? new ArrayList<>() : new ArrayList<>(valveCodes);
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
