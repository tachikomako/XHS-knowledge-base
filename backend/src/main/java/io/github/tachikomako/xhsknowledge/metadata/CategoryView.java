package io.github.tachikomako.xhsknowledge.metadata;

public record CategoryView(
        String id,
        String name,
        String parentId,
        int sortOrder,
        long itemCount
) {
}
