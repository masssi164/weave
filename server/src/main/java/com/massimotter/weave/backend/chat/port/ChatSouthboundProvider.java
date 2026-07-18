package com.massimotter.weave.backend.chat.port;

import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.portability.ProviderCapabilityProbeResult;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.util.List;

/** Narrow provider anti-corruption port. All identifiers are private adapter values. */
public interface ChatSouthboundProvider {

    String providerKey();

    boolean configured();

    ProviderReadiness readiness();

    default ProviderCapabilityProbeResult healthProbe() {
        ProviderReadiness observation = readiness();
        if (observation.available()) {
            return ProviderCapabilityProbeResult.available(observation.supportSafeCode());
        }
        return observation.supportSafeCode().contains("unavailable")
                        || observation.supportSafeCode().contains("interrupted")
                ? ProviderCapabilityProbeResult.unavailable(observation.supportSafeCode())
                : ProviderCapabilityProbeResult.degraded(observation.supportSafeCode());
    }

    ProviderAck ensureVirtualUser(String providerUserRef);

    ProviderAck createRoom(
            String providerActorRef,
            String providerAliasIntent,
            String title,
            List<String> invitedProviderActors,
            String initialEncryptionAlgorithm);

    default ProviderAck createRoom(
            String providerActorRef,
            String providerAliasIntent,
            String title,
            List<String> invitedProviderActors) {
        return createRoom(providerActorRef, providerAliasIntent, title, invitedProviderActors, null);
    }

    ProviderAck joinRoom(String providerActorRef, String providerRoomRef);

    ProviderAck leaveRoom(String providerActorRef, String providerRoomRef);

    ProviderAck enableEncryption(
            String providerActorRef,
            String providerRoomRef,
            String providerTransactionId,
            String algorithm);

    ProviderAck sendEvent(
            String providerActorRef,
            String providerRoomRef,
            String providerTransactionId,
            ChatEventContent content,
            String providerRelationTargetRef);

    ProviderAck redactEvent(
            String providerActorRef,
            String providerRoomRef,
            String providerEventRef,
            String providerTransactionId);

    ProviderAck markRead(String providerActorRef, String providerRoomRef, String providerEventRef);

    void setTyping(String providerActorRef, String providerRoomRef, boolean typing, int timeoutMilliseconds);

    record ProviderAck(String providerRef, String sourceVersion) {
    }
}
