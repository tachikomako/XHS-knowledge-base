package io.github.tachikomako.xhsknowledge.metadata;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 128) String parentId,
        @Min(-10_000) @Max(10_000) Integer sortOrder
) {
}
