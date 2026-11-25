package com.waterrepair.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IsolationPlanRepository extends JpaRepository<IsolationPlan, Long> {
    List<IsolationPlan> findByIncidentIdOrderByVersionNoAscIdAsc(Long incidentId);
    Optional<IsolationPlan> findFirstByIncidentIdAndStatusOrderByVersionNoDesc(Long incidentId, String status);
}
