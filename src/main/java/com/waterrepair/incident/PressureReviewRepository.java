package com.waterrepair.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PressureReviewRepository extends JpaRepository<PressureReview, Long> {
    List<PressureReview> findByIncidentIdOrderByIdAsc(Long incidentId);
}
