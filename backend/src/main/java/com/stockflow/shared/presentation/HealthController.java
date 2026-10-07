package com.stockflow.shared.presentation;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
    @GetMapping("/api/v1/health")
    Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
