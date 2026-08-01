package io.github.tachikomako.xhsknowledge.health;

import io.github.tachikomako.xhsknowledge.settings.SettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final String appVersion;
    private final SettingsService settingsService;

    public HealthController(
            @Value("${app.version}") String appVersion,
            SettingsService settingsService
    ) {
        this.appVersion = appVersion;
        this.settingsService = settingsService;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                "v1",
                appVersion,
                StringUtils.hasText(settingsService.aiRuntimeSettings().apiKey())
        );
    }

    public record HealthResponse(
            String status,
            String apiVersion,
            String appVersion,
            boolean aiConfigured
    ) {
    }
}
