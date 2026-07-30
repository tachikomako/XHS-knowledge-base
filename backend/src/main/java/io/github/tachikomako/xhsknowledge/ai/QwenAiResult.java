package io.github.tachikomako.xhsknowledge.ai;

import java.util.List;

public record QwenAiResult(
        String summary,
        String categoryId,
        List<String> tagIds,
        List<String> suggestedTags,
        double confidence
) {
}
