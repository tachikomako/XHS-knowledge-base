package io.github.tachikomako.xhsknowledge.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tachikomako.xhsknowledge.settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QwenClientTest {

    @Test
    void usesRuntimeSettingsForRequests() {
        RestClient.Builder builder = RestClient.builder();
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.aiRuntimeSettings()).thenReturn(new SettingsService.AiRuntimeSettings(
                "saved-secret",
                "https://saved.example/v1",
                "qwen-saved"
        ));

        QwenClient client = new QwenClient(new ObjectMapper(), builder, settingsService);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://saved.example/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer saved-secret"))
                .andExpect(content().string(containsString("qwen-saved")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.testConnection();
        server.verify();
    }
}
