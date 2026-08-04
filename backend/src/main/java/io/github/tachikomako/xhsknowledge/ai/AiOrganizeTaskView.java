package io.github.tachikomako.xhsknowledge.ai;

import java.util.List;

public record AiOrganizeTaskView(
        String id,
        String status,
        String scope,
        int requestedCount,
        int total,
        int processed,
        int succeeded,
        int failed,
        List<String> errors,
        String message
) {
}
