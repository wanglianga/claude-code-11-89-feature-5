package com.waterrepair.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tool_item")
public class ToolItem {

    @Id
    @Column(length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private boolean available = true;

    @Column(length = 128)
    private String unavailableReason;

    public ToolItem() {
    }

    public ToolItem(String code, String name, boolean available, String unavailableReason) {
        this.code = code;
        this.name = name;
        this.available = available;
        this.unavailableReason = unavailableReason;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }
}
