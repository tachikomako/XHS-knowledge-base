package io.github.tachikomako.xhsknowledge.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public AiOrganizeTaskView organizePending() {
        return aiOrganizationService.startPendingTask();
    }

    @PostMapping("/organize-tasks")
    public AiOrganizeTaskView organizeItems(@RequestBody(required = false) AiOrganizeTaskRequest request) {
        return aiOrganizationService.startTask(request == null ? null : request.itemIds());
    }

    @GetMapping("/organize-tasks/{id}")
    public AiOrganizeTaskView organizeTask(@PathVariable String id) {
        return aiOrganizationService.task(id);
    }

    @PostMapping("/organize-tasks/{id}/cancel")
    public AiOrganizeTaskView cancelOrganizeTask(@PathVariable String id) {
        return aiOrganizationService.cancelTask(id);
    }
}
