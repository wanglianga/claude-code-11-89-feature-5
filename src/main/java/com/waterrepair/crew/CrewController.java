package com.waterrepair.crew;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/crews")
public class CrewController {

    private final CrewRepository crewRepo;
    private final VehicleRepository vehicleRepo;
    private final ToolItemRepository toolRepo;
    private final OperationPermitRepository permitRepo;

    public CrewController(CrewRepository crewRepo, VehicleRepository vehicleRepo,
                          ToolItemRepository toolRepo, OperationPermitRepository permitRepo) {
        this.crewRepo = crewRepo;
        this.vehicleRepo = vehicleRepo;
        this.toolRepo = toolRepo;
        this.permitRepo = permitRepo;
    }

    @GetMapping
    public List<Crew> crews() {
        return crewRepo.findAll();
    }

    @GetMapping("/vehicles")
    public List<Vehicle> vehicles() {
        return vehicleRepo.findAll();
    }

    @GetMapping("/tools")
    public List<ToolItem> tools() {
        return toolRepo.findAll();
    }

    @GetMapping("/operation-permits")
    public List<OperationPermit> permits() {
        return permitRepo.findAll();
    }
}
