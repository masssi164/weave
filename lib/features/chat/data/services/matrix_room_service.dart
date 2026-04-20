import 'package:matrix/matrix.dart' as sdk;
import 'package:riverpod/riverpod.dart';
import 'package:weave/features/chat/data/services/matrix_client_factory.dart';
import 'package:weave/features/chat/data/services/matrix_error_mapper.dart';
import 'package:weave/features/chat/data/services/matrix_service_types.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

abstract interface class MatrixRoomService {
  Future<MatrixRoomTimelineSnapshot> loadRoomTimeline({
    required Uri homeserver,
    required String roomId,
  });

  Future<void> sendMessage({
    required Uri homeserver,
    required String roomId,
    required String message,
  });

  Future<void> markRoomRead({required Uri homeserver, required String roomId});
}

class SdkMatrixRoomService implements MatrixRoomService {
  const SdkMatrixRoomService({required MatrixClientFactory factory})
    : _factory = factory;

  final MatrixClientFactory _factory;

  @override
  Future<MatrixRoomTimelineSnapshot> loadRoomTimeline({
    required Uri homeserver,
    required String roomId,
  }) async {
    final client = await _factory.getClientForHomeserver(homeserver);
    final room = _requireRoom(client, roomId);

    try {
      await client.oneShotSync();
      final timeline = await room.getTimeline(limit: 50);
      final messages = timeline.events
          .where((event) => event.relationshipEventId == null)
          .toList(growable: false)
          .reversed
          .map((event) => _mapMessage(event, timeline, client))
          .toList(growable: false);
      timeline.cancelSubscriptions();

      return MatrixRoomTimelineSnapshot(
        roomId: room.id,
        roomTitle: room.getLocalizedDisplayname(),
        isInvite: room.membership == sdk.Membership.invite,
        canSendMessages: room.membership == sdk.Membership.join,
        messages: messages,
      );
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback: 'Unable to load this Matrix room right now.',
      );
    }
  }

  @override
  Future<void> sendMessage({
    required Uri homeserver,
    required String roomId,
    required String message,
  }) async {
    final client = await _factory.getClientForHomeserver(homeserver);
    final room = _requireRoom(client, roomId);

    try {
      await room.sendTextEvent(message.trim());
      await client.oneShotSync();
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback: 'Unable to send that Matrix message right now.',
      );
    }
  }

  @override
  Future<void> markRoomRead({
    required Uri homeserver,
    required String roomId,
  }) async {
    final client = await _factory.getClientForHomeserver(homeserver);
    final room = _requireRoom(client, roomId);

    try {
      await room.setReadMarker(
        room.lastEvent?.eventId,
        mRead: room.lastEvent?.eventId,
      );
      await client.oneShotSync();
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback: 'Unable to mark this Matrix room as read right now.',
      );
    }
  }

  sdk.Room _requireRoom(sdk.Client client, String roomId) {
    if (!client.isLogged()) {
      throw const ChatFailure.sessionRequired(
        'Connect Weave to your Matrix homeserver before opening a conversation.',
      );
    }

    final room = client.getRoomById(roomId);
    if (room == null) {
      throw const ChatFailure.protocol(
        'That Matrix room is no longer available on this device.',
      );
    }

    return room;
  }

  MatrixTimelineMessageSnapshot _mapMessage(
    sdk.Event event,
    sdk.Timeline timeline,
    sdk.Client client,
  ) {
    final displayEvent = event.getDisplayEvent(timeline);
    final body = displayEvent.body.trim();
    final contentType = switch (event.type) {
      sdk.EventTypes.Encrypted => MatrixMessageContentType.encrypted,
      _
          when body.isEmpty ||
              body.startsWith('Unknown message format of type') =>
        MatrixMessageContentType.unsupported,
      _ => MatrixMessageContentType.text,
    };

    return MatrixTimelineMessageSnapshot(
      id: event.eventId,
      senderId: event.senderId,
      senderDisplayName: event.sender.calcDisplayname(),
      sentAt: event.originServerTs.toLocal(),
      isMine: event.senderId == client.userID,
      deliveryState: switch (event.status) {
        sdk.EventStatus.sending => MatrixMessageDeliveryState.sending,
        sdk.EventStatus.error => MatrixMessageDeliveryState.failed,
        sdk.EventStatus.sent ||
        sdk.EventStatus.synced => MatrixMessageDeliveryState.sent,
      },
      contentType: contentType,
      text: contentType == MatrixMessageContentType.text ? body : null,
    );
  }
}

final matrixRoomServiceProvider = Provider<MatrixRoomService>((ref) {
  return SdkMatrixRoomService(factory: ref.watch(matrixClientFactoryProvider));
});
