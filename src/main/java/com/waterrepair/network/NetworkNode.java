package com.waterrepair.network;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 管网节点：水源、三通、阀门或末端。阀门节点的 code 与 ValveSpec 对应。
 */
@Entity
@Table(name = "network_node")
public class NetworkNode {

    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    /** SOURCE / TEE / VALVE / END */
    @Column(nullable = false, length = 16)
    private String type;

    private Double lng;
    private Double lat;

    public NetworkNode() {
    }

    public NetworkNode(String code, String name, String type, Double lng, Double lat) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.lng = lng;
        this.lat = lat;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Double getLng() {
        return lng;
    }

    public Double getLat() {
        return lat;
    }
}
