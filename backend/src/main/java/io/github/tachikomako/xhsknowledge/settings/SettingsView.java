package io.github.tachikomako.xhsknowledge.settings;

public record SettingsView(
        boolean aiEnabled,
        boolean aiConfigured,
        String baseUrl,
        String model,
        int pendingAiCount,
        int failedAiCount
) {
}
