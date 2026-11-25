package com.waterrepair.network;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 阀门台账。requiredQualification 为操作该阀所需的班组许可编码。
 * exclusivePermitCode 对应一把必须独占占用的“阀门操作许可/操作票”资源。
 */
@Entity
@Table(name = "valve_spec")
public class ValveSpec {

    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String nodeCode;

    @Column(nullable = false, length = 64)
    private String requiredQualification;

    @Column(nullable = false, length = 64)
    private String exclusivePermitCode;

    public ValveSpec() {
    }

    public ValveSpec(String code, String name, String nodeCode,
                     String requiredQualification, String exclusivePermitCode) {
        this.code = code;
        this.name = name;
        this.nodeCode = nodeCode;
        this.requiredQualification = requiredQualification;
        this.exclusivePermitCode = exclusivePermitCode;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public String getRequiredQualification() {
        return requiredQualification;
    }

    public String getExclusivePermitCode() {
        return exclusivePermitCode;
    }
}
