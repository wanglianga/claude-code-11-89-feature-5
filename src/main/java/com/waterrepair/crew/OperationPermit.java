package com.waterrepair.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 阀门操作许可（独占操作票资源）：同一时间窗口只能被一个事故/班组占用。
 * 与班组持有的资质许可互补：资质回答“有没有资格”，操作票回答“此刻这把阀归谁操作”。
 */
@Entity
@Table(name = "operation_permit")
public class OperationPermit {

    @Id
    @Column(length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String valveCode;

    public OperationPermit() {
    }

    public OperationPermit(String code, String name, String valveCode) {
        this.code = code;
        this.name = name;
        this.valveCode = valveCode;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getValveCode() {
        return valveCode;
    }
}
