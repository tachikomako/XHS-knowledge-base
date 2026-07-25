package io.github.tachikomako.xhsknowledge.metadata;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MetadataIntegrationTest {

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
    void managesTaxonomyAssignsMetadataAndFiltersItems() throws Exception {
        String rootId = createCategory("学习", null);
        String childId = createCategory("英语", rootId);

        String tagBody = mockMvc.perform(post("/api/v1/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"#AI\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("AI"))
                .andReturn().getResponse().getContentAsString();
        String tagId = objectMapper.readTree(tagBody).path("id").asText();

        mockMvc.perform(post("/api/v1/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ai\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_ALREADY_EXISTS"));

        String itemId = importItem();
        var itemUpdate = objectMapper.createObjectNode().put("categoryId", childId);
        itemUpdate.putArray("tagIds").add(tagId);
        mockMvc.perform(patch("/api/v1/items/{id}", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemUpdate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(childId))
                .andExpect(jsonPath("$.tagIds[0]").value(tagId));

        mockMvc.perform(get("/api/v1/items").param("categoryId", childId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/v1/items").param("tagId", tagId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(childId)).exists());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_items WHERE category_id = ? AND lifecycle_status = 'ACTIVE'",
                Integer.class,
                childId
        )).isEqualTo(1);
        mockMvc.perform(get("/api/v1/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].itemCount").value(1));

        mockMvc.perform(delete("/api/v1/categories/{id}", childId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));

        mockMvc.perform(put("/api/v1/categories/{id}", childId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"英语学习\",\"parentId\":\"%s\",\"sortOrder\":10}".formatted(rootId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("英语学习"));

        mockMvc.perform(delete("/api/v1/tags/{id}", tagId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/items/{id}", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagIds").isEmpty());
    }

    private String createCategory(String name, String parentId) throws Exception {
        var request = objectMapper.createObjectNode().put("name", name).put("sortOrder", 0);
        if (parentId == null) request.putNull("parentId"); else request.put("parentId", parentId);
        String body = mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("id").asText();
    }

    private String importItem() throws Exception {
        JsonNode request = objectMapper.readTree("""
                {
                  "clientBatchId": "metadata-test-batch",
                  "captureMode": "CURRENT_POST",
                  "extractorVersion": "test-1",
                  "items": [{
                    "url": "https://www.xiaohongshu.com/explore/metadata123",
                    "title": "AI 英语学习",
                    "author": "示例作者",
                    "text": "脱敏正文",
                    "imageUrls": [],
                    "captureLevel": "DETAIL",
                    "capturedAt": "2026-07-25T12:00:00+08:00"
                  }]
                }
                """);
        String body = mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", "test-extension-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("results").path(0).path("itemId").asText();
    }
}
