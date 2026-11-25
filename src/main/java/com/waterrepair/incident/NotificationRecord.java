package com.waterrepair.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 发给受影响地址的通知。旧通知在压力复核更正时不会被删除，
 * 而是置为 SUPERSEDED 并由新的 CORRECTION/CORRECTION_CANCEL 通知替代，形成可追溯链。
 */
@Entity
@Table(name = "notification_record")
public class NotificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long incidentId;

    private Long planId;
    private Long correctionId;

    @Column(nullable = false, length = 32)
    private String addressCode;

    /** SMS / SIMULATED */
    @Column(nullable = false, length = 16)
    private String channel = "SIMULATED_SMS";

    @Column(nullable = false, length = 1024)
    private String content;

    /** INITIAL / CORRECTION / CORRECTION_CANCEL */
    @Column(nullable = false, length = 24)
    private String kind;

    /** SENT / SUPERSEDED */
    @Column(nullable = false, length = 16)
    private String status = "SENT";

    private Long replacesNotificationId;

    private Long supersededByCorrectionId;

    @Column(nullable = false)
    private OffsetDateTime sentAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public Long getCorrectionId() {
        return correctionId;
    }

    public void setCorrectionId(Long correctionId) {
        this.correctionId = correctionId;
    }

    public String getAddressCode() {
        return addressCode;
    }

    public void setAddressCode(String addressCode) {
        this.addressCode = addressCode;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getReplacesNotificationId() {
        return replacesNotificationId;
    }

    public void setReplacesNotificationId(Long replacesNotificationId) {
        this.replacesNotificationId = replacesNotificationId;
    }

    public Long getSupersededByCorrectionId() {
        return supersededByCorrectionId;
    }

    public void setSupersededByCorrectionId(Long supersededByCorrectionId) {
        this.supersededByCorrectionId = supersededByCorrectionId;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(OffsetDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
