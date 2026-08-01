package io.github.tachikomako.xhsknowledge.importx;

import java.util.List;

public record ImportResponse(
        String batchId,
        boolean replayed,
        int received,
        int created,
        int updated,
        int skipped,
        int failed,
        List<ItemResult> results
) {
    public record ItemResult(
            int index,
            String itemId,
            String sourceItemId,
            String status,
            String contentStatus,
            String error
    ) {
    }
}
