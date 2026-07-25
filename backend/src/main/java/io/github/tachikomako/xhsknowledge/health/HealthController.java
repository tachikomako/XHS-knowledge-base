package io.github.tachikomako.xhsknowledge.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final String appVersion;
    private final boolean aiConfigured;

    public HealthController(
            @Value("${app.version}") String appVersion,
            @Value("${qwen.api-key:}") String qwenApiKey
    ) {
        this.appVersion = appVersion;
        this.aiConfigured = !qwenApiKey.isBlank();
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", "v1", appVersion, aiConfigured);
    }

    public record HealthResponse(
            String status,
            String apiVersion,
            String appVersion,
            boolean aiConfigured
    ) {
    }
}
