package io.github.tachikomako.xhsknowledge.ai;

import io.github.tachikomako.xhsknowledge.metadata.CategorySuggestion;

import java.util.List;

public record QwenCategorySuggestions(
        List<CategorySuggestion> categories
) {
}
