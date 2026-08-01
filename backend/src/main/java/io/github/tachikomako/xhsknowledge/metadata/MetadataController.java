package io.github.tachikomako.xhsknowledge.metadata;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class MetadataController {

    private final MetadataService metadataService;

    public MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping("/categories")
    public List<CategoryView> listCategories() {
        return metadataService.listCategories();
    }

    @GetMapping("/categories/source-tags")
    public List<SourceTagView> listSourceTags() {
        return metadataService.listSourceTags();
    }

    @PostMapping("/categories/suggestions")
    public CategorySuggestionResponse suggestCategories() {
        return metadataService.suggestCategories();
    }

    @PostMapping("/categories/suggestions/confirm")
    public List<CategoryView> confirmCategorySuggestions(@Valid @RequestBody ConfirmCategorySuggestionsRequest request) {
        return metadataService.confirmCategorySuggestions(request.categories());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView createCategory(@Valid @RequestBody CategoryRequest request) {
        return metadataService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    public CategoryView updateCategory(@PathVariable String id, @Valid @RequestBody CategoryRequest request) {
        return metadataService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable String id) {
        metadataService.deleteCategory(id);
    }

    @GetMapping("/tags")
    public List<TagView> listTags() {
        return metadataService.listTags();
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public TagView createTag(@Valid @RequestBody TagRequest request) {
        return metadataService.createTag(request);
    }

    @PutMapping("/tags/{id}")
    public TagView updateTag(@PathVariable String id, @Valid @RequestBody TagRequest request) {
        return metadataService.updateTag(id, request);
    }

    @PostMapping("/tags/{sourceTagId}/merge")
    public TagView mergeTag(@PathVariable String sourceTagId, @RequestBody MergeTagRequest request) {
        return metadataService.mergeTag(sourceTagId, request.targetTagId());
    }

    @DeleteMapping("/tags/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTag(@PathVariable String id) {
        metadataService.deleteTag(id);
    }

    public record MergeTagRequest(String targetTagId) { }
}
