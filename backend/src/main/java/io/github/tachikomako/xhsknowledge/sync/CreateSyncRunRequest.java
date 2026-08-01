package io.github.tachikomako.xhsknowledge.sync;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateSyncRunRequest(
        @NotEmpty @Size(max = 2) List<@Size(max = 16) String> requestedSources
) {
}
