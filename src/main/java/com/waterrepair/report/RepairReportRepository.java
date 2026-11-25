package com.waterrepair.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepairReportRepository extends JpaRepository<RepairReport, Long> {
}
