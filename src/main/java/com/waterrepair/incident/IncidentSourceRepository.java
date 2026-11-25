package com.waterrepair.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentSourceRepository extends JpaRepository<IncidentSource, Long> {
    List<IncidentSource> findByIncidentId(Long incidentId);
    List<IncidentSource> findByReportId(Long reportId);
    boolean existsByReportId(Long reportId);
}
