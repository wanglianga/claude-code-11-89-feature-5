package com.waterrepair.dispatch;

import com.waterrepair.common.ApiException;
import com.waterrepair.crew.CrewRepository;
import com.waterrepair.crew.OperationPermitRepository;
import com.waterrepair.crew.ToolItemRepository;
import com.waterrepair.crew.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源占用与互斥。所有预留都遵循“先对资源主行 SELECT ... FOR UPDATE，
 * 再做时间窗重叠查询”的顺序，保证两个并发派单请求对同一队伍/许可恰好一成一败。
 */
@Service
public class OccupancyService {

    public static final String CREW = "CREW";
    public static final String VALVE_PERMIT = "VALVE_PERMIT";
    public static final String VEHICLE = "VEHICLE";
    public static final String TOOL = "TOOL";

    private final ResourceOccupancyRepository occupancyRepo;
    private final AssignmentRepository assignmentRepo;
    private final CrewRepository crewRepo;
    private final OperationPermitRepository permitRepo;
    private final VehicleRepository vehicleRepo;
    private final ToolItemRepository toolRepo;

    public OccupancyService(ResourceOccupancyRepository occupancyRepo,
                            AssignmentRepository assignmentRepo,
                            CrewRepository crewRepo,
                            OperationPermitRepository permitRepo,
                            VehicleRepository vehicleRepo,
                            ToolItemRepository toolRepo) {
        this.occupancyRepo = occupancyRepo;
        this.assignmentRepo = assignmentRepo;
        this.crewRepo = crewRepo;
        this.permitRepo = permitRepo;
        this.vehicleRepo = vehicleRepo;
        this.toolRepo = toolRepo;
    }

    /** 锁定资源主行（串行化同一资源的并发派单），随后校验时间窗互斥并写入 HELD 占用。 */
    @Transactional
    public ResourceOccupancy reserve(Assignment assignment, String resourceType, String resourceCode) {
        lockResourceRow(resourceType, resourceCode);

        List<ResourceOccupancy> overlaps = occupancyRepo.findOverlaps(
                resourceType, resourceCode, assignment.getWindowStart(), assignment.getWindowEnd());
        // 同一派工单自身的占用不算冲突（更正重入时可能出现）
        List<ResourceOccupancy> foreign = overlaps.stream()
                .filter(o -> !o.getAssignmentId().equals(assignment.getId()))
                .toList();
        if (!foreign.isEmpty()) {
            ResourceOccupancy held = foreign.get(0);
            String code = conflictCode(resourceType);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("resourceType", resourceType);
            details.put("resourceCode", resourceCode);
            details.put("heldByOccupancyId", held.getId());
            details.put("heldByAssignmentId", held.getAssignmentId());
            assignmentRepo.findById(held.getAssignmentId())
                    .ifPresent(a -> details.put("heldByAssignmentCode", a.getCode()));
            details.put("heldByIncidentId", held.getIncidentId());
            details.put("heldWindowStart", held.getWindowStart().toString());
            details.put("heldWindowEnd", held.getWindowEnd().toString());
            details.put("requestedWindowStart", assignment.getWindowStart().toString());
            details.put("requestedWindowEnd", assignment.getWindowEnd().toString());
            throw ApiException.conflict(code,
                    resourceType + " " + resourceCode + " 在申请时间窗内已被其他事故占用", details);
        }

        ResourceOccupancy o = new ResourceOccupancy();
        o.setResourceType(resourceType);
        o.setResourceCode(resourceCode);
        o.setAssignmentId(assignment.getId());
        o.setIncidentId(assignment.getIncidentId());
        o.setWindowStart(assignment.getWindowStart());
        o.setWindowEnd(assignment.getWindowEnd());
        o.setStatus("HELD");
        o.setCreatedAt(OffsetDateTime.now());
        return occupancyRepo.save(o);
    }

    private void lockResourceRow(String resourceType, String resourceCode) {
        switch (resourceType) {
            case CREW -> crewRepo.findByCodeForUpdate(resourceCode)
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_CREW", "班组不存在: " + resourceCode));
            case VALVE_PERMIT -> permitRepo.findByCodeForUpdate(resourceCode)
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_PERMIT", "阀门操作许可不存在: " + resourceCode));
            case VEHICLE -> vehicleRepo.findByCodeForUpdate(resourceCode)
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_VEHICLE", "车辆不存在: " + resourceCode));
            case TOOL -> toolRepo.findByCodeForUpdate(resourceCode)
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_TOOL", "工具不存在: " + resourceCode));
            default -> throw ApiException.badRequest("UNKNOWN_RESOURCE_TYPE", "未知资源类型: " + resourceType);
        }
    }

    private String conflictCode(String resourceType) {
        return switch (resourceType) {
            case CREW -> "CREW_ALREADY_BOOKED";
            case VALVE_PERMIT -> "PERMIT_ALREADY_BOOKED";
            case VEHICLE -> "VEHICLE_ALREADY_BOOKED";
            case TOOL -> "TOOL_ALREADY_BOOKED";
            default -> "RESOURCE_CONFLICT";
        };
    }

    @Transactional
    public int releaseByAssignment(Long assignmentId, String reason) {
        List<ResourceOccupancy> rows = occupancyRepo.findByAssignmentIdOrderByIdAsc(assignmentId).stream()
                .filter(o -> "HELD".equals(o.getStatus()))
                .toList();
        OffsetDateTime now = OffsetDateTime.now();
        for (ResourceOccupancy o : rows) {
            o.setStatus("RELEASED");
            o.setReleaseReason(reason);
            o.setReleasedAt(now);
        }
        occupancyRepo.saveAll(rows);
        return rows.size();
    }

    /** 释放指定派工单持有的某类资源中的给定编码（压力复核更正换阀票时使用）。 */
    @Transactional
    public List<String> releaseResources(Long assignmentId, String resourceType,
                                         java.util.Collection<String> resourceCodes, String reason) {
        List<String> released = new java.util.ArrayList<>();
        if (resourceCodes == null || resourceCodes.isEmpty()) {
            return released;
        }
        List<ResourceOccupancy> rows = occupancyRepo.findByAssignmentIdOrderByIdAsc(assignmentId);
        OffsetDateTime now = OffsetDateTime.now();
        for (ResourceOccupancy o : rows) {
            if ("HELD".equals(o.getStatus())
                    && resourceType.equals(o.getResourceType())
                    && resourceCodes.contains(o.getResourceCode())) {
                o.setStatus("RELEASED");
                o.setReleaseReason(reason);
                o.setReleasedAt(now);
                released.add(o.getResourceCode());
            }
        }
        occupancyRepo.saveAll(rows);
        return released;
    }

    @Transactional(readOnly = true)
    public List<ResourceOccupancy> listForAssignment(Long assignmentId) {
        return occupancyRepo.findByAssignmentIdOrderByIdAsc(assignmentId);
    }
}
