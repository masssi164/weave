import 'package:matrix/matrix.dart' as sdk;
import 'package:riverpod/riverpod.dart';

import 'matrix_client_factory_io.dart'
    if (dart.library.js_interop) 'matrix_client_factory_web.dart'
    as impl;

/// Owns the lifecycle of the shared Matrix SDK [sdk.Client].
///
/// All internal Matrix services receive this factory as a dependency instead of
/// constructing or holding the SDK client directly. This keeps client creation,
/// session clearing, and cross-service coordination in one place.
abstract interface class MatrixClientFactory {
  /// Fires once each time a new SDK client has been created and fully
  /// initialized. Services that need to bind SDK-level listeners
  /// (e.g. `onKeyVerificationRequest`) should subscribe here.
  Stream<sdk.Client> get clientCreated;

  /// Fires whenever the stored client session is explicitly cleared –
  /// either by [clearClient] or by a homeserver change detected inside
  /// [getClientForHomeserver]. Services should reset any session-scoped
  /// in-memory state when this fires.
  Stream<void> get sessionCleared;

  /// Returns the already-created SDK client without creating a new one,
  /// or `null` if the client has not been initialized yet.
  ///
  /// Used by services that need to bind listeners eagerly at construction
  /// time without triggering client creation themselves.
  sdk.Client? get currentClient;

  /// Returns the shared, initialized SDK client, creating it on first call.
  ///
  /// Throws [ChatFailure.unsupportedPlatform] on unsupported platforms.
  Future<sdk.Client> getClient();

  /// Returns the shared SDK client, clearing the session first if the
  /// [homeserver] differs from the one used in the previous call.
  ///
  /// Throws [ChatFailure.unsupportedPlatform] on unsupported platforms.
  Future<sdk.Client> getClientForHomeserver(Uri homeserver);

  /// Clears the SDK client's persisted session data and fires [sessionCleared].
  Future<void> clearClient(sdk.Client client);

  Future<void> dispose();
}

final matrixClientFactoryProvider = Provider<MatrixClientFactory>((ref) {
  final factory = impl.createMatrixClientFactory();
  ref.onDispose(factory.dispose);
  return factory;
});
