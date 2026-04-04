import 'package:matrix/matrix.dart' as sdk;
import 'package:riverpod/riverpod.dart';
import 'package:weave/features/chat/data/services/matrix_client_factory.dart';
import 'package:weave/features/chat/data/services/matrix_error_mapper.dart';
import 'package:weave/features/chat/data/services/matrix_service_types.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

/// Loads room lists and maps them to [MatrixRoomSnapshot] value objects.
abstract interface class MatrixConversationService {
  /// Performs a one-shot sync and returns a snapshot of all rooms visible to
  /// the currently signed-in account for [homeserver].
  ///
  /// Throws [ChatFailure.sessionRequired] when not signed in.
  Future<List<MatrixRoomSnapshot>> loadConversations({required Uri homeserver});
}

class SdkMatrixConversationService implements MatrixConversationService {
  const SdkMatrixConversationService({required MatrixClientFactory factory})
    : _factory = factory;

  final MatrixClientFactory _factory;

  @override
  Future<List<MatrixRoomSnapshot>> loadConversations({
    required Uri homeserver,
  }) async {
    final client = await _factory.getClientForHomeserver(homeserver);

    if (!client.isLogged()) {
      throw const ChatFailure.sessionRequired(
        'Connect Weave to your Matrix homeserver to load conversations.',
      );
    }

    try {
      await client.oneShotSync();
      return client.rooms.map(_mapRoom).toList(growable: false);
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback: 'Unable to load conversations from the Matrix homeserver.',
      );
    }
  }

  MatrixRoomSnapshot _mapRoom(sdk.Room room) {
    final lastEvent = room.lastEvent;
    final previewType = _previewTypeForRoom(room, lastEvent);
    final previewText = switch (previewType) {
      MatrixRoomPreviewType.text => lastEvent?.plaintextBody.trim(),
      _ => null,
    };

    return MatrixRoomSnapshot(
      id: room.id,
      title: room.getLocalizedDisplayname(),
      previewType: previewType,
      previewText: previewText,
      lastActivityAt: lastEvent?.originServerTs.toLocal(),
      unreadCount: room.notificationCount,
      isInvite: room.membership == sdk.Membership.invite,
      isDirectMessage: room.isDirectChat,
    );
  }

  MatrixRoomPreviewType _previewTypeForRoom(sdk.Room room, sdk.Event? event) {
    if (event == null) {
      return MatrixRoomPreviewType.none;
    }

    if (room.encrypted || event.type == sdk.EventTypes.Encrypted) {
      return MatrixRoomPreviewType.encrypted;
    }

    final preview = event.plaintextBody.trim();
    if (preview.isEmpty ||
        preview.startsWith('Unknown message format of type')) {
      return MatrixRoomPreviewType.unsupported;
    }

    return MatrixRoomPreviewType.text;
  }
}

final matrixConversationServiceProvider = Provider<MatrixConversationService>((
  ref,
) {
  return SdkMatrixConversationService(
    factory: ref.watch(matrixClientFactoryProvider),
  );
});
