package io.github.tachikomako.xhsknowledge.settings;

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
        return new SettingsView(aiEnabled(), StringUtils.hasText(apiKey), model);
    }

    public boolean aiEnabled() {
        String value = jdbcTemplate.query(
                "SELECT value FROM app_settings WHERE key = ?",
                result -> result.next() ? result.getString("value") : "false",
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
}
