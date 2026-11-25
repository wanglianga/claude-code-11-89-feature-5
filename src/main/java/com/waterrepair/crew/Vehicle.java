package com.waterrepair.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    @Column(length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 32)
    private String plateNo;

    @Column(nullable = false)
    private boolean available = true;

    @Column(length = 128)
    private String unavailableReason;

    public Vehicle() {
    }

    public Vehicle(String code, String name, String plateNo, boolean available, String unavailableReason) {
        this.code = code;
        this.name = name;
        this.plateNo = plateNo;
        this.available = available;
        this.unavailableReason = unavailableReason;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getPlateNo() {
        return plateNo;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }
}
