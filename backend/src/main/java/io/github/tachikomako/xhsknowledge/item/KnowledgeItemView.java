package io.github.tachikomako.xhsknowledge.item;

import java.util.List;

public record KnowledgeItemView(
        String id,
        String sourceType,
        String sourceItemId,
        String canonicalUrl,
        String originalUrl,
        String title,
        String content,
        String contentStatus,
        String contentLastError,
        List<String> sourceTags,
        List<String> sourceRelations,
        String author,
        String captureLevel,
        String summary,
        String userNote,
        String categoryId,
        List<String> tagIds,
        String aiStatus,
        Double aiConfidence,
        String aiLastError,
        String lifecycleStatus,
        boolean manualMetadataLocked,
        String createdAt,
        String sourceUpdatedAt,
        String userEditedAt,
        String updatedAt
) {
}
