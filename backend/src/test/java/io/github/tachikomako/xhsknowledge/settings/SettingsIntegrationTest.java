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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
        "qwen.base-url=https://env.example/v1",
        "qwen.model=qwen-test",
        "xhs.secrets-dir=target/test-secrets/settings",
        "xhs.extension-token=test-token"
})
class SettingsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SettingsService settingsService;

    @MockitoBean
    private QwenClient qwenClient;

    private final Path secretsFile = Path.of("target/test-secrets/settings/ai.properties");

    @BeforeEach
    void cleanSettings() throws Exception {
        jdbcTemplate.update("DELETE FROM app_settings");
        jdbcTemplate.update("DELETE FROM knowledge_item_source_tags");
        jdbcTemplate.update("DELETE FROM knowledge_items");
        Files.deleteIfExists(secretsFile);
    }

    @Test
    void exposesOnlyAiStatusAndStoresTheSwitchInSqlite() throws Exception {
        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(true))
                .andExpect(jsonPath("$.aiConfigured").value(true))
                .andExpect(jsonPath("$.baseUrl").value("https://env.example/v1"))
                .andExpect(jsonPath("$.model").value("qwen-test"))
                .andExpect(jsonPath("$.pendingAiCount").value(0))
                .andExpect(jsonPath("$.failedAiCount").value(0))
                .andExpect(content().string(not(containsString("test-secret"))));

        mockMvc.perform(patch("/api/v1/settings/ai")
                        .header("X-Extension-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aiEnabled": true,
                                  "apiKey": "saved-secret",
                                  "baseUrl": "https://saved.example/v1",
                                  "model": "qwen-saved"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(true))
                .andExpect(jsonPath("$.aiConfigured").value(true))
                .andExpect(jsonPath("$.baseUrl").value("https://saved.example/v1"))
                .andExpect(jsonPath("$.model").value("qwen-saved"))
                .andExpect(content().string(not(containsString("saved-secret"))));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT value FROM app_settings WHERE key = 'ai.enabled'", String.class
        )).isEqualTo("true");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT value FROM app_settings WHERE key = 'qwen.base-url'", String.class
        )).isEqualTo("https://saved.example/v1");
        assertThat(Files.readString(secretsFile)).contains("saved-secret");
        assertThat(settingsService.aiRuntimeSettings().apiKey()).isEqualTo("saved-secret");
        assertThat(settingsService.aiRuntimeSettings().baseUrl()).isEqualTo("https://saved.example/v1");
        assertThat(settingsService.aiRuntimeSettings().model()).isEqualTo("qwen-saved");
    }

    @Test
    void keepsSavedApiKeyWhenPatchKeyIsBlankAndCanClearIt() throws Exception {
        mockMvc.perform(patch("/api/v1/settings/ai")
                        .header("X-Extension-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aiEnabled": true,
                                  "apiKey": "saved-secret",
                                  "baseUrl": "https://saved.example/v1",
                                  "model": "qwen-saved"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/settings/ai")
                        .header("X-Extension-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aiEnabled": false,
                                  "apiKey": "",
                                  "baseUrl": "https://next.example/v1",
                                  "model": "qwen-next"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(false))
                .andExpect(jsonPath("$.aiConfigured").value(true));

        assertThat(settingsService.aiRuntimeSettings().apiKey()).isEqualTo("saved-secret");

        mockMvc.perform(delete("/api/v1/settings/ai/credentials")
                        .header("X-Extension-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiConfigured").value(false))
                .andExpect(content().string(not(containsString("saved-secret"))));
        assertThat(Files.exists(secretsFile)).isFalse();
        assertThat(settingsService.aiRuntimeSettings().apiKey()).isEmpty();
    }

    @Test
    void testsQwenConnectionWithoutReturningSecretsOrRawErrors() throws Exception {
        when(qwenClient.configured()).thenReturn(true);
        when(qwenClient.model()).thenReturn("qwen-test");
        doThrow(new IllegalStateException("test-secret raw provider error"))
                .when(qwenClient).testConnection();

        mockMvc.perform(post("/api/v1/settings/ai/test")
                        .header("X-Extension-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.model").value("qwen-test"))
                .andExpect(jsonPath("$.message").value("连接失败"))
                .andExpect(content().string(not(containsString("test-secret"))));
    }

    @Test
    void rejectsSettingsWritesWithoutLocalToken() throws Exception {
        mockMvc.perform(patch("/api/v1/settings/ai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aiEnabled": true,
                                  "apiKey": "saved-secret",
                                  "baseUrl": "https://saved.example/v1",
                                  "model": "qwen-saved"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
