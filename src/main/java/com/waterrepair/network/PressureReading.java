package com.waterrepair.network;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 压力读数。clockOffsetSeconds 为设备校时偏差：
 * 校时后的真实采样时刻 = sampledAt + clockOffsetSeconds。
 */
@Entity
@Table(name = "pressure_reading")
public class PressureReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务编号，如 PR-20261126-001，回执中按编号引用 */
    @Column(nullable = false, unique = true, length = 64)
    private String readingCode;

    @Column(nullable = false, length = 64)
    private String deviceNo;

    @Column(nullable = false, length = 32)
    private String segmentCode;

    @Column(nullable = false)
    private Double pressureMpa;

    @Column(nullable = false)
    private OffsetDateTime sampledAt;

    @Column(nullable = false)
    private Integer clockOffsetSeconds = 0;

    @Column(length = 256)
    private String note;

    public PressureReading() {
    }

    public PressureReading(String readingCode, String deviceNo, String segmentCode, Double pressureMpa,
                           OffsetDateTime sampledAt, Integer clockOffsetSeconds, String note) {
        this.readingCode = readingCode;
        this.deviceNo = deviceNo;
        this.segmentCode = segmentCode;
        this.pressureMpa = pressureMpa;
        this.sampledAt = sampledAt;
        this.clockOffsetSeconds = clockOffsetSeconds;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public String getReadingCode() {
        return readingCode;
    }

    public String getDeviceNo() {
        return deviceNo;
    }

    public String getSegmentCode() {
        return segmentCode;
    }

    public Double getPressureMpa() {
        return pressureMpa;
    }

    public OffsetDateTime getSampledAt() {
        return sampledAt;
    }

    public Integer getClockOffsetSeconds() {
        return clockOffsetSeconds;
    }

    public String getNote() {
        return note;
    }
}
