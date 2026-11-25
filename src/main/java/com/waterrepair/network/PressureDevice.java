package com.waterrepair.network;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 压力采集设备台账，设备归属于某一支管。
 */
@Entity
@Table(name = "pressure_device")
public class PressureDevice {

    @Id
    @Column(length = 64)
    private String deviceNo;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String segmentCode;

    public PressureDevice() {
    }

    public PressureDevice(String deviceNo, String name, String segmentCode) {
        this.deviceNo = deviceNo;
        this.name = name;
        this.segmentCode = segmentCode;
    }

    public String getDeviceNo() {
        return deviceNo;
    }

    public String getName() {
        return name;
    }

    public String getSegmentCode() {
        return segmentCode;
    }
}
