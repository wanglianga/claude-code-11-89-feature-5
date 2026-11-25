package com.waterrepair.dispatch;

import com.waterrepair.crew.Crew;
import com.waterrepair.crew.CrewRepository;
import com.waterrepair.crew.ToolItem;
import com.waterrepair.crew.ToolItemRepository;
import com.waterrepair.crew.Vehicle;
import com.waterrepair.crew.VehicleRepository;
import com.waterrepair.network.ValveSpec;
import com.waterrepair.network.ValveSpecRepository;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 班组接单资格核验：在岗活跃、当班窗口、对应许可、可用车辆、齐备且可用的抢修工具。
 */
@Service
public class EligibilityService {

    public static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Shanghai");

    private final CrewRepository crewRepo;
    private final VehicleRepository vehicleRepo;
    private final ToolItemRepository toolRepo;
    private final ValveSpecRepository valveRepo;

    public EligibilityService(CrewRepository crewRepo, VehicleRepository vehicleRepo,
                              ToolItemRepository toolRepo, ValveSpecRepository valveRepo) {
        this.crewRepo = crewRepo;
        this.vehicleRepo = vehicleRepo;
        this.toolRepo = toolRepo;
        this.valveRepo = valveRepo;
    }

    public record CheckItem(String code, boolean passed, String detail) {
    }

    public record EligibilityResult(boolean eligible,
                                    String crewCode,
                                    String kind,
                                    List<CheckItem> checks,
                                    List<String> requiredQualifications,
                                    String vehicleCode,
                                    List<String> toolCodes,
                                    List<String> permitCodes) {
    }

    public EligibilityResult evaluate(String crewCode, String kind,
                                     OffsetDateTime start, OffsetDateTime end,
                                     List<String> planValveCodes) {
        List<CheckItem> checks = new ArrayList<>();
        List<String> requiredQuals = new ArrayList<>();
        List<String> permitCodes = new ArrayList<>();

        Crew crew = crewRepo.findById(crewCode).orElse(null);
        if (crew == null) {
            checks.add(new CheckItem("CREW_EXISTS", false, "班组不存在: " + crewCode));
            return new EligibilityResult(false, crewCode, kind, checks, requiredQuals,
                    null, List.of(), permitCodes);
        }
        checks.add(new CheckItem("CREW_EXISTS", true, crew.getName()));
        checks.add(new CheckItem("CREW_ACTIVE", crew.isActive(),
                crew.isActive() ? "班组在岗名册内" : "班组已停用"));

        // 当班窗口（按 Asia/Shanghai 的本地时刻比较，样例均为凌晨同班窗口）
        LocalTime tStart = start.atZoneSameInstant(LOCAL_ZONE).toLocalTime();
        LocalTime tEnd = end.atZoneSameInstant(LOCAL_ZONE).toLocalTime();
        boolean shiftOk = !tStart.isBefore(crew.getShiftStart()) && !tEnd.isAfter(crew.getShiftEnd());
        checks.add(new CheckItem("WINDOW_WITHIN_SHIFT", shiftOk,
                "申请窗口 " + tStart + "-" + tEnd + "，当班时间 "
                        + crew.getShiftStart() + "-" + crew.getShiftEnd()));

        if ("SCOUT".equals(kind)) {
            requiredQuals.add("Q_INSPECT");
        } else {
            requiredQuals.add("Q_REPAIR");
            if (planValveCodes != null) {
                for (String valveCode : planValveCodes.stream().sorted().toList()) {
                    ValveSpec valve = valveRepo.findById(valveCode).orElse(null);
                    if (valve != null) {
                        requiredQuals.add(valve.getRequiredQualification());
                    }
                }
            }
        }
        List<String> missingQuals = requiredQuals.stream().distinct()
                .filter(q -> !crew.getQualificationCodes().contains(q)).sorted().toList();
        checks.add(new CheckItem("QUALIFICATION", missingQuals.isEmpty(),
                missingQuals.isEmpty() ? "持有全部所需许可 " + requiredQuals.stream().distinct().sorted().toList()
                        : "缺少许可 " + missingQuals));

        // 车辆
        String vehicleCode = crew.getPrimaryVehicleCode();
        String usableVehicleCode = null;
        if (vehicleCode == null || vehicleCode.isBlank()) {
            checks.add(new CheckItem("VEHICLE_AVAILABLE", false, "班组未绑定值班车辆"));
        } else {
            Vehicle v = vehicleRepo.findById(vehicleCode).orElse(null);
            boolean ok = v != null && v.isAvailable();
            checks.add(new CheckItem("VEHICLE_AVAILABLE", ok,
                    ok ? "车辆可用: " + vehicleCode + " " + v.getPlateNo()
                            : "车辆不可用: " + vehicleCode
                                    + (v == null ? "（台账缺失）" : "，原因=" + v.getUnavailableReason())));
            if (ok) {
                usableVehicleCode = vehicleCode;
            }
        }

        // 工具
        List<String> unavailableTools = new ArrayList<>();
        List<String> usableTools = new ArrayList<>();
        for (String toolCode : crew.getStandardToolCodes()) {
            ToolItem t = toolRepo.findById(toolCode).orElse(null);
            if (t == null || !t.isAvailable()) {
                unavailableTools.add(toolCode + (t == null ? "(台账缺失)" : "(" + t.getUnavailableReason() + ")"));
            } else {
                usableTools.add(toolCode);
            }
        }
        checks.add(new CheckItem("TOOLS_AVAILABLE", unavailableTools.isEmpty(),
                unavailableTools.isEmpty() ? "标准抢修工具齐备: " + crew.getStandardToolCodes()
                        : "工具不可用: " + unavailableTools));

        if (planValveCodes != null) {
            for (String valveCode : planValveCodes.stream().sorted().toList()) {
                valveRepo.findById(valveCode)
                        .ifPresent(v -> permitCodes.add(v.getExclusivePermitCode()));
            }
        }

        boolean eligible = checks.stream().allMatch(CheckItem::passed);
        return new EligibilityResult(eligible, crewCode, kind, checks,
                requiredQuals.stream().distinct().sorted().toList(),
                usableVehicleCode, List.copyOf(usableTools), List.copyOf(permitCodes));
    }
}
