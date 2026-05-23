import 'dart:convert';
import 'dart:math';

import 'package:matrix/matrix.dart' as sdk;
import 'package:riverpod/riverpod.dart';
import 'package:weave/features/chat/data/services/matrix_auth_browser.dart';
import 'package:weave/features/chat/data/services/matrix_client_factory.dart';
import 'package:weave/features/chat/data/services/matrix_error_mapper.dart';
import 'package:weave/features/chat/data/services/matrix_service_types.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

/// Handles all Matrix session operations: OAuth discovery and login,
/// session restore, sign-out, and session clearing.
abstract interface class MatrixSessionService {
  Future<void> connect({required Uri homeserver});

  /// Signs the current user out from the Matrix homeserver.
  ///
  /// Returns without error on unsupported platforms (web, desktop).
  Future<void> signOut();

  /// Clears locally persisted session data without contacting the homeserver.
  ///
  /// Returns without error on unsupported platforms (web, desktop).
  Future<void> clearSession();
}

class SdkMatrixSessionService implements MatrixSessionService {
  SdkMatrixSessionService({
    required MatrixClientFactory factory,
    required MatrixAuthBrowser authBrowser,
  }) : _factory = factory,
       _authBrowser = authBrowser;

  final MatrixClientFactory _factory;
  final MatrixAuthBrowser _authBrowser;

  static final Uri _clientUri = Uri.parse(matrixOidcClientUri);

  @override
  Future<void> connect({required Uri homeserver}) async {
    final normalizedHomeserver = _normalizeUri(homeserver);
    final client = await _factory.getClientForHomeserver(normalizedHomeserver);

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
      final redirectUri = _buildLoopbackRedirectUri();
      final clientInformation = sdk.OidcClientInformation(
        clientName: matrixOidcClientName,
        clientUri: _clientUri,
        logoUri: null,
        tosUri: null,
        policyUri: null,
      );
      final oidcClient = await _registerOidcClient(
        client,
        redirectUri: redirectUri,
        clientInformation: clientInformation,
      );
      final session = await client.initOidcLoginSession(
        oidcClientData: oidcClient,
        redirectUri: redirectUri,
      );
      final callbackUri = await _authBrowser.authenticate(
        authorizationUri: session.authenticationUri,
        redirectUri: redirectUri,
      );
      final callbackParameters = _extractCallbackParameters(callbackUri);
      final code = callbackParameters['code']?.trim();
      final state = callbackParameters['state']?.trim();

      if (code == null || code.isEmpty || state == null || state.isEmpty) {
        throw const ChatFailure.protocol(
          'The Matrix homeserver sign-in callback did not include the '
          'expected authorization response.',
        );
      }

      await client.oidcLogin(session: session, code: code, state: state);
    } on ChatFailure {
      rethrow;
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback: 'Unable to connect Weave to the Matrix homeserver.',
      );
    }
  }

  @override
  Future<void> signOut() async {
    final sdk.Client client;
    try {
      client = await _factory.getClient();
    } on ChatFailure catch (e) {
      if (e.type == ChatFailureType.unsupportedPlatform) return;
      rethrow;
    }

    if (!client.isLogged()) {
      await _factory.clearClient(client);
      return;
    }

    try {
      await client.logout();
    } catch (_) {
      try {
        await _factory.clearClient(client);
      } catch (clearError) {
        throw mapMatrixServiceError(
          clearError,
          fallback: 'Unable to clear the saved Matrix session.',
        );
      }
    }
  }

  @override
  Future<void> clearSession() async {
    final sdk.Client client;
    try {
      client = await _factory.getClient();
    } on ChatFailure catch (e) {
      if (e.type == ChatFailureType.unsupportedPlatform) return;
      rethrow;
    }

    try {
      await _factory.clearClient(client);
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback: 'Unable to clear the saved Matrix session.',
      );
    }
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
        throw mapMatrixServiceError(
          error,
          fallback:
              'Unable to determine whether the Matrix homeserver supports OAuth 2.0.',
        );
      }
    } on sdk.MatrixException catch (error) {
      if (error.error == sdk.MatrixError.M_UNRECOGNIZED) {
        return null;
      }
      throw mapMatrixServiceError(
        error,
        fallback:
            'Unable to determine whether the Matrix homeserver supports OAuth 2.0.',
      );
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback:
            'Unable to determine whether the Matrix homeserver supports OAuth 2.0.',
      );
    }
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

  Future<sdk.OidcClientData> _registerOidcClient(
    sdk.Client client, {
    required Uri redirectUri,
    required sdk.OidcClientInformation clientInformation,
  }) async {
    final authMetadata = await client.getAuthMetadata();
    final response = await client.httpClient.post(
      authMetadata.registrationEndpoint,
      body: jsonEncode({
        'redirect_uris': [redirectUri.toString()],
        'token_endpoint_auth_method': 'none',
        'response_types': const ['code'],
        'grant_types': const ['authorization_code', 'refresh_token'],
        'application_type': sdk.OidcApplicationType.native.name,
        ...clientInformation.toJson(),
        'contacts': [matrixOidcContact],
      }),
      headers: const {'content-type': 'application/json'},
    );
    if (response.statusCode != 201) {
      throw ChatFailure.protocol(
        'Matrix OIDC client registration failed with HTTP '
        '${response.statusCode}: ${utf8.decode(response.bodyBytes)}',
      );
    }

    final json = jsonDecode(utf8.decode(response.bodyBytes));
    if (json is! Map<String, Object?>) {
      throw const ChatFailure.protocol(
        'The Matrix OIDC client registration response was not a JSON object.',
      );
    }

    final clientId = json['client_id'];
    if (clientId is! String || clientId.isEmpty) {
      throw const ChatFailure.protocol(
        'The Matrix OIDC client registration response did not include a client_id.',
      );
    }

    final issuedAtRaw = json['client_id_issued_at'];
    final issuedAt = switch (issuedAtRaw) {
      int value when value > 9999999999 => DateTime.fromMillisecondsSinceEpoch(
        value,
      ),
      int value => DateTime.fromMillisecondsSinceEpoch(value * 1000),
      _ => null,
    };

    return sdk.OidcClientData(
      clientId: clientId,
      clientIdIssuedAt: issuedAt,
      clientInformation: clientInformation,
      additionalProperties: json,
    );
  }

  Uri _buildLoopbackRedirectUri() {
    final port = 20000 + Random.secure().nextInt(30000);
    return Uri(
      scheme: 'http',
      host: matrixOidcLoopbackRedirectHost,
      port: port,
      path: matrixOidcRedirectPath,
    );
  }

  Uri _normalizeUri(Uri uri) {
    final normalized = uri.toString().trim();
    if (!normalized.endsWith('/')) {
      return uri;
    }
    return Uri.parse(normalized.substring(0, normalized.length - 1));
  }
}

final matrixSessionServiceProvider = Provider<MatrixSessionService>((ref) {
  return SdkMatrixSessionService(
    factory: ref.watch(matrixClientFactoryProvider),
    authBrowser: ref.watch(matrixAuthBrowserProvider),
  );
});
