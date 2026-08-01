package io.github.tachikomako.xhsknowledge.settings;

import io.github.tachikomako.xhsknowledge.ai.AiEligibilityService;
import io.github.tachikomako.xhsknowledge.ai.QwenClient;
import io.github.tachikomako.xhsknowledge.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Properties;

@Service
public class SettingsService {

    private static final String AI_ENABLED_KEY = "ai.enabled";
    private static final String AI_CREDENTIALS_CLEARED_KEY = "qwen.credentials-cleared";
    private static final String BASE_URL_KEY = "qwen.base-url";
    private static final String MODEL_KEY = "qwen.model";
    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String DEFAULT_MODEL = "qwen-plus";

    private final JdbcTemplate jdbcTemplate;
    private final String envApiKey;
    private final String envBaseUrl;
    private final String envModel;
    private final Path secretsFile;
    private final AiEligibilityService aiEligibilityService;

    public SettingsService(
            JdbcTemplate jdbcTemplate,
            AiEligibilityService aiEligibilityService,
            @Value("${qwen.api-key:}") String apiKey,
            @Value("${qwen.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
            @Value("${qwen.model:" + DEFAULT_MODEL + "}") String model,
            @Value("${xhs.secrets-dir:./data/secrets}") String secretsDir
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiEligibilityService = aiEligibilityService;
        this.envApiKey = apiKey;
        this.envBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : DEFAULT_BASE_URL;
        this.envModel = StringUtils.hasText(model) ? model : DEFAULT_MODEL;
        this.secretsFile = Path.of(secretsDir).resolve("ai.properties");
    }

    public SettingsView get() {
        AiRuntimeSettings ai = aiRuntimeSettings();
        return new SettingsView(
                aiEnabled(),
                StringUtils.hasText(ai.apiKey()),
                ai.baseUrl(),
                ai.model(),
                aiEligibilityService.eligibleCount(),
                aiCount("FAILED")
        );
    }

    public AiConnectionTestResponse testAiConnection(QwenClient qwenClient) {
        if (!qwenClient.configured()) {
            return new AiConnectionTestResponse(false, false, qwenClient.model(), "Qwen API key is not configured");
        }
        try {
            qwenClient.testConnection();
            return new AiConnectionTestResponse(true, true, qwenClient.model(), "连接成功");
        } catch (Exception exception) {
            return new AiConnectionTestResponse(false, true, qwenClient.model(), "连接失败");
        }
    }

    public boolean aiEnabled() {
        String value = settingValue(AI_ENABLED_KEY);
        return StringUtils.hasText(value)
                ? Boolean.parseBoolean(value)
                : StringUtils.hasText(aiRuntimeSettings().apiKey());
    }

    @Transactional
    public SettingsView updateAi(AiSettingsRequest request) {
        String baseUrl = request.baseUrl().trim();
        String model = request.model().trim();
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AI_SETTINGS", "Base URL and model are required");
        }
        saveSetting(AI_ENABLED_KEY, Boolean.toString(request.aiEnabled()));
        saveSetting(BASE_URL_KEY, baseUrl);
        saveSetting(MODEL_KEY, model);
        if (StringUtils.hasText(request.apiKey())) {
            saveApiKey(request.apiKey().trim());
            saveSetting(AI_CREDENTIALS_CLEARED_KEY, "false");
        }
        return get();
    }

    @Transactional
    public SettingsView clearAiCredentials() {
        try {
            Files.deleteIfExists(secretsFile);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_SECRET_WRITE_FAILED", "Failed to clear AI credentials");
        }
        saveSetting(AI_CREDENTIALS_CLEARED_KEY, "true");
        return get();
    }

    public AiRuntimeSettings aiRuntimeSettings() {
        String apiKey = savedApiKey();
        if (!StringUtils.hasText(apiKey) && !Boolean.parseBoolean(setting(AI_CREDENTIALS_CLEARED_KEY, "false"))) {
            apiKey = envApiKey;
        }
        return new AiRuntimeSettings(
                apiKey,
                setting(BASE_URL_KEY, envBaseUrl),
                setting(MODEL_KEY, envModel)
        );
    }

    private String setting(String key, String fallback) {
        String value = settingValue(key);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String settingValue(String key) {
        return jdbcTemplate.query(
                "SELECT value FROM app_settings WHERE key = ?",
                result -> result.next() ? result.getString("value") : "",
                key
        );
    }

    private void saveSetting(String key, String value) {
        jdbcTemplate.update("""
                INSERT INTO app_settings(key, value, updated_at) VALUES (?, ?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at
                """, key, value, now());
    }

    private String savedApiKey() {
        if (!Files.exists(secretsFile)) return "";
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(secretsFile)) {
            properties.load(input);
            return properties.getProperty("apiKey", "");
        } catch (IOException exception) {
            return "";
        }
    }

    private void saveApiKey(String apiKey) {
        Properties properties = new Properties();
        properties.setProperty("apiKey", apiKey);
        try {
            Files.createDirectories(secretsFile.getParent());
            try (OutputStream output = Files.newOutputStream(secretsFile)) {
                properties.store(output, "Local AI credentials");
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_SECRET_WRITE_FAILED", "Failed to save AI credentials");
        }
    }

    public record AiRuntimeSettings(String apiKey, String baseUrl, String model) {
    }

    private String now() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    private int aiCount(String... statuses) {
        String placeholders = String.join(",", java.util.Collections.nCopies(statuses.length, "?"));
        Object[] args = statuses;
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM knowledge_items
                WHERE lifecycle_status = 'ACTIVE'
                  AND content_status = 'COMPLETED'
                  AND manual_metadata_locked = 0
                  AND ai_status IN (%s)
                """.formatted(placeholders), Integer.class, args);
        return count == null ? 0 : count;
    }
}
