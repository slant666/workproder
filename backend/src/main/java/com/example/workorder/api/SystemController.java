package com.example.workorder.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemController {

    @GetMapping("/api/system/status")
    public Map<String, Object> status() {
        return Map.of(
                "status", "ok",
                "service", "work-order-system",
                "timestamp", Instant.now().toString());
    }
}
