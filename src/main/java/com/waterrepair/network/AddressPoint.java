package com.waterrepair.network;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * 地址点：映射到唯一管段。aliases 保存可被报修原文命中的地址写法。
 * building 仅作楼栋信息展示——相同楼栋不构成合并事故的依据。
 */
@Entity
@Table(name = "address_point")
public class AddressPoint {

    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String displayAddress;

    @Column(nullable = false, length = 32)
    private String segmentCode;

    @Column(length = 64)
    private String building;

    private Double lng;
    private Double lat;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "address_alias", joinColumns = @JoinColumn(name = "address_code"))
    @OrderColumn(name = "alias_order")
    @Column(name = "alias", length = 128)
    private List<String> aliases = new ArrayList<>();

    public AddressPoint() {
    }

    public AddressPoint(String code, String displayAddress, String segmentCode, String building,
                        Double lng, Double lat, List<String> aliases) {
        this.code = code;
        this.displayAddress = displayAddress;
        this.segmentCode = segmentCode;
        this.building = building;
        this.lng = lng;
        this.lat = lat;
        if (aliases != null) {
            this.aliases = aliases;
        }
    }

    public String getCode() {
        return code;
    }

    public String getDisplayAddress() {
        return displayAddress;
    }

    public String getSegmentCode() {
        return segmentCode;
    }

    public String getBuilding() {
        return building;
    }

    public Double getLng() {
        return lng;
    }

    public Double getLat() {
        return lat;
    }

    public List<String> getAliases() {
        return aliases;
    }
}
