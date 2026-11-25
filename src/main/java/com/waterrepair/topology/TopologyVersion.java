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
 * 不可变拓扑版本。每次资产工程师确认现场证据后发布一个新版本，
 * snapshotJson 保存发布时刻管网图（含未知支管标记）的完整快照。
 *
 * <p>事故固定其创建/确认时采用的 topologyVersionCode：新派工与新影响计算用当前
 * ACTIVE 版本，已结案事故则按其固定版本快照复演。</p>
 */
@Entity
@Table(name = "topology_version")
public class TopologyVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 40)
    private String versionCode;

    @Column(nullable = false)
    private Integer versionNo;

    /** ACTIVE / SUPERSEDED */
    @Column(nullable = false, length = 16)
    private String status;

    /** BASELINE / PARTIAL / FULL */
    @Column(nullable = false, length = 16)
    private String revisionMode;

    @Column(length = 40)
    private String parentVersionCode;

    @Column(length = 1024)
    private String changeSummary;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "topology_version_evidence",
            joinColumns = @JoinColumn(name = "version_id"))
    @OrderColumn(name = "idx")
    @Column(name = "evidence_code", length = 40)
    private List<String> evidenceCodes = new ArrayList<>();

    @Column(length = 64)
    private String confirmerName;

    @Column(length = 64)
    private String publishedBy;

    @Column(nullable = false)
    private OffsetDateTime publishedAt;

    /** 该版本开始供新派工/影响计算使用的生效时刻。 */
    @Column(nullable = false)
    private OffsetDateTime effectiveAt;

    @Lob
    @Column(nullable = false, columnDefinition = "text")
    private String snapshotJson;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(String versionCode) {
        this.versionCode = versionCode;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRevisionMode() {
        return revisionMode;
    }

    public void setRevisionMode(String revisionMode) {
        this.revisionMode = revisionMode;
    }

    public String getParentVersionCode() {
        return parentVersionCode;
    }

    public void setParentVersionCode(String parentVersionCode) {
        this.parentVersionCode = parentVersionCode;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public List<String> getEvidenceCodes() {
        return evidenceCodes;
    }

    public void setEvidenceCodes(List<String> evidenceCodes) {
        this.evidenceCodes = evidenceCodes == null ? new ArrayList<>() : new ArrayList<>(evidenceCodes);
    }

    public String getConfirmerName() {
        return confirmerName;
    }

    public void setConfirmerName(String confirmerName) {
        this.confirmerName = confirmerName;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(String publishedBy) {
        this.publishedBy = publishedBy;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public OffsetDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(OffsetDateTime effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
