package io.github.tachikomako.xhsknowledge.ai;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiOrganizationService aiOrganizationService;

    public AiController(AiOrganizationService aiOrganizationService) {
        this.aiOrganizationService = aiOrganizationService;
    }

    @PostMapping("/organize-pending")
    public AiOrganizeBatchResponse organizePending() {
        return aiOrganizationService.organizePending();
    }
}
