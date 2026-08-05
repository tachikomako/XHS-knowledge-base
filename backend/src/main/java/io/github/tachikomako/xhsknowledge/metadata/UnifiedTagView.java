package io.github.tachikomako.xhsknowledge.metadata;

import java.util.List;

public record UnifiedTagView(
        String name,
        long usageCount,
        List<String> origins
) {
}
