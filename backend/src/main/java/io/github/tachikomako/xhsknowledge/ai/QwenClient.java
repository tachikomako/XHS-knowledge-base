package io.github.tachikomako.xhsknowledge.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tachikomako.xhsknowledge.settings.SettingsService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class QwenClient {

    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final SettingsService settingsService;

    public QwenClient(
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            SettingsService settingsService
    ) {
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
        this.settingsService = settingsService;
    }

    public boolean configured() {
        return StringUtils.hasText(settingsService.aiRuntimeSettings().apiKey());
    }

    public String model() {
        return settingsService.aiRuntimeSettings().model();
    }

    public void testConnection() {
        SettingsService.AiRuntimeSettings settings = settingsService.aiRuntimeSettings();
        restClient(settings).post()
                .uri("/chat/completions")
                .headers(headers -> headers.setBearerAuth(settings.apiKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", settings.model(),
                        "temperature", 0,
                        "max_tokens", 8,
                        "messages", List.of(
                                Map.of("role", "system", "content", "Return a short plain text health check."),
                                Map.of("role", "user", "content", "ping")
                        )
                ))
                .retrieve()
                .toBodilessEntity();
    }

    public QwenAiResult organize(String prompt) throws Exception {
        SettingsService.AiRuntimeSettings settings = settingsService.aiRuntimeSettings();
        JsonNode response = restClient(settings).post()
                .uri("/chat/completions")
                .headers(headers -> headers.setBearerAuth(settings.apiKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", settings.model(),
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

    public QwenCategorySuggestions suggestCategories(String prompt) throws Exception {
        SettingsService.AiRuntimeSettings settings = settingsService.aiRuntimeSettings();
        JsonNode response = restClient(settings).post()
                .uri("/chat/completions")
                .headers(headers -> headers.setBearerAuth(settings.apiKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", settings.model(),
                        "temperature", 0.2,
                        "response_format", Map.of("type", "json_object"),
                        "messages", List.of(
                                Map.of("role", "system", "content", "Return only valid JSON."),
                                Map.of("role", "user", "content", prompt)
                        )
                ))
                .retrieve()
                .body(JsonNode.class);
        String content = response.path("choices").path(0).path("message").path("content").asText();
        return objectMapper.readValue(content, QwenCategorySuggestions.class);
    }

    private RestClient restClient(SettingsService.AiRuntimeSettings settings) {
        return restClientBuilder.clone().baseUrl(settings.baseUrl()).build();
    }
}
