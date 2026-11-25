package com.waterrepair.config;

import com.waterrepair.auth.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "00-开发/回放支持")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SeedService seedService;

    public AdminController(SeedService seedService) {
        this.seedService = seedService;
    }

    @Operation(summary = "清空业务数据并重新播种样例（回放脚本开始时调用，保证可重复）")
    @Roles({"DISPATCHER"})
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        Map<String, Object> counts = seedService.reset();
        return Map.of("reset", true, "seeded", counts);
    }

    @Operation(summary = "当前播种数据计数")
    @GetMapping("/seed-counts")
    public Map<String, Object> counts() {
        return seedService.counts();
    }
}
