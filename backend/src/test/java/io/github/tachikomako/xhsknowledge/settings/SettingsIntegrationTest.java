package io.github.tachikomako.xhsknowledge.settings;

import io.github.tachikomako.xhsknowledge.ai.QwenClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "qwen.api-key=test-secret",
        "qwen.model=qwen-test"
})
class SettingsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private QwenClient qwenClient;

    @BeforeEach
    void cleanSettings() {
        jdbcTemplate.update("DELETE FROM app_settings");
        jdbcTemplate.update("DELETE FROM knowledge_items");
    }

    @Test
    void exposesOnlyAiStatusAndStoresTheSwitchInSqlite() throws Exception {
        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(true))
                .andExpect(jsonPath("$.aiConfigured").value(true))
                .andExpect(jsonPath("$.model").value("qwen-test"))
                .andExpect(jsonPath("$.pendingAiCount").value(0))
                .andExpect(jsonPath("$.failedAiCount").value(0))
                .andExpect(content().string(not(containsString("test-secret"))));

        mockMvc.perform(patch("/api/v1/settings/ai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aiEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(true));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT value FROM app_settings WHERE key = 'ai.enabled'", String.class
        )).isEqualTo("true");
    }

    @Test
    void testsQwenConnectionWithoutReturningSecretsOrRawErrors() throws Exception {
        when(qwenClient.configured()).thenReturn(true);
        when(qwenClient.model()).thenReturn("qwen-test");
        doThrow(new IllegalStateException("test-secret raw provider error"))
                .when(qwenClient).testConnection();

        mockMvc.perform(post("/api/v1/settings/ai/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.model").value("qwen-test"))
                .andExpect(jsonPath("$.message").value("Qwen connection failed"))
                .andExpect(content().string(not(containsString("test-secret"))));
    }
}
