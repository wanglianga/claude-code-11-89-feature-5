package com.waterrepair.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttributionJudgmentRepository extends JpaRepository<AttributionJudgment, Long> {
    List<AttributionJudgment> findByReportIdOrderByVersionNoAscIdAsc(Long reportId);
}
