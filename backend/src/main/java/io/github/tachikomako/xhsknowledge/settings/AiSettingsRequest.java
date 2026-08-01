package io.github.tachikomako.xhsknowledge.settings;

import jakarta.validation.constraints.NotBlank;

public record AiSettingsRequest(
        boolean aiEnabled,
        String apiKey,
        @NotBlank String baseUrl,
        @NotBlank String model
) {
}
