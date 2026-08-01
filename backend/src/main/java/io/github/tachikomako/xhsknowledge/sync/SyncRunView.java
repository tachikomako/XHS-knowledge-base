package io.github.tachikomako.xhsknowledge.sync;

public record SyncRunView(
        String id,
        String requestedSources,
        String status,
        int discoveredCount,
        int processedCount,
        int createdCount,
        int updatedCount,
        int unchangedCount,
        int contentCompletedCount,
        int contentFailedCount,
        int aiCompletedCount,
        int aiFailedCount,
        String startedAt,
        String finishedAt,
        String errorSummary
) {
}
