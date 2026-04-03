import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:weave/features/files/data/services/nextcloud_login_launcher.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/entities/nextcloud_session.dart';

class NextcloudAuthClient {
  NextcloudAuthClient({
    required http.Client httpClient,
    required NextcloudLoginLauncher loginLauncher,
    Duration pollInterval = const Duration(seconds: 2),
    int maxPollAttempts = 150,
  }) : _httpClient = httpClient,
       _loginLauncher = loginLauncher,
       _pollInterval = pollInterval,
       _maxPollAttempts = maxPollAttempts;

  final http.Client _httpClient;
  final NextcloudLoginLauncher _loginLauncher;
  final Duration _pollInterval;
  final int _maxPollAttempts;

  Future<NextcloudSession> connect(Uri configuredBaseUrl) async {
    final normalizedBaseUrl = _normalizeBaseUrl(configuredBaseUrl);
    final startResponse = await _send(
      () => _httpClient.post(
        _resolve(normalizedBaseUrl, 'index.php/login/v2'),
        headers: const {'Accept': 'application/json'},
      ),
      fallbackMessage: 'Unable to start the Nextcloud login flow.',
    );

    if (startResponse.statusCode != 200) {
      throw FilesFailure.protocol(
        'Nextcloud rejected the login flow request (${startResponse.statusCode}).',
      );
    }

    final startPayload = _decodeJson(startResponse.body);
    final poll = startPayload['poll'];
    if (poll is! Map<String, dynamic>) {
      throw const FilesFailure.protocol(
        'Nextcloud returned an invalid login flow response.',
      );
    }

    final loginValue = startPayload['login'];
    final endpointValue = poll['endpoint'];
    final tokenValue = poll['token'];
    if (loginValue is! String || endpointValue is! String || tokenValue is! String) {
      throw const FilesFailure.protocol(
        'Nextcloud returned incomplete login flow data.',
      );
    }

    await _loginLauncher.launch(Uri.parse(loginValue));

    return _pollForSession(
      configuredBaseUrl: normalizedBaseUrl,
      endpoint: Uri.parse(endpointValue),
      token: tokenValue,
    );
  }

  Future<void> revokeAppPassword(NextcloudSession session) async {
    try {
      await _httpClient.delete(
        _resolve(session.baseUrl, 'ocs/v2.php/core/apppassword'),
        headers: {
          ..._authHeaders(session.loginName, session.appPassword),
          'OCS-APIREQUEST': 'true',
        },
      );
    } catch (_) {
      // Local cleanup should still proceed if the remote app password cannot be deleted.
    }
  }

  Future<NextcloudSession> _pollForSession({
    required Uri configuredBaseUrl,
    required Uri endpoint,
    required String token,
  }) async {
    for (var attempt = 0; attempt < _maxPollAttempts; attempt++) {
      final response = await _send(
        () => _httpClient.post(
          endpoint,
          headers: const {
            'Content-Type': 'application/x-www-form-urlencoded',
            'Accept': 'application/json',
          },
          body: {'token': token},
        ),
        fallbackMessage: 'Unable to finish the Nextcloud login flow.',
      );

      if (response.statusCode == 404) {
        if (attempt < _maxPollAttempts - 1) {
          await Future<void>.delayed(_pollInterval);
          continue;
        }
        break;
      }

      if (response.statusCode != 200) {
        throw FilesFailure.protocol(
          'Nextcloud returned an unexpected login status (${response.statusCode}).',
        );
      }

      final payload = _decodeJson(response.body);
      final serverValue = payload['server'];
      final loginNameValue = payload['loginName'];
      final appPasswordValue = payload['appPassword'];
      if (serverValue is! String ||
          loginNameValue is! String ||
          appPasswordValue is! String) {
        throw const FilesFailure.protocol(
          'Nextcloud returned incomplete app credentials.',
        );
      }

      final serverUrl = _normalizeBaseUrl(Uri.parse(serverValue));
      if (serverUrl != configuredBaseUrl) {
        throw const FilesFailure.configuration(
          'The Nextcloud login flow completed for a different server than the one configured in Weave.',
        );
      }

      final userId = await _fetchUserId(
        baseUrl: serverUrl,
        loginName: loginNameValue,
        appPassword: appPasswordValue,
      );

      return NextcloudSession(
        baseUrl: serverUrl,
        loginName: loginNameValue,
        userId: userId,
        appPassword: appPasswordValue,
      );
    }

    throw const FilesFailure.cancelled(
      'The Nextcloud login flow did not finish before it timed out.',
    );
  }

