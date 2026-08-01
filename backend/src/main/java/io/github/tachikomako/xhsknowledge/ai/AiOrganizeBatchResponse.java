package io.github.tachikomako.xhsknowledge.ai;

public record AiOrganizeBatchResponse(
        int processed,
        int succeeded,
        int failed,
        int skipped,
        String message
) {
}
