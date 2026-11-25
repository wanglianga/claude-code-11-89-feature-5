package com.waterrepair.dispatch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface ResourceOccupancyRepository extends JpaRepository<ResourceOccupancy, Long> {

    List<ResourceOccupancy> findByAssignmentIdOrderByIdAsc(Long assignmentId);

    List<ResourceOccupancy> findByIncidentIdOrderByIdAsc(Long incidentId);

    /**
     * 半开区间重叠：existing.start &lt; newEnd AND existing.end &gt; newStart；
     * 端点相接（一个窗口结束时刻等于另一个开始时刻）不算冲突。
     */
    @Query("""
            select o from ResourceOccupancy o
            where o.resourceType = :type
              and o.resourceCode = :code
              and o.status = 'HELD'
              and o.windowStart < :end
              and o.windowEnd > :start
            order by o.id
            """)
    List<ResourceOccupancy> findOverlaps(@Param("type") String type,
                                          @Param("code") String code,
                                          @Param("start") OffsetDateTime start,
                                          @Param("end") OffsetDateTime end);
}
