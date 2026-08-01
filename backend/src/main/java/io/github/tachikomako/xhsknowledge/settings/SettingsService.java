package io.github.tachikomako.xhsknowledge.settings;

import io.github.tachikomako.xhsknowledge.ai.QwenClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class SettingsService {

    private static final String AI_ENABLED_KEY = "ai.enabled";

    private final JdbcTemplate jdbcTemplate;
    private final String apiKey;
    private final String model;

    public SettingsService(
            JdbcTemplate jdbcTemplate,
            @Value("${qwen.api-key:}") String apiKey,
            @Value("${qwen.model:qwen-plus}") String model
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.apiKey = apiKey;
        this.model = StringUtils.hasText(model) ? model : "qwen-plus";
    }

    public SettingsView get() {
        return new SettingsView(
                aiEnabled(),
                StringUtils.hasText(apiKey),
                model,
                aiCount("PENDING", "PROCESSING"),
                aiCount("FAILED")
        );
    }

    public AiConnectionTestResponse testAiConnection(QwenClient qwenClient) {
        if (!qwenClient.configured()) {
            return new AiConnectionTestResponse(false, false, qwenClient.model(), "Qwen API key is not configured");
        }
        try {
            qwenClient.testConnection();
            return new AiConnectionTestResponse(true, true, qwenClient.model(), "Qwen connection succeeded");
        } catch (Exception exception) {
            return new AiConnectionTestResponse(false, true, qwenClient.model(), "Qwen connection failed");
        }
    }

    public boolean aiEnabled() {
        String value = jdbcTemplate.query(
                "SELECT value FROM app_settings WHERE key = ?",
                result -> result.next() ? result.getString("value") : Boolean.toString(StringUtils.hasText(apiKey)),
                AI_ENABLED_KEY
        );
        return Boolean.parseBoolean(value);
    }

    @Transactional
    public SettingsView updateAi(boolean enabled) {
        jdbcTemplate.update("""
                INSERT INTO app_settings(key, value, updated_at) VALUES (?, ?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at
                """, AI_ENABLED_KEY, Boolean.toString(enabled), now());
        return get();
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
