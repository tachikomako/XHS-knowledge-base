package io.github.tachikomako.xhsknowledge.metadata;

import java.util.List;

public record CategorySuggestionResponse(
        List<CategorySuggestion> suggestions,
        List<SourceTagView> sourceTags
) {
}
