package com.waterrepair.crew;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OperationPermitRepository extends JpaRepository<OperationPermit, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from OperationPermit p where p.code = :code")
    Optional<OperationPermit> findByCodeForUpdate(@Param("code") String code);
}
