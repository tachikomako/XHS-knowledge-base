package io.github.tachikomako.xhsknowledge.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tachikomako.xhsknowledge.item.KnowledgeItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiOrganizationServiceTest {

    @Autowired
    private AiOrganizationService aiOrganizationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiEligibilityService aiEligibilityService;

    @MockitoBean
    private QwenClient qwenClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM item_ai_suggestions");
        jdbcTemplate.update("DELETE FROM knowledge_item_tags");
        jdbcTemplate.update("DELETE FROM knowledge_item_source_tags");
        jdbcTemplate.update("DELETE FROM item_source_relations");
        jdbcTemplate.update("DELETE FROM import_batches");
        jdbcTemplate.update("DELETE FROM knowledge_items");
        jdbcTemplate.update("DELETE FROM tags");
        jdbcTemplate.update("DELETE FROM categories");
        jdbcTemplate.update("DELETE FROM app_settings");
        jdbcTemplate.update("""
                INSERT INTO app_settings(key, value, updated_at) VALUES ('ai.enabled', 'false', '2026-08-01T00:00:00Z')
                """);
    }

    @Test
    void writesOnlyValidatedAiMetadataAndSuggestions() throws Exception {
        String categoryId = insertCategory("AI");
        String tagId = insertTag("Agent");
        String itemId = importItem();
        when(qwenClient.organize(anyString())).thenReturn(new QwenAiResult(
                "这是一个适合收藏的 AI Agent 教程。",
                categoryId,
                List.of(tagId, "missing-tag"),
                List.of("提示词", "Agent"),
                1.7
        ));

        aiOrganizationService.organizeNow(itemId);

        var item = jdbcTemplate.queryForMap(
                "SELECT summary, category_id, ai_status, ai_confidence FROM knowledge_items WHERE id = ?",
                itemId
        );
        assertThat(item.get("summary")).isEqualTo("这是一个适合收藏的 AI Agent 教程。");
        assertThat(item.get("category_id")).isEqualTo(categoryId);
        assertThat(item.get("ai_status")).isEqualTo("COMPLETED");
        assertThat((Number) item.get("ai_confidence")).hasToString("1.0");
        assertThat(jdbcTemplate.queryForList(
                "SELECT tag_id FROM knowledge_item_tags WHERE item_id = ?", String.class, itemId
        )).containsExactly(tagId);
        assertThat(jdbcTemplate.queryForList(
                "SELECT value FROM item_ai_suggestions WHERE item_id = ? ORDER BY value", String.class, itemId
        )).containsExactly("Agent", "提示词");
    }

    @Test
    void doesNotCreateCategoriesBeforeUserConfirmsTaxonomy() throws Exception {
        String itemId = importItem();
        when(qwenClient.organize(anyString())).thenReturn(new QwenAiResult(
                "这是一个 AI 工具教程。",
                "default-technology",
                List.of(),
                List.of("AI工具"),
                0.8
        ));

        aiOrganizationService.organizeNow(itemId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT category_id FROM knowledge_items WHERE id = ?", String.class, itemId
        )).isNull();
        assertThat(jdbcTemplate.queryForList(
                "SELECT name FROM categories ORDER BY sort_order", String.class
        )).isEmpty();
    }

    @Test
    void doesNotOverwriteManualMetadata() throws Exception {
        String categoryId = insertCategory("AI");
        String tagId = insertTag("Agent");
        String itemId = importItem();
        mockMvc.perform(patch("/api/v1/items/{id}", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"手动摘要\",\"categoryId\":\"%s\",\"tagIds\":[\"%s\"]}".formatted(categoryId, tagId)))
                .andExpect(status().isOk());
        when(qwenClient.organize(anyString())).thenReturn(new QwenAiResult(
                "AI 摘要",
                null,
                List.of(),
                List.of("新标签"),
                0.8
        ));

        aiOrganizationService.organizeNow(itemId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT summary FROM knowledge_items WHERE id = ?", String.class, itemId
        )).isEqualTo("手动摘要");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM item_ai_suggestions WHERE item_id = ?", Integer.class, itemId
        )).isZero();
    }

    @Test
    void manuallyOrganizesSingleItemFromApi() throws Exception {
        String categoryId = insertCategory("AI");
        String itemId = importItem();
        when(qwenClient.configured()).thenReturn(true);
        when(qwenClient.organize(anyString())).thenReturn(new QwenAiResult(
                "AI summary",
                categoryId,
                List.of(),
                List.of(),
                0.9
        ));

        mockMvc.perform(post("/api/v1/items/{id}/organize", itemId))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT ai_status FROM knowledge_items WHERE id = ?", String.class, itemId
        )).isEqualTo("COMPLETED");
        verify(qwenClient, atLeastOnce()).organize(anyString());
    }

    @Test
    void manuallyOrganizesPendingItemsInBatches() throws Exception {
        enableAi();
        String categoryId = insertCategory("AI");
        String itemId = importItem();
        jdbcTemplate.update("UPDATE knowledge_items SET ai_status = 'FAILED' WHERE id = ?", itemId);
        when(qwenClient.configured()).thenReturn(true);
        when(qwenClient.organize(anyString())).thenReturn(new QwenAiResult(
                "Recovered summary",
                categoryId,
                List.of(),
                List.of(),
                0.8
        ));

        mockMvc.perform(post("/api/v1/ai/organize-pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(1))
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.message").value("已处理 1 条，成功 1 条，失败 0 条"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT summary FROM knowledge_items WHERE id = ?", String.class, itemId
        )).isEqualTo("Recovered summary");
    }

    @Test
    void pendingCountAndBatchUseTheSameEligibilityRules() throws Exception {
        insertItem("discovered-pending", "DISCOVERED", "PENDING", 0);
        insertItem("completed-pending", "COMPLETED", "PENDING", 0);
        insertItem("completed-failed", "COMPLETED", "FAILED", 0);
        insertItem("completed-processing", "COMPLETED", "PROCESSING", 0);
        insertItem("completed-locked", "COMPLETED", "PENDING", 1);

        assertThat(aiEligibilityService.eligibleCount()).isEqualTo(2);
        assertThat(aiEligibilityService.eligibleItemIds(50))
                .containsExactlyInAnyOrder("completed-pending", "completed-failed")
                .doesNotContain("discovered-pending", "completed-processing", "completed-locked");
    }

    @Test
    void returnsClearMessageWhenAiIsDisabled() throws Exception {
        insertItem("completed-pending", "COMPLETED", "PENDING", 0);
        when(qwenClient.configured()).thenReturn(true);

        mockMvc.perform(post("/api/v1/ai/organize-pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(1))
                .andExpect(jsonPath("$.processed").value(0))
                .andExpect(jsonPath("$.message").value("AI 整理尚未开启"));
    }

    @Test
    void returnsClearMessageWhenQwenIsNotConfigured() throws Exception {
        enableAi();
        insertItem("completed-pending", "COMPLETED", "PENDING", 0);
        when(qwenClient.configured()).thenReturn(false);

        mockMvc.perform(post("/api/v1/ai/organize-pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(1))
                .andExpect(jsonPath("$.processed").value(0))
                .andExpect(jsonPath("$.message").value("请先在设置中配置并测试 Qwen API"));
    }

    @Test
    void explainsWhyThereAreNoEligibleItems() throws Exception {
        enableAi();
        insertItem("discovered-pending", "DISCOVERED", "PENDING", 0);
        when(qwenClient.configured()).thenReturn(true);

        mockMvc.perform(post("/api/v1/ai/organize-pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(0))
                .andExpect(jsonPath("$.blockedByContent").value(1))
                .andExpect(jsonPath("$.message").value("没有可整理内容，请先完成正文同步"));

        jdbcTemplate.update("UPDATE knowledge_items SET content_status = 'COMPLETED', manual_metadata_locked = 1 WHERE id = ?", "discovered-pending");

        mockMvc.perform(post("/api/v1/ai/organize-pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(0))
                .andExpect(jsonPath("$.blockedByManualLock").value(1))
                .andExpect(jsonPath("$.message").value("待处理内容已被用户手动锁定"));

        jdbcTemplate.update("DELETE FROM knowledge_items");

        mockMvc.perform(post("/api/v1/ai/organize-pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(0))
                .andExpect(jsonPath("$.message").value("当前没有需要 AI 整理的内容"));
    }

    private String insertCategory(String name) {
        String id = name.toLowerCase();
        jdbcTemplate.update(
                "INSERT INTO categories(id, name, sort_order, created_at, updated_at) VALUES (?, ?, 0, ?, ?)",
                id, name, "2026-07-31T00:00:00Z", "2026-07-31T00:00:00Z"
        );
        return id;
    }

    private String insertTag(String name) {
        String id = name.toLowerCase();
        jdbcTemplate.update(
                "INSERT INTO tags(id, name, normalized_name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                id, name, name.toLowerCase(), "2026-07-31T00:00:00Z", "2026-07-31T00:00:00Z"
        );
        return id;
    }

    private String importItem() throws Exception {
        String body = mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", "test-extension-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientBatchId": "ai-test-batch",
                                  "captureMode": "CURRENT_POST",
                                  "extractorVersion": "test-1",
                                  "items": [{
                                    "url": "https://www.xiaohongshu.com/explore/aitest123?xsec_token=test-token-placeholder",
                                    "title": "AI Agent 教程",
                                    "author": "作者",
                                    "text": "正文内容",
                                    "captureLevel": "DETAIL",
                                    "capturedAt": "2026-07-31T12:00:00+08:00"
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("results").path(0).path("itemId").asText();
    }

    private void insertItem(String id, String contentStatus, String aiStatus, int locked) {
        jdbcTemplate.update("""
                INSERT INTO knowledge_items(
                  id, source_type, canonical_url, original_url, title, content, content_status,
                  image_urls_json, capture_level, ai_status, lifecycle_status, manual_metadata_locked,
                  created_at, source_updated_at, updated_at
                ) VALUES (?, 'XIAOHONGSHU', ?, ?, ?, '正文内容', ?, '[]', 'DETAIL', ?, 'ACTIVE', ?, ?, ?, ?)
                """,
                id,
                "https://www.xiaohongshu.com/explore/" + id,
                "https://www.xiaohongshu.com/explore/" + id,
                id,
                contentStatus,
                aiStatus,
                locked,
                "2026-08-01T00:00:00Z",
                "2026-08-01T00:00:00Z",
                "2026-08-01T00:00:00Z"
        );
    }

    private void enableAi() {
        jdbcTemplate.update("""
                INSERT INTO app_settings(key, value, updated_at) VALUES ('ai.enabled', 'true', '2026-08-01T00:00:00Z')
                ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at
                """);
    }
}
