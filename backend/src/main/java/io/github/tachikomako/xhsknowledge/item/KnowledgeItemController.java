package io.github.tachikomako.xhsknowledge.item;

import io.github.tachikomako.xhsknowledge.ai.AiOrganizationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items")
public class KnowledgeItemController {

    private final KnowledgeItemService itemService;
    private final AiOrganizationService aiOrganizationService;

    public KnowledgeItemController(KnowledgeItemService itemService, AiOrganizationService aiOrganizationService) {
        this.itemService = itemService;
        this.aiOrganizationService = aiOrganizationService;
    }

    @GetMapping
    public PageResponse<KnowledgeItemView> search(
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String tagId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String sourceScope,
            @RequestParam(required = false) String captureLevel,
            @RequestParam(required = false) String aiStatus,
            @RequestParam(required = false) String contentStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "updatedAt,desc") String sort
    ) {
        return itemService.search(
                query,
                categoryId,
                tagId,
                sourceType,
                sourceScope,
                captureLevel,
                aiStatus,
                contentStatus,
                page,
                pageSize,
                sort
        );
    }

    @GetMapping("/{id}")
    public KnowledgeItemView get(@PathVariable String id) {
        return itemService.get(id);
    }

    @PatchMapping("/{id}")
    public KnowledgeItemView update(
            @PathVariable String id,
            @RequestBody UpdateKnowledgeItemRequest request
    ) {
        return itemService.update(id, request);
    }

    @PostMapping("/clear")
    public ClearItemsResponse clear(@RequestBody ClearItemsRequest request) {
        return new ClearItemsResponse(itemService.clear(request == null ? null : request.confirmation()));
    }

    @PostMapping("/{id}/organize")
    public KnowledgeItemView organize(@PathVariable String id) {
        aiOrganizationService.organizeManually(id);
        return itemService.get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        itemService.delete(id);
    }

    public record ClearItemsRequest(String confirmation) {}

    public record ClearItemsResponse(int deletedItems) {}
}
