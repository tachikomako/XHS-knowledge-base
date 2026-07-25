package io.github.tachikomako.xhsknowledge.common;

import java.time.OffsetDateTime;

public record ApiError(
        String code,
        String message,
        String requestId,
        OffsetDateTime timestamp
) {
}