  Future<String> _fetchUserId({
    required Uri baseUrl,
    required String loginName,
    required String appPassword,
  }) async {
    final response = await _send(
      () => _httpClient.get(
        _resolve(baseUrl, 'ocs/v2.php/cloud/user?format=json'),
        headers: {
          ..._authHeaders(loginName, appPassword),
          'OCS-APIREQUEST': 'true',
          'Accept': 'application/json',
        },
      ),
      fallbackMessage: 'Unable to validate the Nextcloud account.',
    );

    if (response.statusCode == 401 || response.statusCode == 403) {
      throw const FilesFailure.invalidCredentials(
        'The saved Nextcloud credentials are no longer valid.',
      );
    }

    if (response.statusCode != 200) {
      throw FilesFailure.protocol(
        'Nextcloud returned an unexpected account lookup status (${response.statusCode}).',
      );
    }

    final payload = _decodeJson(response.body);
    final ocs = payload['ocs'];
    if (ocs is! Map<String, dynamic>) {
      throw const FilesFailure.protocol(
        'Nextcloud returned an invalid account response.',
      );
    }

    final data = ocs['data'];
    if (data is! Map<String, dynamic>) {
      throw const FilesFailure.protocol(
        'Nextcloud returned incomplete account data.',
      );
    }

    final userId = (data['id'] as String?)?.trim();
    if (userId == null || userId.isEmpty) {
      throw const FilesFailure.protocol(
        'Nextcloud did not provide a usable account identifier.',
      );
    }

    return userId;
  }

  Future<http.Response> _send(
    Future<http.Response> Function() request, {
    required String fallbackMessage,
  }) async {
    try {
      return await request();
    } on FilesFailure {
      rethrow;
    } catch (error) {
      throw FilesFailure.unknown(fallbackMessage, cause: error);
    }
  }

  Map<String, String> _authHeaders(String username, String password) {
    final encoded = base64Encode(utf8.encode('$username:$password'));
    return {'Authorization': 'Basic $encoded'};
  }

  Map<String, dynamic> _decodeJson(String raw) {
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic>) {
        throw const FilesFailure.protocol(
          'Nextcloud returned an invalid JSON payload.',
        );
      }
      return decoded;
    } on FilesFailure {
      rethrow;
    } catch (error) {
      throw FilesFailure.protocol(
        'Nextcloud returned an invalid JSON payload.',
        cause: error,
      );
    }
  }

  Uri _normalizeBaseUrl(Uri uri) {
    final path = uri.path.endsWith('/') ? uri.path : '${uri.path}/';
    return uri.replace(path: path, query: null, fragment: null);
  }

  Uri _resolve(Uri baseUrl, String relativePath) {
    return _normalizeBaseUrl(baseUrl).resolve(relativePath);
  }
}

final nextcloudHttpClientProvider = Provider<http.Client>((ref) {
  final client = http.Client();
  ref.onDispose(client.close);
  return client;
});

final nextcloudAuthClientProvider = Provider<NextcloudAuthClient>((ref) {
  return NextcloudAuthClient(
    httpClient: ref.watch(nextcloudHttpClientProvider),
    loginLauncher: ref.watch(nextcloudLoginLauncherProvider),
  );
});
