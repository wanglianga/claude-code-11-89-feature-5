package com.waterrepair.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "water-repair-service");
        try {
            Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
            body.put("db", one != null && one == 1 ? "UP" : "UNKNOWN");
        } catch (Exception ex) {
            body.put("db", "DOWN: " + ex.getMessage());
        }
        return body;
    }
}
