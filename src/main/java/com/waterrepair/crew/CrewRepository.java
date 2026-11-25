package com.waterrepair.crew;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CrewRepository extends JpaRepository<Crew, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Crew c where c.code = :code")
    Optional<Crew> findByCodeForUpdate(@Param("code") String code);
}
