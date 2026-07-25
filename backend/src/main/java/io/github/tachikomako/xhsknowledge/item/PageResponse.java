package io.github.tachikomako.xhsknowledge.item;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        long page,
        long pageSize,
        long total
) {
}
