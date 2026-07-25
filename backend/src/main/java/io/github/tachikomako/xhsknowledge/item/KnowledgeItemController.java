package io.github.tachikomako.xhsknowledge.item;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items")
public class KnowledgeItemController {

    private final KnowledgeItemService itemService;

    public KnowledgeItemController(KnowledgeItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public PageResponse<KnowledgeItemView> search(
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String captureLevel,
            @RequestParam(required = false) String lifecycleStatus,
            @RequestParam(required = false) String aiStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "updatedAt,desc") String sort
    ) {
        return itemService.search(
                query,
                categoryId,
                sourceType,
                captureLevel,
                lifecycleStatus,
                aiStatus,
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

    @PostMapping("/{id}/archive")
    public KnowledgeItemView archive(@PathVariable String id) {
        return itemService.changeLifecycle(id, "ARCHIVED");
    }

    @PostMapping("/{id}/trash")
    public KnowledgeItemView trash(@PathVariable String id) {
        return itemService.changeLifecycle(id, "TRASHED");
    }

    @PostMapping("/{id}/restore")
    public KnowledgeItemView restore(@PathVariable String id) {
        return itemService.changeLifecycle(id, "ACTIVE");
    }
}
