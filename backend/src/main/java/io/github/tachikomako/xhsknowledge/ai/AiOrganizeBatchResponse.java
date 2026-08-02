package io.github.tachikomako.xhsknowledge.ai;

import java.util.List;

public record AiOrganizeBatchResponse(
        int eligible,
        int processed,
        int succeeded,
        int failed,
        int blockedByContent,
        int blockedByManualLock,
        int skipped,
        List<String> errors,
        String message
) {
}
