package com.waterrepair.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorrectionRecordRepository extends JpaRepository<CorrectionRecord, Long> {
    List<CorrectionRecord> findByIncidentIdOrderByRevisionAscIdAsc(Long incidentId);
}
