package com.massimotter.weave.backend.chat.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceController;
import com.massimotter.weave.backend.config.ChatE2eProofProperties;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatE2eCallbackReplayControllerTest {

    private static final String RUN_ID = "isolated-run-1234";

    @Test
    void replaysCapturedCallbackWithoutReturningItsPrivateBody() {
        ChatE2eProofProperties properties = properties();
        ChatE2eCallbackReplayTap tap = new ChatE2eCallbackReplayTap(properties);
        byte[] privatePayload = "{\"events\":[{\"ciphertext\":\"private-opaque-value\"}]}"
                .getBytes(StandardCharsets.UTF_8);
        tap.captureFirst("homeserver-transaction-1", privatePayload);
        MatrixApplicationServiceController applicationService = mock(MatrixApplicationServiceController.class);
        when(applicationService.replayCapturedForIsolatedProof(any()))
                .thenReturn(ResponseEntity.ok(Map.of()));
        ChatE2eCallbackReplayController controller = new ChatE2eCallbackReplayController(
                tap, applicationService, properties, new ObjectMapper());

        ResponseEntity<Map<String, Object>> response = controller.replay(request("{\"runId\":\"" + RUN_ID + "\"}"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .containsEntry("contractVersion", "chat-provider-callback-replay-v1")
                .containsEntry("replayed", true)
                .containsEntry("supportSafe", true);
        assertThat(response.toString()).doesNotContain("private-opaque-value", "homeserver-transaction-1");
        verify(applicationService).replayCapturedForIsolatedProof(any());
    }

    @Test
    void rejectsWrongRunAndMissingCaptureWithSupportSafeResponses() {
        ChatE2eProofProperties properties = properties();
        ChatE2eCallbackReplayTap tap = new ChatE2eCallbackReplayTap(properties);
        MatrixApplicationServiceController applicationService = mock(MatrixApplicationServiceController.class);
        ChatE2eCallbackReplayController controller = new ChatE2eCallbackReplayController(
                tap, applicationService, properties, new ObjectMapper());

        assertThat(controller.replay(request("{\"runId\":\"wrong-run-1234\"}")).getStatusCode().value())
                .isEqualTo(400);
        ResponseEntity<Map<String, Object>> missing = controller.replay(request("{\"runId\":\"" + RUN_ID + "\"}"));
        assertThat(missing.getStatusCode().value()).isEqualTo(409);
        assertThat(missing.getBody()).containsEntry("supportSafe", true);
    }

    private ChatE2eProofProperties properties() {
        return new ChatE2eProofProperties(true, "/private/proof-token", RUN_ID, "isolated");
    }

    private MockHttpServletRequest request(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
