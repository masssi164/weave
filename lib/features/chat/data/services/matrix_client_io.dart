import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:matrix/matrix.dart' as sdk;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart' as sqflite;
import 'package:weave/features/chat/data/services/matrix_auth_browser.dart';
import 'package:weave/features/chat/data/services/matrix_client_interface.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

typedef MatrixSdkClientFactory =
    sdk.Client Function({required sdk.DatabaseApi database});
typedef MatrixDatabaseOpener = Future<sqflite.Database> Function(String path);
typedef ApplicationSupportDirectoryProvider = Future<Directory> Function();

class SdkMatrixClient implements MatrixClient {
  SdkMatrixClient({
    required MatrixAuthBrowser authBrowser,
    ApplicationSupportDirectoryProvider? appSupportDirectoryProvider,
    MatrixDatabaseOpener? databaseOpener,
    MatrixSdkClientFactory? clientFactory,
  }) : _authBrowser = authBrowser,
       _appSupportDirectoryProvider =
           appSupportDirectoryProvider ?? getApplicationSupportDirectory,
       _databaseOpener = databaseOpener ?? sqflite.openDatabase,
       _clientFactory = clientFactory ?? _defaultClientFactory;

  final MatrixAuthBrowser _authBrowser;
  final ApplicationSupportDirectoryProvider _appSupportDirectoryProvider;
  final MatrixDatabaseOpener _databaseOpener;
  final MatrixSdkClientFactory _clientFactory;

  sdk.Client? _client;
  Future<sdk.Client>? _clientFuture;

  static final Uri _redirectUri = Uri.parse(matrixOidcRedirectUri);
  static final Uri _clientUri = Uri.parse(matrixOidcClientUri);

  static sdk.Client _defaultClientFactory({required sdk.DatabaseApi database}) {
    return sdk.Client(
      'weave_matrix_chat',
      database: database,
      supportedLoginTypes: {
        sdk.AuthenticationTypes.password,
        sdk.AuthenticationTypes.sso,
        sdk.AuthenticationTypes.oauth2,
      },
      onSoftLogout: (client) => client.refreshAccessToken(),
    );
  }

  @override
  Future<List<MatrixRoomSnapshot>> loadConversations({
    required Uri homeserver,
  }) async {
    final client = await _ensureClient();
    final normalizedHomeserver = _normalizeUri(homeserver);

    await _clearSessionIfHomeserverChanged(client, normalizedHomeserver);

    if (!client.isLogged()) {
      throw const ChatFailure.sessionRequired(
        'Connect Weave to your Matrix homeserver to load conversations.',
      );
    }

    try {
      await client.oneShotSync();
      return client.rooms.map(_mapRoom).toList(growable: false);
    } catch (error) {
      throw _mapError(
        error,
        fallback: 'Unable to load conversations from the Matrix homeserver.',
      );
    }
  }

  @override
  Future<void> connect({required Uri homeserver}) async {
    final client = await _ensureClient();
    final normalizedHomeserver = _normalizeUri(homeserver);

    await _clearSessionIfHomeserverChanged(client, normalizedHomeserver);

    if (client.isLogged()) {
      return;
    }

    final authMetadata = await _discoverAuthMetadata(
      client,
      normalizedHomeserver,
    );
    if (authMetadata == null) {
      throw ChatFailure.unsupportedConfiguration(
        'The configured Matrix homeserver at ${normalizedHomeserver.toString()} '
        'does not advertise Matrix OAuth 2.0 metadata. '
        'Weave currently requires Matrix Native OAuth 2.0 for chat.',
      );
    }

    try {
      final oidcClient = await client.registerOidcClient(
        redirectUris: [_redirectUri],
        applicationType: sdk.OidcApplicationType.native,
        clientInformation: sdk.OidcClientInformation(
          clientName: matrixOidcClientName,
          clientUri: _clientUri,
          logoUri: null,
          tosUri: null,
          policyUri: null,
        ),
      );
      final session = await client.initOidcLoginSession(
        oidcClientData: oidcClient,
        redirectUri: _redirectUri,
      );
      final callbackUri = await _authBrowser.authenticate(
        authorizationUri: session.authenticationUri,
        redirectUri: _redirectUri,
      );
      final callbackParameters = _extractCallbackParameters(callbackUri);
      final code = callbackParameters['code']?.trim();
      final state = callbackParameters['state']?.trim();

      if (code == null || code.isEmpty || state == null || state.isEmpty) {
        throw const ChatFailure.protocol(
          'The Matrix homeserver sign-in callback did not include the expected authorization response.',
        );
      }

      await client.oidcLogin(session: session, code: code, state: state);
    } on ChatFailure {
      rethrow;
    } catch (error) {
      throw _mapError(
        error,
        fallback: 'Unable to connect Weave to the Matrix homeserver.',
      );
    }
  }

