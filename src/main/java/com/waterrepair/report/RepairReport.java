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
 * 一条热线/现场报修线索。原文（rawAddress/description/photoRefs）永久保存，
 * 重拨通过 redialOfId / hotlineCallId 串联，只作为新的来源追加，不生成新事故。
 */
@Entity
@Table(name = "repair_report")
public class RepairReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务编号 R-0001，持久化时按 id 生成 */
    @Column(unique = true, length = 32)
    private String code;

    @Column(length = 64)
    private String hotlineCallId;

    private Long redialOfId;

    @Column(length = 32)
    private String callerPhone;

    @Column(nullable = false, length = 512)
    private String rawAddress;

    @Column(length = 2048)
    private String description;

    private Double lng;
    private Double lat;

    /** 接听时刻（业务发生时间，由请求给出，保证回放确定） */
    @Column(nullable = false)
    private OffsetDateTime receivedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "report_photo", joinColumns = @JoinColumn(name = "report_id"))
    @OrderColumn(name = "idx")
    @Column(name = "photo_ref", length = 256)
    private List<String> photoRefs = new ArrayList<>();

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public RepairReport() {
    }

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

    public String getHotlineCallId() {
        return hotlineCallId;
    }

    public void setHotlineCallId(String hotlineCallId) {
        this.hotlineCallId = hotlineCallId;
    }

    public Long getRedialOfId() {
        return redialOfId;
    }

    public void setRedialOfId(Long redialOfId) {
        this.redialOfId = redialOfId;
    }

    public String getCallerPhone() {
        return callerPhone;
    }

    public void setCallerPhone(String callerPhone) {
        this.callerPhone = callerPhone;
    }

    public String getRawAddress() {
        return rawAddress;
    }

    public void setRawAddress(String rawAddress) {
        this.rawAddress = rawAddress;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public List<String> getPhotoRefs() {
        return photoRefs;
    }

    public void setPhotoRefs(List<String> photoRefs) {
        this.photoRefs = photoRefs == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(photoRefs);
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
