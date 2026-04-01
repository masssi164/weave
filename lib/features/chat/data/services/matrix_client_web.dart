import 'package:weave/features/chat/data/services/matrix_auth_browser.dart';
import 'package:weave/features/chat/data/services/matrix_client_interface.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

class _WebMatrixClientStub implements MatrixClient {
  const _WebMatrixClientStub();

  @override
  Future<List<MatrixRoomSnapshot>> loadConversations({
    required Uri homeserver,
  }) async {
    throw const ChatFailure.unsupportedPlatform(
      'Matrix chat is not supported on the web.',
    );
  }

  @override
  Future<void> connect({required Uri homeserver}) async {
    throw const ChatFailure.unsupportedPlatform(
      'Matrix chat is not supported on the web.',
    );
  }

  @override
  Future<void> signOut() async {}

  @override
  Future<void> clearSession() async {}
}

MatrixClient createMatrixClient({required MatrixAuthBrowser authBrowser}) {
  return const _WebMatrixClientStub();
}
