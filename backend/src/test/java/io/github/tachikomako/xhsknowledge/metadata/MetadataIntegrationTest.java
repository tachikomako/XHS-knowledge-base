package io.github.tachikomako.xhsknowledge.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tachikomako.xhsknowledge.ai.QwenCategorySuggestions;
import io.github.tachikomako.xhsknowledge.ai.QwenClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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

    @MockitoBean
    private QwenClient qwenClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM knowledge_item_tags");
        jdbcTemplate.update("DELETE FROM knowledge_item_source_tags");
        jdbcTemplate.update("DELETE FROM item_source_relations");
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
        mockMvc.perform(get("/api/v1/items").param("categoryId", rootId))
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

    @Test
    void mergesTagsWithoutDuplicatingItemLinks() throws Exception {
        String sourceTagId = createTag("source");
        String targetTagId = createTag("target");
        String firstItemId = importItem("merge-first-batch", "mergefirst123");
        String secondItemId = importItem("merge-second-batch", "mergesecond123");

        replaceTags(firstItemId, sourceTagId, targetTagId);
        replaceTags(secondItemId, sourceTagId);

        mockMvc.perform(post("/api/v1/tags/{id}/merge", sourceTagId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetTagId\":\"%s\"}".formatted(targetTagId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetTagId))
                .andExpect(jsonPath("$.itemCount").value(2));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tags WHERE id = ?", Integer.class, sourceTagId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_item_tags WHERE tag_id = ?", Integer.class, targetTagId
        )).isEqualTo(2);
    }

    @Test
    void generatesAndConfirmsCategorySuggestionsFromSourceTags() throws Exception {
        importItemWithTags();
        when(qwenClient.configured()).thenReturn(true);
        when(qwenClient.suggestCategories(anyString())).thenReturn(new QwenCategorySuggestions(
                java.util.List.of(new CategorySuggestion("AI 与编程", "AI tools", "coding and agents", "daily life"))
        ));

        mockMvc.perform(get("/api/v1/categories/source-tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("AI"));

        mockMvc.perform(post("/api/v1/categories/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].name").value("AI 与编程"))
                .andExpect(jsonPath("$.sourceTags[0].name").value("AI"));

        mockMvc.perform(post("/api/v1/categories/suggestions/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categories":[{"name":"AI 与编程","definition":"","scope":"","exclusions":""}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("AI 与编程"));
    }

    @Test
    void unifiesSourceAndKnowledgeTagsWithoutDuplicatingUsage() throws Exception {
        String itemId = importItemWithTags();
        String knowledgeTagId = createTag("#ai");
        replaceTags(itemId, knowledgeTagId);

        mockMvc.perform(get("/api/v1/tags").param("view", "unified").param("query", "AI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("AI"))
                .andExpect(jsonPath("$[0].usageCount").value(1))
                .andExpect(jsonPath("$[0].origins[0]").value("SOURCE"))
                .andExpect(jsonPath("$[0].origins[1]").value("KNOWLEDGE"));

        mockMvc.perform(get("/api/v1/items").param("tagName", "#ai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void searchesChineseTagsFromHistoricalContentWhenSourceTagsAreMissing() throws Exception {
        String itemId = importItem("chinese-tag-batch", "chinesetag123");
        jdbcTemplate.update("DELETE FROM knowledge_item_source_tags WHERE item_id = ?", itemId);
        jdbcTemplate.update(
                "UPDATE knowledge_items SET content = ?, content_status = 'COMPLETED' WHERE id = ?",
                "训练 #体态 #体态改善 #核心训练",
                itemId
        );

        mockMvc.perform(get("/api/v1/tags").param("view", "unified").param("query", "#体态"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("体态"))
                .andExpect(jsonPath("$[1].name").value("体态改善"));
        mockMvc.perform(get("/api/v1/items").param("tagName", "体态"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
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

    private String createTag(String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("id").asText();
    }

    private void replaceTags(String itemId, String... tagIds) throws Exception {
        var request = objectMapper.createObjectNode();
        var tags = request.putArray("tagIds");
        for (String tagId : tagIds) tags.add(tagId);
        mockMvc.perform(patch("/api/v1/items/{id}", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isOk());
    }

    private String importItem() throws Exception {
        return importItem("metadata-test-batch", "metadata123");
    }

    private String importItemWithTags() throws Exception {
        JsonNode request = objectMapper.readTree("""
                {
                  "clientBatchId": "metadata-source-tag-batch",
                  "captureMode": "CURRENT_POST",
                  "extractorVersion": "test-1",
                  "items": [{
                    "url": "https://www.xiaohongshu.com/explore/sourcetag123",
                    "title": "AI 编程",
                    "author": "作者",
                    "text": "正文 #AI #编程",
                    "sourceTags": ["AI", "编程"],
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

    private String importItem(String batchId, String sourceId) throws Exception {
        JsonNode request = objectMapper.readTree("""
                {
                  "clientBatchId": "%s",
                  "captureMode": "CURRENT_POST",
                  "extractorVersion": "test-1",
                  "items": [{
                    "url": "https://www.xiaohongshu.com/explore/%s",
                    "title": "AI 英语学习",
                    "author": "示例作者",
                    "text": "脱敏正文",
                    "captureLevel": "DETAIL",
                    "capturedAt": "2026-07-25T12:00:00+08:00"
                  }]
                }
                """.formatted(batchId, sourceId));
        String body = mockMvc.perform(post("/api/v1/imports/xiaohongshu")
                        .header("X-Extension-Token", "test-extension-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("results").path(0).path("itemId").asText();
    }
}
