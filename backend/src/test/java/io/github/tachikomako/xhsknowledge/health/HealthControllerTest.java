package io.github.tachikomako.xhsknowledge.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "qwen.api-key=",
        "xhs.secrets-dir=target/test-secrets/health"
})
class HealthControllerTest {

    static {
        try {
            Files.deleteIfExists(Path.of("target/test-secrets/health/ai.properties"));
        } catch (Exception ignored) {
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsStableHealthContract() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.apiVersion").value("v1"))
                .andExpect(jsonPath("$.appVersion").value("0.1.0-SNAPSHOT"))
                .andExpect(jsonPath("$.aiConfigured").value(false));
    }

    @Test
    void allowsManifestV3ExtensionPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/health")
                        .header("Origin", "chrome-extension://abcdefghijklmnop")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "chrome-extension://abcdefghijklmnop"));
    }
}