  @override
  Future<void> signOut() async {
    if (!_isInteractivePlatform) {
      return;
    }

    final client = await _ensureClient();
    if (!client.isLogged()) {
      await clearSession();
      return;
    }

    try {
      await client.logout();
    } catch (error) {
      try {
        await client.clear();
      } catch (clearError) {
        throw _mapError(
          clearError,
          fallback: 'Unable to clear the saved Matrix session.',
        );
      }
    }
  }

  @override
  Future<void> clearSession() async {
    if (!_isInteractivePlatform) {
      return;
    }

    final client = await _ensureClient();

    try {
      await client.clear();
    } catch (error) {
      throw _mapError(
        error,
        fallback: 'Unable to clear the saved Matrix session.',
      );
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
      return client;
    } catch (error) {
      throw _mapError(
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

    await client.clear();
  }

  Future<sdk.GetAuthMetadataResponse?> _discoverAuthMetadata(
    sdk.Client client,
    Uri homeserver,
  ) async {
    try {
      final (_, _, _, authMetadata) = await client.checkHomeserver(
        homeserver,
        fetchAuthMetadata: true,
      );
      return authMetadata;
    } on sdk.BadServerLoginTypesException {
      client.homeserver = homeserver;
      try {
        return await client.getAuthMetadata();
      } on sdk.MatrixException catch (error) {
        if (error.error == sdk.MatrixError.M_UNRECOGNIZED) {
          return null;
        }
        throw _mapError(
          error,
          fallback:
              'Unable to determine whether the Matrix homeserver supports OAuth 2.0.',
        );
      }
    } on sdk.MatrixException catch (error) {
      if (error.error == sdk.MatrixError.M_UNRECOGNIZED) {
        return null;
      }
      throw _mapError(
        error,
        fallback:
            'Unable to determine whether the Matrix homeserver supports OAuth 2.0.',
      );
    } catch (error) {
      throw _mapError(
        error,
        fallback:
            'Unable to determine whether the Matrix homeserver supports OAuth 2.0.',
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

  Map<String, String> _extractCallbackParameters(Uri callbackUri) {
    if (callbackUri.queryParameters.isNotEmpty) {
      return callbackUri.queryParameters;
    }

    if (callbackUri.fragment.isEmpty) {
      return const <String, String>{};
    }

    return Uri.splitQueryString(callbackUri.fragment);
  }

  Uri _normalizeUri(Uri uri) {
    final normalized = uri.toString().trim();
    if (!normalized.endsWith('/')) {
      return uri;
    }

    return Uri.parse(normalized.substring(0, normalized.length - 1));
  }

  ChatFailure _mapError(Object error, {required String fallback}) {
    if (error is ChatFailure) {
      return error;
    }

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

  bool get _isInteractivePlatform {
    if (kIsWeb) {
      return false;
    }

    return defaultTargetPlatform == TargetPlatform.android ||
        defaultTargetPlatform == TargetPlatform.iOS ||
        defaultTargetPlatform == TargetPlatform.macOS;
  }
}

MatrixClient createMatrixClient({required MatrixAuthBrowser authBrowser}) {
  return SdkMatrixClient(authBrowser: authBrowser);
}
