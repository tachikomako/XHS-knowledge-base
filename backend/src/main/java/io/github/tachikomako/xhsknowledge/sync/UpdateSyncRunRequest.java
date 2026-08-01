package io.github.tachikomako.xhsknowledge.sync;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateSyncRunRequest(
        @Size(max = 32) String status,
        @Min(0) @Max(100000) Integer discoveredCount,
        @Min(0) @Max(100000) Integer processedCount,
        @Min(0) @Max(100000) Integer createdCount,
        @Min(0) @Max(100000) Integer updatedCount,
        @Min(0) @Max(100000) Integer unchangedCount,
        @Min(0) @Max(100000) Integer contentCompletedCount,
        @Min(0) @Max(100000) Integer contentFailedCount,
        @Min(0) @Max(100000) Integer aiCompletedCount,
        @Min(0) @Max(100000) Integer aiFailedCount,
        @Size(max = 1000) String errorSummary
) {
}
