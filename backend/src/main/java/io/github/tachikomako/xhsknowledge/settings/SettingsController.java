package io.github.tachikomako.xhsknowledge.settings;

import io.github.tachikomako.xhsknowledge.ai.QwenClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final QwenClient qwenClient;

    public SettingsController(SettingsService settingsService, QwenClient qwenClient) {
        this.settingsService = settingsService;
        this.qwenClient = qwenClient;
    }

    @GetMapping
    public SettingsView get() {
        return settingsService.get();
    }

    @PatchMapping("/ai")
    public SettingsView updateAi(@RequestBody AiSettingsRequest request) {
        return settingsService.updateAi(request.aiEnabled());
    }

    @PostMapping("/ai/test")
    public AiConnectionTestResponse testAi() {
        return settingsService.testAiConnection(qwenClient);
    }
}
