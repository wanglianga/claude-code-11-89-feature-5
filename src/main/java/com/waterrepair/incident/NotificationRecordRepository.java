package com.waterrepair.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long> {
    List<NotificationRecord> findByIncidentIdOrderByIdAsc(Long incidentId);
}
