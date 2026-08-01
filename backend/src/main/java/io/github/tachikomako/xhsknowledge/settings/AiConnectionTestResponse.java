package io.github.tachikomako.xhsknowledge.settings;

public record AiConnectionTestResponse(
        boolean success,
        boolean configured,
        String model,
        String message
) {
}
