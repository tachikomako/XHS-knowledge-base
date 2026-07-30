package io.github.tachikomako.xhsknowledge.media;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MediaProxyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsNonXhsHosts() throws Exception {
        mockMvc.perform(get("/api/v1/media/proxy").param("url", "http://127.0.0.1/private.png"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_HOST"));
    }

    @Test
    void rejectsInvalidUrls() throws Exception {
        mockMvc.perform(get("/api/v1/media/proxy").param("url", "not a url"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEDIA_URL"));
    }
}
