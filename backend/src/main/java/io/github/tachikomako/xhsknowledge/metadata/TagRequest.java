package io.github.tachikomako.xhsknowledge.metadata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagRequest(@NotBlank @Size(max = 50) String name) {
}
