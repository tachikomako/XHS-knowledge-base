package io.github.tachikomako.xhsknowledge.importx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ImportFlowIntegrationTest {

    private static final String TOKEN = "test-extension-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM item_ai_suggestions");
        jdbcTemplate.update("DELETE FROM knowledge_item_tags");
        jdbcTemplate.update("DELETE FROM item_source_relations");
        jdbcTemplate.update("DELETE FROM import_batches");
        jdbcTemplate.update("DELETE FROM knowledge_items");
        jdbcTemplate.update("DELETE FROM tags");
        jdbcTemplate.update("DELETE FROM categories");
    }

    @Test
    void requiresExtensionTokenForImport() throws Exception {
        mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson("batch-auth", "CARD", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_EXTENSION_TOKEN"));
    }

    @Test
    void importsIdempotentlyAndUpgradesCardToDetail() throws Exception {
        String firstBody = mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson("batch-card", "CARD", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.results[0].contentStatus").value("DISCOVERED"))
                .andExpect(jsonPath("$.updated").value(0))
                .andReturn().getResponse().getContentAsString();

        JsonNode first = objectMapper.readTree(firstBody);
        String itemId = first.path("results").path(0).path("itemId").asText();
        assertThat(itemId).isNotBlank();

        mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson("batch-card", "CARD", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.created").value(1));

        mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson("batch-detail", "DETAIL", "完整正文内容")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));

        mockMvc.perform(get("/api/v1/items/{id}", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captureLevel").value("DETAIL"))
                .andExpect(jsonPath("$.contentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.content").value("完整正文内容"))
                .andExpect(jsonPath("$.canonicalUrl").value(
                        "https://www.xiaohongshu.com/explore/abc123"));

        mockMvc.perform(get("/api/v1/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void physicallyDeletesItemAndAllowsReimportWithSameNoteId() throws Exception {
        String categoryId = "category-fixture";
        String tagId = "tag-fixture";
        jdbcTemplate.update(
                "INSERT INTO categories(id, name, sort_order, created_at, updated_at) VALUES (?, ?, 0, ?, ?)",
                categoryId, "学习", "2026-07-25T00:00:00Z", "2026-07-25T00:00:00Z"
        );
        jdbcTemplate.update(
                "INSERT INTO tags(id, name, normalized_name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                tagId, "教程", "教程", "2026-07-25T00:00:00Z", "2026-07-25T00:00:00Z"
        );

        String body = mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson("batch-create", "DETAIL", "正文")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String itemId = objectMapper.readTree(body).path("results").path(0).path("itemId").asText();

        var changes = objectMapper.createObjectNode()
                .put("summary", "我的摘要")
                .put("userNote", "稍后实践")
                .put("categoryId", categoryId);
        changes.putArray("tagIds").add(tagId);
        mockMvc.perform(patch("/api/v1/items/{id}", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changes.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("我的摘要"))
                .andExpect(jsonPath("$.userNote").value("稍后实践"))
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.tagIds[0]").value(tagId))
                .andExpect(jsonPath("$.manualMetadataLocked").value(true));

        mockMvc.perform(delete("/api/v1/items/{id}", itemId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/items/{id}", itemId))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_items WHERE id = ?", Integer.class, itemId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_item_tags WHERE item_id = ?", Integer.class, itemId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM categories WHERE id = ?", Integer.class, categoryId
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tags WHERE id = ?", Integer.class, tagId
        )).isOne();

        String reimportBody = mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson("batch-after-delete", "DETAIL", "重新导入")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.results[0].status").value("CREATED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(reimportBody).path("results").path(0).path("itemId").asText())
                .isNotEqualTo(itemId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_items WHERE source_item_id = 'abc123'", Integer.class
        )).isOne();
    }

    @Test
    void clearsLibraryPhysicallyWhileKeepingTaxonomySettingsAndSyncHistory() throws Exception {
        String categoryId = "clear-category";
        String tagId = "clear-tag";
        jdbcTemplate.update(
                "INSERT INTO categories(id, name, sort_order, created_at, updated_at) VALUES (?, ?, 0, ?, ?)",
                categoryId, "Clear Category", "2026-08-01T00:00:00Z", "2026-08-01T00:00:00Z"
        );
        jdbcTemplate.update(
                "INSERT INTO tags(id, name, normalized_name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                tagId, "Clear Tag", "clear tag", "2026-08-01T00:00:00Z", "2026-08-01T00:00:00Z"
        );
        jdbcTemplate.update(
                "INSERT OR REPLACE INTO app_settings(key, value, updated_at) VALUES (?, ?, ?)",
                "clear-test-setting", "on", "2026-08-01T00:00:00Z"
        );
        jdbcTemplate.update(
                "INSERT INTO sync_runs(id, requested_sources, status, started_at) VALUES (?, ?, ?, ?)",
                "clear-sync-run", "[\"FAVORITE\"]", "SUCCESS", "2026-08-01T00:00:00Z"
        );

        String body = mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson("batch-clear-create", "DETAIL", "clear body", "https://www.xiaohongshu.com/explore/clear123?xsec_token=test-token-placeholder", "FAVORITE")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String itemId = objectMapper.readTree(body).path("results").path(0).path("itemId").asText();
        jdbcTemplate.update("INSERT INTO knowledge_item_tags(item_id, tag_id) VALUES (?, ?)", itemId, tagId);
        jdbcTemplate.update(
                "INSERT INTO item_ai_suggestions(item_id, suggestion_type, value, created_at) VALUES (?, ?, ?, ?)",
                itemId, "TAG", "Suggestion", "2026-08-01T00:00:00Z"
        );

        mockMvc.perform(post("/api/v1/items/clear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONFIRMATION"));

        mockMvc.perform(post("/api/v1/items/clear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"清空知识库\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedItems").value(1));

        assertThat(count("knowledge_items")).isZero();
        assertThat(count("knowledge_item_tags")).isZero();
        assertThat(count("item_source_relations")).isZero();
        assertThat(count("item_ai_suggestions")).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories WHERE id = ?", Integer.class, categoryId)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tags WHERE id = ?", Integer.class, tagId)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_settings WHERE key = ?", Integer.class, "clear-test-setting")).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sync_runs WHERE id = ?", Integer.class, "clear-sync-run")).isOne();

        mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson("batch-after-clear", "DETAIL", "reimport", "https://www.xiaohongshu.com/explore/clear123?xsec_token=test-token-placeholder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.results[0].status").value("CREATED"));
    }

    @Test
    void preservesCredentialedSourceUrlWhileDeduplicatingByNoteId() throws Exception {
        String noteId = "6a65035a000000000e035015";
        String feedUrl = "https://www.xiaohongshu.com/explore/" + noteId
                + "?xsec_token=feed-token-placeholder&xsec_source=pc_feed";
        String bareUrl = "https://www.xiaohongshu.com/explore/" + noteId;
        String collectUrl = "https://www.xiaohongshu.com/explore/" + noteId
                + "?xsec_token=collect-token-placeholder&xsec_source=pc_collect";

        String firstBody = mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson("batch-feed-url", "DETAIL", "detail", feedUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andReturn().getResponse().getContentAsString();
        String itemId = objectMapper.readTree(firstBody).path("results").path(0).path("itemId").asText();

        mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson("batch-bare-url", "CARD", null, bareUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(1));

        mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson("batch-collect-url", "CARD", null, collectUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));

        mockMvc.perform(get("/api/v1/items/{id}", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceItemId").value(noteId))
                .andExpect(jsonPath("$.canonicalUrl").value(bareUrl))
                .andExpect(jsonPath("$.originalUrl").value(collectUrl));

        mockMvc.perform(get("/api/v1/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void recordsFavoriteAndLikedRelationsWithoutDuplicatingItems() throws Exception {
        String noteId = "relation123";
        String favoriteUrl = "https://www.xiaohongshu.com/explore/" + noteId
                + "?xsec_token=fav-token&xsec_source=pc_collect";
        String likedUrl = "https://www.xiaohongshu.com/explore/" + noteId
                + "?xsec_token=liked-token&xsec_source=pc_like";

        mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson("batch-relation-fav", "CARD", null, favoriteUrl, "FAVORITE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson("batch-relation-liked", "CARD", null, likedUrl, "LIKED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_items WHERE source_item_id = ?", Integer.class, noteId
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM item_source_relations WHERE item_id = (SELECT id FROM knowledge_items WHERE source_item_id = ?)",
                Integer.class,
                noteId
        )).isEqualTo(2);
    }

    private String importJson(String batchId, String captureLevel, String text) throws Exception {
        return importJson(
                batchId,
                captureLevel,
                text,
                "https://www.xiaohongshu.com/explore/abc123?xsec_token=test-token-placeholder"
        );
    }

    private String importJson(String batchId, String captureLevel, String text, String url) throws Exception {
        return importJson(batchId, captureLevel, text, url, null);
    }

    private String importJson(String batchId, String captureLevel, String text, String url, String sourceRelation) throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("clientBatchId", batchId);
        root.put("captureMode", "DETAIL".equals(captureLevel) ? "CURRENT_POST" : "FAVORITES_PAGE");
        root.put("extractorVersion", "test-1");
        var item = root.putArray("items").addObject();
        item.put("url", url);
        item.put("title", "示例收藏");
        item.put("author", "作者");
        if (text == null) {
            item.putNull("text");
        } else {
            item.put("text", text);
        }
        if (sourceRelation != null) item.put("sourceRelation", sourceRelation);
        item.put("captureLevel", captureLevel);
        item.put("capturedAt", "2026-07-25T12:00:00+08:00");
        return objectMapper.writeValueAsString(root);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
