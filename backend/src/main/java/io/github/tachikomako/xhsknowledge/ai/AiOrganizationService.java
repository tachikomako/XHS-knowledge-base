package io.github.tachikomako.xhsknowledge.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tachikomako.xhsknowledge.common.ApiException;
import io.github.tachikomako.xhsknowledge.item.KnowledgeItemEntity;
import io.github.tachikomako.xhsknowledge.item.KnowledgeItemMapper;
import io.github.tachikomako.xhsknowledge.settings.SettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AiOrganizationService {
    private static final List<DefaultCategory> DEFAULT_CATEGORIES = List.of(
            new DefaultCategory("default-technology", "技术", 10),
            new DefaultCategory("default-software", "软件工具", 20),
            new DefaultCategory("default-english", "英语学习", 30),
            new DefaultCategory("default-life", "生活", 40),
            new DefaultCategory("default-other", "其他", 50)
    );

    private final KnowledgeItemMapper itemMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final QwenClient qwenClient;

    public AiOrganizationService(
            KnowledgeItemMapper itemMapper,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            SettingsService settingsService,
            QwenClient qwenClient
    ) {
        this.itemMapper = itemMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.qwenClient = qwenClient;
    }

    @Async
    public void organizeLater(String itemId) {
        if (!settingsService.aiEnabled() || !qwenClient.configured()) return;
        try {
            markPending(itemId);
            organizeNow(itemId);
        } catch (Exception exception) {
            markFailed(itemId, exception);
        }
    }

    public void organizeManually(String itemId) {
        if (!qwenClient.configured()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_NOT_CONFIGURED", "Qwen API key is not configured");
        }
        try {
            markPending(itemId);
            organizeNow(itemId);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            markFailed(itemId, exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_ORGANIZE_FAILED", "AI organization failed");
        }
    }

    public AiOrganizeBatchResponse organizePending() {
        if (!qwenClient.configured()) {
            return new AiOrganizeBatchResponse(0, 0, 0, pendingItemIds().size(), "Qwen API key is not configured");
        }
        int processed = 0;
        int succeeded = 0;
        int failed = 0;
        List<String> itemIds = pendingItemIds();
        for (String itemId : itemIds) {
            processed++;
            try {
                organizeManually(itemId);
                succeeded++;
            } catch (Exception exception) {
                failed++;
            }
        }
        return new AiOrganizeBatchResponse(processed, succeeded, failed, 0, null);
    }

    @Transactional
    public void organizeNow(String itemId) throws Exception {
        KnowledgeItemEntity item = itemMapper.selectById(itemId);
        if (item == null || Integer.valueOf(1).equals(item.getManualMetadataLocked())) return;
        ensureDefaultCategories();
        QwenAiResult result = qwenClient.organize(prompt(item));
        item = itemMapper.selectById(itemId);
        if (item == null || Integer.valueOf(1).equals(item.getManualMetadataLocked())) return;

        Set<String> categoryIds = Set.copyOf(jdbcTemplate.queryForList("SELECT id FROM categories", String.class));
        Set<String> tagIds = Set.copyOf(jdbcTemplate.queryForList("SELECT id FROM tags", String.class));
        String categoryId = StringUtils.hasText(result.categoryId()) && categoryIds.contains(result.categoryId())
                ? result.categoryId()
                : null;
        List<String> validTagIds = result.tagIds() == null ? List.of() : result.tagIds().stream()
                .filter(tagIds::contains)
                .distinct()
                .limit(20)
                .toList();

        String timestamp = now();
        item.setSummary(limit(result.summary(), 500));
        item.setCategoryId(categoryId);
        item.setAiConfidence(Math.max(0, Math.min(1, result.confidence())));
        item.setAiStatus("SUCCESS");
        item.setAiLastError(null);
        item.setUpdatedAt(timestamp);
        itemMapper.updateById(item);
        replaceTags(itemId, validTagIds);
        saveSuggestions(itemId, result.suggestedTags(), timestamp);
    }

    private void markPending(String itemId) {
        jdbcTemplate.update("""
                UPDATE knowledge_items
                SET ai_status = 'PENDING', ai_last_error = NULL, updated_at = ?
                WHERE id = ? AND manual_metadata_locked = 0
                """, now(), itemId);
    }

    private void markFailed(String itemId, Exception exception) {
        jdbcTemplate.update("""
                UPDATE knowledge_items
                SET ai_status = 'FAILED', ai_last_error = ?, updated_at = ?
                WHERE id = ? AND manual_metadata_locked = 0
                """, limit(exception.getMessage(), 200), now(), itemId);
    }

    private List<String> pendingItemIds() {
        return jdbcTemplate.queryForList("""
                SELECT id
                FROM knowledge_items
                WHERE lifecycle_status = 'ACTIVE'
                  AND content_status = 'COMPLETED'
                  AND manual_metadata_locked = 0
                  AND ai_status IN ('NOT_REQUESTED', 'PENDING', 'FAILED')
                ORDER BY updated_at DESC
                LIMIT 50
                """, String.class);
    }

    private String prompt(KnowledgeItemEntity item) throws JsonProcessingException {
        return """
                请整理下面的小红书收藏，返回 JSON：
                {"summary":"两三句话的简短摘要","categoryId":"existing-category-id","tagIds":["existing-tag-id"],"suggestedTags":["新标签建议"],"confidence":0.86}
                只能选择已有分类和已有标签；没有合适分类或标签时用 null 或空数组。

                已有分类：
                %s

                已有标签：
                %s

                收藏内容：
                标题：%s
                作者：%s
                正文：%s
                """.formatted(
                objectMapper.writeValueAsString(catalog("categories")),
                objectMapper.writeValueAsString(catalog("tags")),
                item.getTitle(),
                item.getAuthor(),
                limit(item.getContent(), 4000)
        );
    }

    private List<?> catalog(String table) {
        return jdbcTemplate.queryForList("SELECT id, name FROM " + table + " ORDER BY lower(name)");
    }

    private void ensureDefaultCategories() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories", Integer.class);
        if (count != null && count > 0) return;
        String timestamp = now();
        for (DefaultCategory category : DEFAULT_CATEGORIES) {
            jdbcTemplate.update("""
                    INSERT OR IGNORE INTO categories(id, name, parent_id, sort_order, created_at, updated_at)
                    VALUES (?, ?, NULL, ?, ?, ?)
                    """, category.id(), category.name(), category.sortOrder(), timestamp, timestamp);
        }
    }

    private void replaceTags(String itemId, List<String> tagIds) {
        jdbcTemplate.update("DELETE FROM knowledge_item_tags WHERE item_id = ?", itemId);
        for (String tagId : tagIds) {
            jdbcTemplate.update("INSERT INTO knowledge_item_tags(item_id, tag_id) VALUES (?, ?)", itemId, tagId);
        }
    }

    private void saveSuggestions(String itemId, List<String> suggestions, String timestamp) {
        jdbcTemplate.update("DELETE FROM item_ai_suggestions WHERE item_id = ? AND suggestion_type = 'TAG'", itemId);
        if (suggestions == null) return;
        for (String suggestion : new LinkedHashSet<>(suggestions)) {
            String value = limit(suggestion, 50);
            if (StringUtils.hasText(value)) {
                jdbcTemplate.update(
                        "INSERT OR IGNORE INTO item_ai_suggestions(item_id, suggestion_type, value, created_at) VALUES (?, 'TAG', ?, ?)",
                        itemId,
                        value.trim(),
                        timestamp
                );
            }
        }
    }

    private String limit(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String now() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    private record DefaultCategory(String id, String name, int sortOrder) {
    }
}
