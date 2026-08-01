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
        assertThat(item.get("ai_status")).isEqualTo("SUCCESS");
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
        )).isEqualTo("SUCCESS");
        verify(qwenClient, atLeastOnce()).organize(anyString());
    }

    @Test
    void manuallyOrganizesPendingItemsInBatches() throws Exception {
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
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.processed").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.succeeded").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.failed").value(0));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT summary FROM knowledge_items WHERE id = ?", String.class, itemId
        )).isEqualTo("Recovered summary");
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
}
