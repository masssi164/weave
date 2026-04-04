import 'package:matrix/matrix.dart' as sdk;
import 'package:weave/features/chat/data/services/matrix_client_factory.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart'; // needed for unsupportedPlatform

class _WebMatrixClientFactory implements MatrixClientFactory {
  const _WebMatrixClientFactory();

  @override
  Stream<sdk.Client> get clientCreated => const Stream.empty();

  @override
  Stream<void> get sessionCleared => const Stream.empty();

  @override
  sdk.Client? get currentClient => null;

  @override
  Future<sdk.Client> getClient() async {
    throw const ChatFailure.unsupportedPlatform(
      'Matrix chat is not supported on the web.',
    );
  }

  @override
  Future<sdk.Client> getClientForHomeserver(Uri homeserver) async {
    throw const ChatFailure.unsupportedPlatform(
      'Matrix chat is not supported on the web.',
    );
  }

  @override
  Future<void> clearClient(sdk.Client client) async {}

  @override
  Future<void> dispose() async {}
}

// ignore: unused_element
MatrixClientFactory createMatrixClientFactory() {
  return const _WebMatrixClientFactory();
}
