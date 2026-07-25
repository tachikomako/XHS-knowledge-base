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
        jdbcTemplate.update("DELETE FROM knowledge_item_tags");
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
                .andExpect(jsonPath("$.content").value("完整正文内容"))
                .andExpect(jsonPath("$.canonicalUrl").value(
                        "https://www.xiaohongshu.com/explore/abc123"));

        mockMvc.perform(get("/api/v1/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void preservesTrashTombstoneAndSupportsManualNotes() throws Exception {
        String body = mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson("batch-create", "DETAIL", "正文")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String itemId = objectMapper.readTree(body).path("results").path(0).path("itemId").asText();

        mockMvc.perform(patch("/api/v1/items/{id}", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"我的摘要\",\"userNote\":\"稍后实践\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("我的摘要"))
                .andExpect(jsonPath("$.userNote").value("稍后实践"))
                .andExpect(jsonPath("$.manualMetadataLocked").value(true));

        mockMvc.perform(post("/api/v1/items/{id}/trash", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("TRASHED"));

        mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson("batch-after-trash", "DETAIL", "更长的新正文")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(1));

        mockMvc.perform(get("/api/v1/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get("/api/v1/items").param("lifecycleStatus", "TRASHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
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

    private String importJson(String batchId, String captureLevel, String text) throws Exception {
        return importJson(
                batchId,
                captureLevel,
                text,
                "https://www.xiaohongshu.com/explore/abc123?xsec_token=test-token-placeholder"
        );
    }

    private String importJson(String batchId, String captureLevel, String text, String url) throws Exception {
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
        item.put("coverUrl", "https://sns-webpic-qc.xhscdn.com/example.jpg");
        item.putArray("imageUrls");
        item.put("captureLevel", captureLevel);
        item.put("capturedAt", "2026-07-25T12:00:00+08:00");
        return objectMapper.writeValueAsString(root);
    }
}
