package com.waterrepair.topology;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TopologyChangeReviewRepository extends JpaRepository<TopologyChangeReview, Long> {
    List<TopologyChangeReview> findByTopologyVersionCodeOrderByIdAsc(String topologyVersionCode);
    List<TopologyChangeReview> findByIncidentIdOrderByIdDesc(Long incidentId);
    List<TopologyChangeReview> findAllByOrderByIdAsc();
}
