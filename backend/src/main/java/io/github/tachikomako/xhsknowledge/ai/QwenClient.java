package io.github.tachikomako.xhsknowledge.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class QwenClient {

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public QwenClient(
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            @Value("${qwen.api-key:}") String apiKey,
            @Value("${qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
            @Value("${qwen.model:qwen-plus}") String model
    ) {
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = StringUtils.hasText(model) ? model : "qwen-plus";
    }

    public boolean configured() {
        return StringUtils.hasText(apiKey);
    }

    public QwenAiResult organize(String prompt) throws Exception {
        JsonNode response = restClient.post()
                .uri("/chat/completions")
                .headers(headers -> headers.setBearerAuth(apiKey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", model,
                        "temperature", 0.2,
                        "response_format", Map.of("type", "json_object"),
                        "messages", List.of(
                                Map.of("role", "system", "content", "你是个人知识库整理助手，只输出合法 JSON。"),
                                Map.of("role", "user", "content", prompt)
                        )
                ))
                .retrieve()
                .body(JsonNode.class);
        String content = response.path("choices").path(0).path("message").path("content").asText();
        return objectMapper.readValue(content, QwenAiResult.class);
    }
}
