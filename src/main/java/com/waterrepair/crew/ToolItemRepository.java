package com.waterrepair.crew;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ToolItemRepository extends JpaRepository<ToolItem, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ToolItem t where t.code = :code")
    Optional<ToolItem> findByCodeForUpdate(@Param("code") String code);
}
