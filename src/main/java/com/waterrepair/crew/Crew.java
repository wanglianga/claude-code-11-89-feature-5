package com.waterrepair.crew;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 抢修/勘查班组。qualificationCodes 为持有的许可：
 * Q_INSPECT 勘查判断、Q_REPAIR 管道维修、Q_VALVE_STD 常规阀门操作、Q_VALVE_LARGE 大阀(V1)操作。
 */
@Entity
@Table(name = "crew")
public class Crew {

    @Id
    @Column(length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private LocalTime shiftStart;

    @Column(nullable = false)
    private LocalTime shiftEnd;

    @Column(length = 32)
    private String contactPhone;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 64)
    private String primaryVehicleCode;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "crew_qualification", joinColumns = @JoinColumn(name = "crew_code"))
    @OrderColumn(name = "idx")
    @Column(name = "qualification_code", length = 64)
    private List<String> qualificationCodes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "crew_standard_tool", joinColumns = @JoinColumn(name = "crew_code"))
    @OrderColumn(name = "idx")
    @Column(name = "tool_code", length = 64)
    private List<String> standardToolCodes = new ArrayList<>();

    public Crew() {
    }

    public Crew(String code, String name, LocalTime shiftStart, LocalTime shiftEnd, String contactPhone,
                boolean active, String primaryVehicleCode,
                List<String> qualificationCodes, List<String> standardToolCodes) {
        this.code = code;
        this.name = name;
        this.shiftStart = shiftStart;
        this.shiftEnd = shiftEnd;
        this.contactPhone = contactPhone;
        this.active = active;
        this.primaryVehicleCode = primaryVehicleCode;
        if (qualificationCodes != null) {
            this.qualificationCodes = qualificationCodes;
        }
        if (standardToolCodes != null) {
            this.standardToolCodes = standardToolCodes;
        }
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public LocalTime getShiftStart() {
        return shiftStart;
    }

    public LocalTime getShiftEnd() {
        return shiftEnd;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public boolean isActive() {
        return active;
    }

    public String getPrimaryVehicleCode() {
        return primaryVehicleCode;
    }

    public List<String> getQualificationCodes() {
        return qualificationCodes;
    }

    public List<String> getStandardToolCodes() {
        return standardToolCodes;
    }
}
