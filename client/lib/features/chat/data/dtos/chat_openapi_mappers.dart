import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;
import 'package:weave/integrations/weave_api/domain/entities/openapi_feature_adapter.dart';

const _chatFeatureKey = 'chat';

extension ChatReadinessOpenApiMapper on openapi.ChatReadiness {
  OpenApiFeatureReadiness toFeatureReadiness() {
    return OpenApiFeatureReadiness(
      featureKey: _chatFeatureKey,
      state: OpenApiFeatureCapabilityState.fromApi(memberState),
      memberImpact: _fallbackText(
        memberImpact,
        'Weave Chat readiness unknown.',
      ),
      supportSafe: supportSafe ?? true,
      diagnosticsRedacted: downstreamDiagnosticsExposedToMember != true,
    );
  }
}

extension ChatReadinessResponseOpenApiMapper on openapi.ChatReadinessResponse {
  OpenApiFeatureReadiness toFeatureReadiness() {
    return OpenApiFeatureReadiness(
      featureKey: _chatFeatureKey,
      state: OpenApiFeatureCapabilityState.fromApi(impactState),
      memberImpact: _fallbackText(
        memberImpact,
        'Weave Chat readiness unknown.',
      ),
      capabilities: grantedCapabilities
          .map(
            (capability) => OpenApiFeatureCapability(
              key: capability,
              state: OpenApiFeatureCapabilityState.available,
            ),
          )
          .toList(growable: false),
      diagnosticsRedacted: diagnosticsRedacted,
    );
  }
}

extension ChatConversationsOpenApiMapper on openapi.ChatConversationsResponse {
  OpenApiResourcePage<ChatConversation> toConversationPage() {
    return OpenApiResourcePage<ChatConversation>(
      featureKey: _chatFeatureKey,
      readiness: readiness.toFeatureReadiness(),
      resources: conversations
          .map((conversation) => conversation.toDomainConversation())
          .toList(growable: false),
    );
  }
}

extension ChatMessagesOpenApiMapper on openapi.ChatMessagesResponse {
  ChatRoomTimeline toRoomTimeline(String fallbackConversationId) {
    final roomId = _fallbackText(conversationId, fallbackConversationId);
    return ChatRoomTimeline(
      roomId: roomId,
      roomTitle: roomId,
      isInvite: false,
      canSendMessages: true,
      messages: messages
          .map((message) => message.toDomainMessage(roomId))
          .toList(growable: false),
    );
  }
}

extension ChatConversationResponseOpenApiMapper
    on openapi.ChatConversationResponse {
  ChatConversation toDomainConversation() {
    final conversationKind = _fallbackText(kind, 'channel');
    return ChatConversation(
      id: _requiredText(id, 'id'),
      title: _requiredText(title, 'title'),
      previewType: ChatConversationPreviewType.text,
      previewText: 'Weave Chat conversation',
      lastActivityAt: _readDateTime(lastMessageAt),
      unreadCount: 0,
      isInvite: false,
      isDirectMessage: conversationKind == 'direct',
      isAiChat: conversationKind == 'ai',
    );
  }
}

extension ChatMessageResponseOpenApiMapper on openapi.ChatMessageResponse {
  ChatMessage toDomainMessage(String fallbackConversationId) {
    final redacted = encryptedProviderContentRedacted == true;
    final sender = _requiredText(senderRef, 'senderRef');
    return ChatMessage(
      id: _requiredText(id, 'id'),
      senderId: sender,
      senderDisplayName: sender,
      sentAt: _requiredDateTime(sentAt, 'sentAt'),
      isMine: isMine == true,
      deliveryState: ChatMessageDeliveryState.sent,
      contentType: redacted
          ? ChatMessageContentType.encrypted
          : ChatMessageContentType.text,
      text: text,
    );
  }
}

String _requiredText(String? value, String key) {
  final trimmed = value?.trim();
  if (trimmed != null && trimmed.isNotEmpty) {
    return trimmed;
  }
  throw ChatFailure.configuration(
    'The Weave Chat facade returned an invalid "$key" value.',
  );
}

String _fallbackText(String? value, String fallback) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? fallback : trimmed;
}

DateTime? _readDateTime(String? value) {
  if (value == null || value.trim().isEmpty) {
    return null;
  }
  return DateTime.tryParse(value)?.toLocal();
}

DateTime _requiredDateTime(String value, String key) {
  final parsed = DateTime.tryParse(value);
  if (parsed == null) {
    throw ChatFailure.configuration(
      'The Weave Chat facade returned an invalid "$key" value.',
    );
  }
  return parsed.toLocal();
}
