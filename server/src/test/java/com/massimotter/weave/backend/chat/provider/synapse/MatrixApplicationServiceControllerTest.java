package com.massimotter.weave.backend.chat.provider.synapse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.e2e.ChatE2eCallbackReplayTap;
import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.config.ChatE2eProofProperties;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatrixApplicationServiceControllerTest {

    private final CanonicalChatStore store = mock(CanonicalChatStore.class);
    private final SynapseBackedCanonicalChatAdapter adapter = mock(SynapseBackedCanonicalChatAdapter.class);
    private final MatrixSynapseChatSouthboundAdapter provider = mock(MatrixSynapseChatSouthboundAdapter.class);
    private final ChatProviderPortAdapterResolver resolver = mock(ChatProviderPortAdapterResolver.class);
    private final ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();

    @Test
    void callbackIsBoundedDurablyDeduplicatedAndAcknowledgedAfterProcessing() {
        when(provider.providerKey()).thenReturn("matrix-synapse");
        when(resolver.synapseAdapter()).thenReturn(adapter);
        when(store.beginCallback(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(CanonicalChatStore.CallbackStart.NEW);
        when(store.recordCallbackEvent(anyString(), any()))
                .thenReturn(new CanonicalChatStore.CallbackEventResult("accepted", "hash"));
        MatrixApplicationServiceController controller = controller(65_536);
        MockHttpServletRequest request = request("""
                {"events":[{
                  "event_id":"$opaque:matrix.internal",
                  "room_id":"!opaque:matrix.internal",
                  "sender":"@_weave_opaque:matrix.internal",
                  "type":"m.room.encrypted",
                  "origin_server_ts":1,
                  "unsigned":{"transaction_id":"weave_opaque"},
                  "content":{
                    "algorithm":"m.megolm.v1.aes-sha2",
                    "ciphertext":"opaque-ciphertext",
                    "sender_key":"curve25519:opaque",
                    "session_id":"opaque-session",
                    "device_id":"OPAQUEDEVICE"
                  }
                }]}
                """);

        var response = controller.transaction("hs-transaction-1", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        verify(store).recordCallbackEvent(anyString(), any());
        verify(store).completeCallback("matrix-synapse", "hs-transaction-1", 0);
        assertThat(response.toString()).doesNotContain("opaque-ciphertext", "$opaque", "!opaque", "@_weave_");
    }

    @Test
    void callbackReplayDoesNotProcessEventsAgain() {
        when(provider.providerKey()).thenReturn("matrix-synapse");
        when(resolver.synapseAdapter()).thenReturn(adapter);
        when(store.beginCallback(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(CanonicalChatStore.CallbackStart.DUPLICATE);
        MatrixApplicationServiceController controller = controller(65_536);

        var response = controller.transaction("hs-transaction-1", request("{\"events\":[]}"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(store, never()).recordCallbackEvent(anyString(), any());
        verify(store, never()).completeCallback(anyString(), anyString(), anyInt());
    }

    @Test
    void callbackSemanticDisagreementFailsClosedWithoutProcessingEvents() {
        when(provider.providerKey()).thenReturn("matrix-synapse");
        when(resolver.synapseAdapter()).thenReturn(adapter);
        when(store.beginCallback(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(CanonicalChatStore.CallbackStart.SEMANTIC_MISMATCH);
        MatrixApplicationServiceController controller = controller(65_536);

        var response = controller.transaction("hs-transaction-mismatch", request("{\"events\":[]}"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody())
                .containsEntry("errcode", "M_UNAVAILABLE")
                .doesNotContainValue("hs-transaction-mismatch");
        verify(store, never()).recordCallbackEvent(anyString(), any());
        verify(store, never()).completeCallback(anyString(), anyString(), anyInt());
    }

    @Test
    void firstDeliverySemanticDigestUsesAnOrderIndependentSemanticEventSet() throws Exception {
        JsonNode first = objectMapper.readTree("""
                {"presentation":"first-delivery","events":[{
                  "event_id":"$state:matrix.internal",
                  "room_id":"!room:matrix.internal",
                  "sender":"@_weave_sender:matrix.internal",
                  "type":"m.room.create",
                  "content":{"creator":"@_weave_sender:matrix.internal"},
                  "origin_server_ts":123,
                  "age":12,
                  "redacted_because":{"event_id":"$redaction:matrix.internal","age":7,"unsigned":{"age":7}},
                  "unsigned":{"age":12,"stable":"kept","redacted_because":{"event_id":"$redaction:matrix.internal","age":7,"unsigned":{"age":7}}}
                },{
                  "event_id":"$name:matrix.internal",
                  "room_id":"!room:matrix.internal",
                  "sender":"@_weave_sender:matrix.internal",
                  "type":"m.room.name",
                  "state_key":"",
                  "content":{"name":"Collaboration"}
                }]}
                """);
        JsonNode retried = objectMapper.readTree("""
                {"presentation":"retry-delivery","events":[{
                  "unsigned":{"age":42,"presentation_only":"changed"},
                  "origin_server_ts":999,
                  "content":{"name":"Collaboration"},
                  "state_key":"",
                  "sender":"@_weave_sender:matrix.internal",
                  "room_id":"!room:matrix.internal",
                  "type":"m.room.name",
                  "event_id":"$name:matrix.internal"
                },{
                  "unsigned":{"stable":"kept","age":98765,"redacted_because":{"unsigned":{"age":87654},"age":87654,"event_id":"$redaction:matrix.internal"}},
                  "age":98765,
                  "redacted_because":{"unsigned":{"age":87654},"age":87654,"event_id":"$redaction:matrix.internal"},
                  "origin_server_ts":456,
                  "content":{"creator":"@_weave_sender:matrix.internal"},
                  "type":"m.room.create",
                  "sender":"@_weave_sender:matrix.internal",
                  "room_id":"!room:matrix.internal",
                  "event_id":"$state:matrix.internal"
                }]}
                """);
        JsonNode changed = retried.deepCopy();
        ((tools.jackson.databind.node.ObjectNode) changed.path("events").get(1).path("content"))
                .put("creator", "@_weave_other:matrix.internal");

        assertThat(MatrixApplicationServiceController.semanticPayloadDigest(first))
                .isEqualTo(MatrixApplicationServiceController.semanticPayloadDigest(retried))
                .isNotEqualTo(MatrixApplicationServiceController.semanticPayloadDigest(changed));
        assertThat(first.path("events").get(0).path("age").asInt()).isEqualTo(12);
        assertThat(first.path("events").get(0).path("unsigned").path("age").asInt()).isEqualTo(12);
    }

    @Test
    void capturesFirstSuccessfullyProcessedEncryptedCallbackForRealReplay() {
        when(provider.providerKey()).thenReturn("matrix-synapse");
        when(store.beginCallback(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(CanonicalChatStore.CallbackStart.NEW);
        when(store.recordCallbackEvent(anyString(), any()))
                .thenReturn(new CanonicalChatStore.CallbackEventResult("acknowledged-echo", "hash"));
        ChatE2eCallbackReplayTap tap = new ChatE2eCallbackReplayTap(new ChatE2eProofProperties(
                true, "/private/proof-token", "isolated-run-1234", "isolated"));
        MatrixApplicationServiceController controller = controller(65_536, tap);
        String payload = """
                {"events":[{
                  "event_id":"$opaque:matrix.internal",
                  "room_id":"!opaque:matrix.internal",
                  "sender":"@_weave_opaque:matrix.internal",
                  "type":"m.room.encrypted",
                  "origin_server_ts":1,
                  "content":{
                    "algorithm":"m.megolm.v1.aes-sha2",
                    "ciphertext":"opaque-ciphertext",
                    "sender_key":"curve25519:opaque",
                    "session_id":"opaque-session",
                    "device_id":"OPAQUEDEVICE"
                  }
                }]}
                """;

        var response = controller.transaction("hs-real-encrypted-1", request(payload));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ChatE2eCallbackReplayTap.CapturedCallback captured = tap.captured().orElseThrow();
        assertThat(captured.transactionId()).isEqualTo("hs-real-encrypted-1");
        assertThat(new String(captured.payload(), StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void oversizedCallbackFailsBeforeJsonOrPersistenceProcessing() {
        when(provider.providerKey()).thenReturn("matrix-synapse");
        when(resolver.synapseAdapter()).thenReturn(adapter);
        MatrixApplicationServiceController controller = controller(65_536);
        MockHttpServletRequest request = request("{\"events\":[],\"padding\":\"" + "x".repeat(65_536) + "\"}");

        var response = controller.transaction("hs-transaction-oversized", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).containsEntry("errcode", "M_TOO_LARGE");
        verify(store, never()).beginCallback(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void callbackPreservesStateKeyPresenceForStateClassification() {
        when(provider.providerKey()).thenReturn("matrix-synapse");
        when(store.beginCallback(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(CanonicalChatStore.CallbackStart.NEW);
        when(store.recordCallbackEvent(anyString(), any()))
                .thenReturn(new CanonicalChatStore.CallbackEventResult("ignored", "hash"));
        MatrixApplicationServiceController controller = controller(65_536);

        var response = controller.transaction("hs-state-1", request("""
                {"events":[{
                  "event_id":"$state:matrix.internal",
                  "room_id":"!room:matrix.internal",
                  "sender":"@_weave_sender:matrix.internal",
                  "type":"m.room.encryption",
                  "state_key":"",
                  "origin_server_ts":1,
                  "content":{"algorithm":"m.megolm.v1.aes-sha2"}
                }]}
                """));

        ArgumentCaptor<CanonicalChatStore.ProviderCallbackEvent> event =
                ArgumentCaptor.forClass(CanonicalChatStore.ProviderCallbackEvent.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(store).recordCallbackEvent(anyString(), event.capture());
        assertThat(event.getValue().stateKey()).isEmpty();
        assertThat(event.getValue().providerRedactsRef()).isNull();
    }

    @Test
    void callbackMarksHomeserverRedactedEncryptedProjectionWithoutCopyingRedactionBody() {
        when(provider.providerKey()).thenReturn("matrix-synapse");
        when(store.beginCallback(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(CanonicalChatStore.CallbackStart.NEW);
        when(store.recordCallbackEvent(anyString(), any()))
                .thenReturn(new CanonicalChatStore.CallbackEventResult("acknowledged-redacted-projection", "hash"));
        MatrixApplicationServiceController controller = controller(65_536);

        var response = controller.transaction("hs-redacted-encrypted", request("""
                {"events":[{
                  "event_id":"$encrypted:matrix.internal",
                  "room_id":"!room:matrix.internal",
                  "sender":"@_weave_sender:matrix.internal",
                  "type":"m.room.encrypted",
                  "origin_server_ts":1,
                  "unsigned":{"redacted_because":{"type":"m.room.redaction","content":{"reason":"private"}}},
                  "content":{}
                }]}
                """));

        ArgumentCaptor<CanonicalChatStore.ProviderCallbackEvent> event =
                ArgumentCaptor.forClass(CanonicalChatStore.ProviderCallbackEvent.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(store).recordCallbackEvent(anyString(), event.capture());
        assertThat(event.getValue().providerRedacted()).isTrue();
        assertThat(event.getValue().content()).isEmpty();
    }

    @Test
    void callbackNormalizesRoomV11ContentRedactionTarget() {
        when(provider.providerKey()).thenReturn("matrix-synapse");
        when(store.beginCallback(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(CanonicalChatStore.CallbackStart.NEW);
        when(store.recordCallbackEvent(anyString(), any()))
                .thenReturn(new CanonicalChatStore.CallbackEventResult("acknowledged-redaction-echo", "hash"));
        MatrixApplicationServiceController controller = controller(65_536);

        var response = controller.transaction("hs-redaction-v11", request("""
                {"events":[{
                  "event_id":"$redaction:matrix.internal",
                  "room_id":"!room:matrix.internal",
                  "sender":"@_weave_sender:matrix.internal",
                  "type":"m.room.redaction",
                  "origin_server_ts":1,
                  "content":{"redacts":"$target:matrix.internal"}
                }]}
                """));

        ArgumentCaptor<CanonicalChatStore.ProviderCallbackEvent> event =
                ArgumentCaptor.forClass(CanonicalChatStore.ProviderCallbackEvent.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(store).recordCallbackEvent(anyString(), event.capture());
        assertThat(event.getValue().providerRedactsRef()).isEqualTo("$target:matrix.internal");
        assertThat(event.getValue().content()).isEmpty();
        assertThat(event.getValue().stateKey()).isNull();
    }

    @Test
    void callbackAcceptsCompatibleTopLevelRedactionAndRejectsConflictingTargets() {
        when(provider.providerKey()).thenReturn("matrix-synapse");
        when(store.beginCallback(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(CanonicalChatStore.CallbackStart.NEW);
        MatrixApplicationServiceController controller = controller(65_536);

        var response = controller.transaction("hs-redaction-conflict", request("""
                {"events":[{
                  "event_id":"$redaction:matrix.internal",
                  "room_id":"!room:matrix.internal",
                  "sender":"@_weave_sender:matrix.internal",
                  "type":"m.room.redaction",
                  "redacts":"$top-level:matrix.internal",
                  "origin_server_ts":1,
                  "content":{"redacts":"$content:matrix.internal"}
                }]}
                """));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(store, never()).recordCallbackEvent(anyString(), any());
        verify(store).recordMalformedCallbackEvent(
                eq("matrix-synapse"),
                eq("hs-redaction-conflict"),
                anyString(),
                eq("callback-event-malformed"));
    }

    private MatrixApplicationServiceController controller(int maximumBytes) {
        return controller(maximumBytes, null);
    }

    private MatrixApplicationServiceController controller(
            int maximumBytes,
            ChatE2eCallbackReplayTap callbackReplayTap) {
        ChatRuntimeProperties properties = new ChatRuntimeProperties(
                "matrix-synapse",
                new ChatRuntimeProperties.Storage("jpa"),
                new ChatRuntimeProperties.Matrix(
                        "http://matrix.internal:8008",
                        "matrix.internal",
                        "weave-chat-synapse",
                        "_weave_",
                        "/private/as-token",
                        "/private/hs-token",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(60),
                        maximumBytes,
                        100));
        return new MatrixApplicationServiceController(
                store, provider, properties, objectMapper, callbackReplayTap);
    }

    private MockHttpServletRequest request(String json) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("PUT");
        request.setContentType("application/json");
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
