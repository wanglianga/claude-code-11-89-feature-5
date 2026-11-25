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
 * 交叉路口/地标：可能正落在相邻管段之间，归属判断必须给出多个候选段，
 * 不能用“最近坐标”直接并成同一事故。
 */
@Entity
@Table(name = "geo_landmark")
public class GeoLandmark {

    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    private Double lng;
    private Double lat;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "landmark_candidate_segment", joinColumns = @JoinColumn(name = "landmark_code"))
    @OrderColumn(name = "idx")
    @Column(name = "segment_code", length = 32)
    private List<String> candidateSegmentCodes = new ArrayList<>();

    public GeoLandmark() {
    }

    public GeoLandmark(String code, String name, Double lng, Double lat, List<String> candidateSegmentCodes) {
        this.code = code;
        this.name = name;
        this.lng = lng;
        this.lat = lat;
        if (candidateSegmentCodes != null) {
            this.candidateSegmentCodes = candidateSegmentCodes;
        }
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Double getLng() {
        return lng;
    }

    public Double getLat() {
        return lat;
    }

    public List<String> getCandidateSegmentCodes() {
        return candidateSegmentCodes;
    }
}
