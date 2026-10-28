package com.massimotter.weave.backend.chat.port;

import java.util.List;
import java.util.Map;

/**
 * Supported Matrix wire projection operations and immutable public-envelope inputs.
 * Implementations own JNI/Ruma; authorization, canonical state and client cryptographic secrets stay outside.
 */
public interface MatrixProtocolCodec {
    Map<String, Object> versions();

    Map<String, Object> whoami(String subject, String deviceId);

    Map<String, Object> sync(
            String subject,
            String cursor,
            String since,
            List<CanonicalConversation> conversations,
            Map<String, Map<String, Object>> accountData,
            MatrixSyncCrypto crypto,
            List<String> leftRooms);

    void validateSyncToken(String since);

    String decodeSyncCursor(String since);

    Map<String, Object> joinedRooms(List<CanonicalConversation> conversations);

    Map<String, Object> messages(String cursor, String from, CanonicalConversation conversation);

    Map<String, Object> members(CanonicalConversation conversation);

    Map<String, Object> parseObject(String requestJson);

    ParsedEventContent parseEvent(String eventType, String requestJson);

    Map<String, Object> sendResponse(String messageId);

    String decodeRoomId(String matrixRoomId);

    String decodeEventId(String matrixEventId);

    String roomId(String conversationId);

    String userId(String memberRef);

    Map<String, Object> error(String errcode, String message);

    String serverName();

    record CanonicalConversation(
            String conversationId,
            String title,
            long updatedAtEpochMillis,
            int unreadCount,
            String encryptionAlgorithm,
            List<CanonicalMembership> memberships,
            List<CanonicalMessage> messages,
            boolean timelineLimited,
            String timelineBeforeCursor,
            String inviterRef) {
        public CanonicalConversation(String conversationId, String title, long updatedAtEpochMillis,
                int unreadCount, String encryptionAlgorithm, List<CanonicalMembership> memberships,
                List<CanonicalMessage> messages, boolean timelineLimited, String timelineBeforeCursor) {
            this(conversationId, title, updatedAtEpochMillis, unreadCount, encryptionAlgorithm,
                    memberships, messages, timelineLimited, timelineBeforeCursor, null);
        }
        public CanonicalConversation(String conversationId, String title, long updatedAtEpochMillis,
                int unreadCount, String encryptionAlgorithm, List<CanonicalMembership> memberships,
                List<CanonicalMessage> messages) {
            this(conversationId, title, updatedAtEpochMillis, unreadCount, encryptionAlgorithm,
                    memberships, messages, false, null);
        }
        public CanonicalConversation(String conversationId, String title, long updatedAtEpochMillis,
                int unreadCount, String encryptionAlgorithm, List<CanonicalMembership> memberships,
                List<CanonicalMessage> messages, boolean timelineLimited) {
            this(conversationId, title, updatedAtEpochMillis, unreadCount, encryptionAlgorithm,
                    memberships, messages, timelineLimited, null);
        }
    }

    record CanonicalMembership(String memberRef, String state, String displayName) {}

    record CanonicalMessage(
            String messageId,
            String senderRef,
            long sentAtEpochMillis,
            String kind,
            String messageType,
            String body,
            String format,
            String formattedBody,
            String relationKind,
            String relationTargetEventId,
            String replyToEventId,
            String reactionKey,
            Map<String, Object> presentationExtensions,
            String deliveryState,
            Map<String, Object> encryptedContent,
            boolean redacted) {
        public CanonicalMessage {
            presentationExtensions = presentationExtensions == null
                    ? Map.of()
                    : Map.copyOf(presentationExtensions);
            encryptedContent = encryptedContent == null ? null : Map.copyOf(encryptedContent);
        }
    }

    record ParsedEventContent(
            String kind,
            String body,
            String messageType,
            String format,
            String formattedBody,
            String relationKind,
            String relationTargetEventId,
            String replyToEventId,
            String reactionKey,
            Map<String, Object> encryptedContent,
            Map<String, Object> presentationExtensions) {}

    record MatrixSyncCrypto(
            List<Map<String, Object>> toDeviceEvents,
            List<String> deviceListsChanged,
            List<String> deviceListsLeft,
            Map<String, Long> oneTimeKeyCounts,
            List<String> unusedFallbackKeyTypes,
            long nextSequence,
            long deviceListSequence) {
        public MatrixSyncCrypto(
                List<Map<String, Object>> toDeviceEvents,
                List<String> deviceListsChanged,
                List<String> deviceListsLeft,
                Map<String, Long> oneTimeKeyCounts,
                List<String> unusedFallbackKeyTypes,
                long nextSequence) {
            this(toDeviceEvents, deviceListsChanged, deviceListsLeft, oneTimeKeyCounts, unusedFallbackKeyTypes, nextSequence, nextSequence);
        }

        public static MatrixSyncCrypto empty() {
            return new MatrixSyncCrypto(List.of(), List.of(), List.of(), Map.of(), List.of(), 0, 0);
        }
    }
}
