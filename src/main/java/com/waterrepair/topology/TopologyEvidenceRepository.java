package com.waterrepair.topology;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TopologyEvidenceRepository extends JpaRepository<TopologyEvidence, Long> {
    Optional<TopologyEvidence> findByCode(String code);
    List<TopologyEvidence> findAllByOrderByIdAsc();
}
