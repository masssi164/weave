import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_vodozemac/flutter_vodozemac.dart' as vod;
import 'package:matrix/encryption.dart' as crypto;
import 'package:matrix/matrix.dart' as sdk;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart' as sqflite;
import 'package:weave/features/chat/data/services/matrix_client_factory.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

typedef MatrixSdkClientFactory =
    sdk.Client Function({required sdk.DatabaseApi database});
typedef MatrixDatabaseOpener = Future<sqflite.Database> Function(String path);
typedef ApplicationSupportDirectoryProvider = Future<Directory> Function();

class SdkMatrixClientFactory implements MatrixClientFactory {
  SdkMatrixClientFactory({
    ApplicationSupportDirectoryProvider? appSupportDirectoryProvider,
    MatrixDatabaseOpener? databaseOpener,
    MatrixSdkClientFactory? clientFactory,
  }) : _appSupportDirectoryProvider =
           appSupportDirectoryProvider ?? getApplicationSupportDirectory,
       _databaseOpener = databaseOpener ?? sqflite.openDatabase,
       _clientFactory = clientFactory ?? _defaultSdkClientFactory;

  final ApplicationSupportDirectoryProvider _appSupportDirectoryProvider;
  final MatrixDatabaseOpener _databaseOpener;
  final MatrixSdkClientFactory _clientFactory;

  sdk.Client? _client;
  Future<sdk.Client>? _clientFuture;

  final StreamController<sdk.Client> _clientCreatedController =
      StreamController<sdk.Client>.broadcast();
  final StreamController<void> _sessionClearedController =
      StreamController<void>.broadcast();

  static Future<void>? _vodozemacInitFuture;

  static sdk.Client _defaultSdkClientFactory({
    required sdk.DatabaseApi database,
  }) {
    return sdk.Client(
      'weave_matrix_chat',
      database: database,
      verificationMethods: const <crypto.KeyVerificationMethod>{
        crypto.KeyVerificationMethod.emoji,
        crypto.KeyVerificationMethod.numbers,
      },
      nativeImplementations: sdk.NativeImplementationsIsolate(
        compute,
        vodozemacInit: _ensureVodozemacInitialized,
      ),
      supportedLoginTypes: {
        sdk.AuthenticationTypes.password,
        sdk.AuthenticationTypes.sso,
        sdk.AuthenticationTypes.oauth2,
      },
      onSoftLogout: (client) => client.refreshAccessToken(),
    );
  }

  static Future<void> _ensureVodozemacInitialized() {
    return _vodozemacInitFuture ??= vod.init();
  }

  @override
  Stream<sdk.Client> get clientCreated => _clientCreatedController.stream;

  @override
  Stream<void> get sessionCleared => _sessionClearedController.stream;

  @override
  sdk.Client? get currentClient => _client;

  @override
  Future<sdk.Client> getClient() => _ensureClient();

  @override
  Future<sdk.Client> getClientForHomeserver(Uri homeserver) async {
    final client = await _ensureClient();
    await _clearSessionIfHomeserverChanged(client, _normalizeUri(homeserver));
    return client;
  }

  @override
  Future<void> clearClient(sdk.Client client) async {
    await client.clear();
    if (!_sessionClearedController.isClosed) {
      _sessionClearedController.add(null);
    }
  }

  @override
  Future<void> dispose() async {
    if (!_clientCreatedController.isClosed) {
      await _clientCreatedController.close();
    }
    if (!_sessionClearedController.isClosed) {
      await _sessionClearedController.close();
    }
  }

  Future<sdk.Client> _ensureClient() async {
    final existingClient = _client;
    if (existingClient != null) {
      return existingClient;
    }

    final pendingClient = _clientFuture;
    if (pendingClient != null) {
      return pendingClient;
    }

    final future = _createClient();
    _clientFuture = future;

    try {
      return await future;
    } catch (error) {
      _clientFuture = null;
      _client = null;
      rethrow;
    }
  }

  Future<sdk.Client> _createClient() async {
    if (!_isInteractivePlatform) {
      throw const ChatFailure.unsupportedPlatform(
        'Matrix chat is currently supported on Android, iOS, and macOS.',
      );
    }

    try {
      await _ensureVodozemacInitialized();
      final supportDirectory = await _appSupportDirectoryProvider();
      final databasePath = supportDirectory.uri
          .resolve('weave_matrix_chat.sqlite')
          .toFilePath();
      final rawDatabase = await _databaseOpener(databasePath);
      final database = await sdk.MatrixSdkDatabase.init(
        'weave_matrix_chat',
        database: rawDatabase,
        fileStorageLocation: supportDirectory.uri.resolve(
          'weave_matrix_media/',
        ),
      );
      final client = _clientFactory(database: database);

      await client.init();
      _client = client;
      _clientFuture = null;
      if (!_clientCreatedController.isClosed) {
        _clientCreatedController.add(client);
      }
      return client;
    } catch (error) {
      throw mapMatrixFactoryError(
        error,
        fallback: 'Unable to initialize the Matrix chat client.',
      );
    }
  }

  Future<void> _clearSessionIfHomeserverChanged(
    sdk.Client client,
    Uri homeserver,
  ) async {
    final activeHomeserver = client.homeserver;
    if (activeHomeserver == null || !client.isLogged()) {
      return;
    }

    if (_normalizeUri(activeHomeserver).toString() == homeserver.toString()) {
      return;
    }

    await clearClient(client);
  }

  Uri _normalizeUri(Uri uri) {
    final normalized = uri.toString().trim();
    if (!normalized.endsWith('/')) {
      return uri;
    }
    return Uri.parse(normalized.substring(0, normalized.length - 1));
  }

  bool get _isInteractivePlatform {
    if (kIsWeb) {
      return false;
    }
    return defaultTargetPlatform == TargetPlatform.android ||
        defaultTargetPlatform == TargetPlatform.iOS ||
        defaultTargetPlatform == TargetPlatform.macOS;
  }
}

/// Maps low-level factory errors (SDK, SQLite, I/O) into [ChatFailure].
///
/// Internal to the factory – services use [mapMatrixServiceError] instead.
ChatFailure mapMatrixFactoryError(Object error, {required String fallback}) {
  if (error is ChatFailure) return error;

  if (error is sdk.MatrixException) {
    final message = error.errorMessage.trim();
    return ChatFailure.protocol(
      message.isEmpty ? fallback : message,
      cause: error,
    );
  }

  if (error is IOException || error is sqflite.DatabaseException) {
    return ChatFailure.storage(fallback, cause: error);
  }

  return ChatFailure.unknown(fallback, cause: error);
}

// ignore: unused_element
MatrixClientFactory createMatrixClientFactory() {
  return SdkMatrixClientFactory();
}
