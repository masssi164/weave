package com.massimotter.weave.backend.chat.provider.synapse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import com.massimotter.weave.backend.portability.ProviderCapabilityState;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatrixSynapseChatSouthboundAdapterTest {

    private static final String AS_TOKEN = "as-token-value-0123456789";

    private final AtomicInteger probeCount = new AtomicInteger();
    private final AtomicBoolean throttled = new AtomicBoolean();
    private final AtomicBoolean holdProbe = new AtomicBoolean();
    private final CountDownLatch probeEntered = new CountDownLatch(1);
    private final CountDownLatch releaseProbe = new CountDownLatch(1);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-07-15T10:00:00Z"));
    private HttpServer server;
    private MatrixSynapseChatSouthboundAdapter adapter;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        ChatRuntimeProperties.Matrix properties = new ChatRuntimeProperties.Matrix(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "matrix.internal",
                "weave-chat-synapse",
                "_weave_",
                "/private/as-token",
                "/private/hs-token",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                1_048_576,
                100);
        assertThat(properties.readinessCacheTtl()).isEqualTo(Duration.ofSeconds(60));
        SynapseApplicationServiceClient client = new SynapseApplicationServiceClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                "matrix.internal",
                AS_TOKEN,
                Duration.ofSeconds(2),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                new ObjectMapper().findAndRegisterModules(),
                clock);
        adapter = new MatrixSynapseChatSouthboundAdapter(client, properties, clock);
    }

    @AfterEach
    void stopServer() {
        releaseProbe.countDown();
        server.stop(0);
    }

    @Test
    void concurrentReadinessRequestsUseOneAuthenticatedProbeAndTheCachedResult() throws Exception {
        holdProbe.set(true);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                results.add(executor.submit(() -> adapter.readiness().available()));
            }
            assertThat(probeEntered.await(2, TimeUnit.SECONDS)).isTrue();
            releaseProbe.countDown();

            for (Future<Boolean> result : results) {
                assertThat(result.get(2, TimeUnit.SECONDS)).isTrue();
            }
            assertThat(probeCount).hasValue(1);

            adapter.readiness();
            adapter.healthProbe();
            assertThat(probeCount).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void retryAfterSuppressesProbesAndRecoveryResetsFailureState() {
        assertThat(adapter.readiness().available()).isTrue();
        assertThat(probeCount).hasValue(1);

        throttled.set(true);
        clock.advance(Duration.ofSeconds(60));
        assertThat(adapter.readiness()).satisfies(readiness -> {
            assertThat(readiness.available()).isFalse();
            assertThat(readiness.supportSafeCode()).isEqualTo("chat-provider-throttled");
        });
        assertThat(adapter.supportSafeReadiness()).satisfies(observation -> {
            assertThat(observation.state()).isEqualTo("degraded");
            assertThat(observation.consecutiveFailures()).isEqualTo(1);
            assertThat(observation.nextProbeAt()).isEqualTo(clock.instant().plusSeconds(180));
        });
        assertThat(adapter.healthProbe()).satisfies(result -> {
            assertThat(result.state()).isEqualTo(ProviderCapabilityState.DEGRADED);
            assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(180));
        });
        assertThat(probeCount).hasValue(2);

        clock.advance(Duration.ofSeconds(179));
        adapter.readiness();
        assertThat(probeCount).hasValue(2);

        throttled.set(false);
        clock.advance(Duration.ofSeconds(1));
        assertThat(adapter.readiness().available()).isTrue();
        assertThat(adapter.supportSafeReadiness().consecutiveFailures()).isZero();
        assertThat(probeCount).hasValue(3);
    }

    @Test
    void providerThrottleSuppressesConcurrentOperationRetriesUntilRetryAfter() {
        throttled.set(true);
        ChatEventContent encrypted = ChatEventContent.encrypted(Map.of(
                "algorithm", "m.megolm.v1.aes-sha2",
                "ciphertext", "opaque-ciphertext",
                "sender_key", "curve25519:opaque",
                "session_id", "opaque-session",
                "device_id", "OPAQUEDEVICE"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.sendEvent(
                        "@_weave_author:matrix.internal",
                        "!opaque-room:matrix.internal",
                        "weave_opaque_txn",
                        encrypted,
                        null))
                .isInstanceOfSatisfying(SynapseProviderException.class, exception -> {
                    assertThat(exception.supportSafeCode()).isEqualTo("chat-provider-throttled");
                    assertThat(exception.retryAt()).isEqualTo(clock.instant().plusSeconds(180));
                });
        assertThat(probeCount).hasValue(1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.sendEvent(
                        "@_weave_author:matrix.internal",
                        "!opaque-room:matrix.internal",
                        "weave_opaque_txn",
                        encrypted,
                        null))
                .isInstanceOf(SynapseProviderException.class);
        assertThat(probeCount).hasValue(1);

        throttled.set(false);
        clock.advance(Duration.ofSeconds(180));
        assertThat(adapter.sendEvent(
                        "@_weave_author:matrix.internal",
                        "!opaque-room:matrix.internal",
                        "weave_opaque_txn",
                        encrypted,
                        null).providerRef())
                .isEqualTo("$opaque-event:matrix.internal");
        assertThat(probeCount).hasValue(2);
        assertThat(adapter.supportSafeReadiness().state()).isEqualTo("available");
    }

    private void handle(HttpExchange exchange) throws IOException {
        probeCount.incrementAndGet();
        probeEntered.countDown();
        if (holdProbe.get()) {
            try {
                if (!releaseProbe.await(2, TimeUnit.SECONDS)) {
                    throw new IOException("test probe release timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("test probe interrupted", exception);
            }
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/_matrix/client/v3/account/whoami")) {
            assertThat(exchange.getRequestURI().getRawQuery())
                    .contains("user_id=%40_weave_appservice%3Amatrix.internal");
        } else {
            assertThat(path).contains("/send/m.room.encrypted/");
        }
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer " + AS_TOKEN);

        int status = throttled.get() ? 429 : 200;
        String body = throttled.get()
                ? "{\"errcode\":\"M_LIMIT_EXCEEDED\",\"error\":\"unsafe provider body\"}"
                : path.equals("/_matrix/client/v3/account/whoami")
                        ? "{\"user_id\":\"@_weave_appservice:matrix.internal\"}"
                        : "{\"event_id\":\"$opaque-event:matrix.internal\"}";
        if (throttled.get()) {
            exchange.getResponseHeaders().add("Retry-After", "180");
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
