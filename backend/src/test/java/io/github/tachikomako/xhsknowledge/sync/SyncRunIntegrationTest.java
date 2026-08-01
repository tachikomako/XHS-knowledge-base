package io.github.tachikomako.xhsknowledge.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SyncRunIntegrationTest {

    private static final String TOKEN = "test-extension-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM sync_runs");
    }

    @Test
    void createsUpdatesAndReadsLatestManualSyncRun() throws Exception {
        String body = mockMvc.perform(post("/api/v1/sync-runs")
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedSources\":[\"FAVORITE\",\"LIKED\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.requestedSources").value("FAVORITE,LIKED"))
                .andReturn().getResponse().getContentAsString();
        String id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(body).path("id").asText();

        mockMvc.perform(patch("/api/v1/sync-runs/{id}", id)
                        .header("X-Extension-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "PARTIAL_FAILED",
                                  "discoveredCount": 3,
                                  "processedCount": 2,
                                  "createdCount": 1,
                                  "updatedCount": 1,
                                  "unchangedCount": 0,
                                  "contentCompletedCount": 0,
                                  "contentFailedCount": 0,
                                  "aiCompletedCount": 0,
                                  "aiFailedCount": 0,
                                  "errorSummary": "1 条缺少访问参数"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL_FAILED"))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/sync-runs/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.discoveredCount").value(3));
    }

    @Test
    void protectsWriteEndpointsWithExtensionToken() throws Exception {
        mockMvc.perform(post("/api/v1/sync-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedSources\":[\"FAVORITE\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_EXTENSION_TOKEN"));
    }
}
