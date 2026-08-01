package io.github.tachikomako.xhsknowledge.ai;

public record AiOrganizeBatchResponse(
        int eligible,
        int processed,
        int succeeded,
        int failed,
        int blockedByContent,
        int blockedByManualLock,
        int skipped,
        String message
) {
}
