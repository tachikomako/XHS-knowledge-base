package io.github.tachikomako.xhsknowledge.settings;

public record SettingsView(
        boolean aiEnabled,
        boolean aiConfigured,
        String model,
        int pendingAiCount,
        int failedAiCount
) {
}
