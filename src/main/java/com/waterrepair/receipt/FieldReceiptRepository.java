package com.waterrepair.receipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FieldReceiptRepository extends JpaRepository<FieldReceipt, Long> {
    List<FieldReceipt> findByIncidentIdOrderByIdAsc(Long incidentId);
    List<FieldReceipt> findByIncidentIdAndCycleNoOrderByIdAsc(Long incidentId, Integer cycleNo);
}
