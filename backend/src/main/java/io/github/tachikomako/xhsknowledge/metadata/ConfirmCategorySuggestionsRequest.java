package io.github.tachikomako.xhsknowledge.metadata;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ConfirmCategorySuggestionsRequest(
        @NotEmpty @Size(max = 12) List<@Valid CategorySuggestion> categories
) {
}
