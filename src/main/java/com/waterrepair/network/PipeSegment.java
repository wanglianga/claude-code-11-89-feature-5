package com.waterrepair.network;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 管段：连接两个节点。normalPressureMin/Max 为该支管复压核验的正常压力区间（MPa）。
 */
@Entity
@Table(name = "pipe_segment")
public class PipeSegment {

    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String fromNodeCode;

    @Column(nullable = false, length = 32)
    private String toNodeCode;

    @Column(nullable = false)
    private Double normalPressureMinMpa;

    @Column(nullable = false)
    private Double normalPressureMaxMpa;

    /** 采样时刻经校时偏差修正后，与复压回执时刻允许的最大偏差（秒） */
    @Column(nullable = false)
    private Integer pressureTimeToleranceSeconds;

    /**
     * 拓扑可信状态：CONFIRMED（已测量复核，默认）/ UNKNOWN（相邻未测支管，
     * 不进入权威连通计算，只能作为未知风险提示）。用可空列兼容旧库，null 视为 CONFIRMED。
     */
    @Column(length = 16)
    private String topologyStatus;

    public PipeSegment() {
    }

    public PipeSegment(String code, String name, String fromNodeCode, String toNodeCode,
                       Double normalPressureMinMpa, Double normalPressureMaxMpa,
                       Integer pressureTimeToleranceSeconds) {
        this.code = code;
        this.name = name;
        this.fromNodeCode = fromNodeCode;
        this.toNodeCode = toNodeCode;
        this.normalPressureMinMpa = normalPressureMinMpa;
        this.normalPressureMaxMpa = normalPressureMaxMpa;
        this.pressureTimeToleranceSeconds = pressureTimeToleranceSeconds;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getFromNodeCode() {
        return fromNodeCode;
    }

    public String getToNodeCode() {
        return toNodeCode;
    }

    public Double getNormalPressureMinMpa() {
        return normalPressureMinMpa;
    }

    public Double getNormalPressureMaxMpa() {
        return normalPressureMaxMpa;
    }

    public Integer getPressureTimeToleranceSeconds() {
        return pressureTimeToleranceSeconds;
    }

    public String getTopologyStatus() {
        return topologyStatus == null ? "CONFIRMED" : topologyStatus;
    }

    public void setTopologyStatus(String topologyStatus) {
        this.topologyStatus = topologyStatus;
    }
}
