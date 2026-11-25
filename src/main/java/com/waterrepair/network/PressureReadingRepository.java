package com.waterrepair.network;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PressureReadingRepository extends JpaRepository<PressureReading, Long> {
    Optional<PressureReading> findByReadingCode(String readingCode);
}
