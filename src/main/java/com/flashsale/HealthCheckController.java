package com.flashsale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthCheckController {

    @Value("${flashsale.instance-id:local}")
    private String instanceId;

    @GetMapping("/health")
    public Map<String, String> healthCheck() {
        return Map.of("status", "healthy", "instanceId", instanceId);
    }
}
