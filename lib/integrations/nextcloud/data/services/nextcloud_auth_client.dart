import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/integrations/nextcloud/data/services/nextcloud_login_launcher.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_failure.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_session.dart';

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

  Future<NextcloudSession> createBearerSession({
    required Uri configuredBaseUrl,
    required String bearerToken,
    String? accountLabelHint,
  }) async {
    final normalizedBaseUrl = normalizeNextcloudBaseUrl(configuredBaseUrl);
    _ensureSupportedBaseUrl(
      normalizedBaseUrl,
      message:
          'Use an HTTP or HTTPS Nextcloud URL before validating bearer access.',
    );

    final userId = await _fetchUserId(
      baseUrl: normalizedBaseUrl,
      headers: _bearerHeaders(bearerToken),
    );

    return NextcloudSession.oidcBearer(
      baseUrl: normalizedBaseUrl,
      userId: userId,
      accountLabel: accountLabelHint ?? userId,
      bearerToken: bearerToken,
    );
  }

  Future<NextcloudSession> connect(Uri configuredBaseUrl) async {
    final normalizedBaseUrl = normalizeNextcloudBaseUrl(configuredBaseUrl);
    _ensureSupportedBaseUrl(
      normalizedBaseUrl,
      message:
          'Use an HTTP or HTTPS Nextcloud URL before starting the login flow.',
    );
    final startResponse = await _send(
      () => _httpClient.post(
        _resolve(normalizedBaseUrl, 'index.php/login/v2'),
        headers: const {'Accept': 'application/json'},
      ),
      fallbackMessage: 'Unable to start the Nextcloud login flow.',
    );

    if (startResponse.statusCode != 200) {
      throw NextcloudFailure.protocol(
        'Nextcloud rejected the login flow request (${startResponse.statusCode}).',
      );
    }

    final startPayload = _decodeJson(startResponse.body);
    final poll = startPayload['poll'];
    if (poll is! Map<String, dynamic>) {
      throw const NextcloudFailure.protocol(
        'Nextcloud returned an invalid login flow response.',
      );
    }

    final loginValue = startPayload['login'];
    final endpointValue = poll['endpoint'];
    final tokenValue = poll['token'];
    if (loginValue is! String ||
        endpointValue is! String ||
        tokenValue is! String) {
      throw const NextcloudFailure.protocol(
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
    if (!session.usesAppPassword ||
        session.loginName == null ||
        session.appPassword == null) {
      return;
    }

    try {
      await _httpClient.delete(
        _resolve(session.baseUrl, 'ocs/v2.php/core/apppassword'),
        headers: {
          ..._basicAuthHeaders(session.loginName!, session.appPassword!),
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
        throw NextcloudFailure.protocol(
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
        throw const NextcloudFailure.protocol(
          'Nextcloud returned incomplete app credentials.',
        );
      }

      final serverUrl = normalizeNextcloudBaseUrl(Uri.parse(serverValue));
      _ensureSupportedBaseUrl(
        serverUrl,
        message:
            'Nextcloud returned app credentials for a server that does not use HTTP or HTTPS, which Weave will not use.',
      );
      if (serverUrl != configuredBaseUrl) {
        throw const NextcloudFailure.configuration(
          'The Nextcloud login flow completed for a different server than the one configured in Weave.',
        );
      }

      final userId = await _fetchUserId(
        baseUrl: serverUrl,
        headers: _basicAuthHeaders(loginNameValue, appPasswordValue),
      );

      return NextcloudSession.appPassword(
        baseUrl: serverUrl,
        loginName: loginNameValue,
        userId: userId,
        appPassword: appPasswordValue,
      );
    }

    throw const NextcloudFailure.cancelled(
      'The Nextcloud login flow did not finish before it timed out.',
    );
  }

  Future<String> _fetchUserId({
    required Uri baseUrl,
    required Map<String, String> headers,
  }) async {
    _ensureSupportedBaseUrl(
      baseUrl,
      message:
          'Use an HTTP or HTTPS Nextcloud URL before validating the account.',
    );
    final response = await _send(
      () => _httpClient.get(
        _resolve(baseUrl, 'ocs/v2.php/cloud/user?format=json'),
        headers: {
          ...headers,
          'OCS-APIREQUEST': 'true',
          'Accept': 'application/json',
        },
      ),
      fallbackMessage: 'Unable to validate the Nextcloud account.',
    );

    if (response.statusCode == 401 || response.statusCode == 403) {
      throw const NextcloudFailure.invalidCredentials(
        'The saved Nextcloud credentials are no longer valid.',
      );
    }

    if (response.statusCode != 200) {
      throw NextcloudFailure.protocol(
        'Nextcloud returned an unexpected account lookup status (${response.statusCode}).',
      );
    }

    final payload = _decodeJson(response.body);
    final ocs = payload['ocs'];
    if (ocs is! Map<String, dynamic>) {
      throw const NextcloudFailure.protocol(
        'Nextcloud returned an invalid account response.',
      );
    }

    final data = ocs['data'];
    if (data is! Map<String, dynamic>) {
      throw const NextcloudFailure.protocol(
        'Nextcloud returned incomplete account data.',
      );
    }

    final userId = (data['id'] as String?)?.trim();
    if (userId == null || userId.isEmpty) {
      throw const NextcloudFailure.protocol(
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
    } on NextcloudFailure {
      rethrow;
    } catch (error) {
      throw NextcloudFailure.unknown(fallbackMessage, cause: error);
    }
  }

  Map<String, String> _basicAuthHeaders(String username, String password) {
    final encoded = base64Encode(utf8.encode('$username:$password'));
    return {'Authorization': 'Basic $encoded'};
  }

  Map<String, String> _bearerHeaders(String bearerToken) {
    return {'Authorization': 'Bearer $bearerToken'};
  }

  Map<String, dynamic> _decodeJson(String raw) {
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic>) {
        throw const NextcloudFailure.protocol(
          'Nextcloud returned an invalid JSON payload.',
        );
      }
      return decoded;
    } on NextcloudFailure {
      rethrow;
    } catch (error) {
      throw NextcloudFailure.protocol(
        'Nextcloud returned an invalid JSON payload.',
        cause: error,
      );
    }
  }

  Uri _resolve(Uri baseUrl, String relativePath) {
    return normalizeNextcloudBaseUrl(baseUrl).resolve(relativePath);
  }

  void _ensureSupportedBaseUrl(Uri baseUrl, {required String message}) {
    final scheme = baseUrl.scheme.toLowerCase();
    if (scheme != 'http' && scheme != 'https') {
      throw NextcloudFailure.configuration(message);
    }
  }
}
