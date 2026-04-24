import 'package:weave/features/auth/domain/entities/oidc_constants.dart';

/// Runtime configuration for live-stack integration tests.
class TestConfig {
  const TestConfig({
    required this.baseUrl,
    required this.username,
    required this.password,
    required this.issuerUrl,
    required this.clientId,
    required this.matrixHomeserverUrl,
    required this.nextcloudBaseUrl,
    required this.backendApiBaseUrl,
  });

  factory TestConfig.fromEnvironment() {
    final baseUrl = _parseUrl(
      const String.fromEnvironment(
        'WEAVE_BASE_URL',
        defaultValue: 'https://api.weave.local',
      ),
      variableName: 'WEAVE_BASE_URL',
    );
    final workspaceHost = _workspaceHost(baseUrl.host);
    final issuerUrl = _issuerUrl(baseUrl, workspaceHost);
    final clientId = const String.fromEnvironment(
      'WEAVE_OIDC_CLIENT_ID',
      defaultValue: oidcDefaultClientId,
    ).trim();

    return TestConfig(
      baseUrl: baseUrl,
      username: const String.fromEnvironment('WEAVE_TEST_USERNAME'),
      password: const String.fromEnvironment('WEAVE_TEST_PASSWORD').trim(),
      issuerUrl: issuerUrl,
      clientId: clientId,
      matrixHomeserverUrl: _serviceUri(baseUrl, host: 'matrix.$workspaceHost'),
      nextcloudBaseUrl: _serviceUri(baseUrl, host: 'nextcloud.$workspaceHost'),
      backendApiBaseUrl: baseUrl,
    );
  }

  final Uri baseUrl;
  final String username;
  final String password;
  final Uri issuerUrl;
  final String clientId;
  final Uri matrixHomeserverUrl;
  final Uri nextcloudBaseUrl;
  final Uri backendApiBaseUrl;

  TestConfig copyWith({
    Uri? baseUrl,
    String? username,
    String? password,
    Uri? issuerUrl,
    String? clientId,
    Uri? matrixHomeserverUrl,
    Uri? nextcloudBaseUrl,
    Uri? backendApiBaseUrl,
  }) {
    return TestConfig(
      baseUrl: baseUrl ?? this.baseUrl,
      username: username ?? this.username,
      password: password ?? this.password,
      issuerUrl: issuerUrl ?? this.issuerUrl,
      clientId: clientId ?? this.clientId,
      matrixHomeserverUrl: matrixHomeserverUrl ?? this.matrixHomeserverUrl,
      nextcloudBaseUrl: nextcloudBaseUrl ?? this.nextcloudBaseUrl,
      backendApiBaseUrl: backendApiBaseUrl ?? this.backendApiBaseUrl,
    );
  }

  Uri apiUri(String path) {
    final pathSegments = path
        .split('/')
        .where((segment) => segment.isNotEmpty)
        .toList(growable: false);

    return backendApiBaseUrl.replace(
      pathSegments: [
        ...backendApiBaseUrl.pathSegments.where(
          (segment) => segment.isNotEmpty,
        ),
        ...pathSegments,
      ],
    );
  }

  Uri unreachableBackendApiBaseUrl() {
    return backendApiBaseUrl.replace(
      port: backendApiBaseUrl.scheme == 'https' ? 1 : 9,
    );
  }

  bool get hasCredentials =>
      username.trim().isNotEmpty && password.trim().isNotEmpty;

  void requireCredentials() {
    final missing = <String>[
      if (username.trim().isEmpty) 'WEAVE_TEST_USERNAME',
      if (password.trim().isEmpty) 'WEAVE_TEST_PASSWORD',
    ];

    if (missing.isNotEmpty) {
      throw StateError(
        'Missing integration test credential dart-define(s): '
        '${missing.join(', ')}.',
      );
    }
  }

  static Uri _parseUrl(String value, {required String variableName}) {
    final parsed = Uri.tryParse(value.trim());
    if (parsed == null ||
        !parsed.isAbsolute ||
        parsed.host.isEmpty ||
        (parsed.scheme != 'http' && parsed.scheme != 'https')) {
      throw StateError(
        '$variableName must be an absolute HTTP or HTTPS URL. '
        'Received "$value".',
      );
    }

    return parsed;
  }

  static Uri _serviceUri(
    Uri baseUrl, {
    required String host,
    List<String> pathSegments = const <String>[],
  }) {
    return baseUrl.replace(
      host: host,
      pathSegments: pathSegments,
      query: null,
      fragment: null,
    );
  }

  static Uri _issuerUrl(Uri baseUrl, String workspaceHost) {
    const override = String.fromEnvironment('WEAVE_OIDC_ISSUER_URL');
    if (override.trim().isNotEmpty) {
      return _parseUrl(override, variableName: 'WEAVE_OIDC_ISSUER_URL');
    }

    return _serviceUri(
      baseUrl,
      host: 'keycloak.$workspaceHost',
      pathSegments: const <String>['realms', 'weave'],
    );
  }

  static String _workspaceHost(String host) {
    final labels = host.split('.');
    final serviceLabel = labels.first.toLowerCase();
    if (labels.length > 2 &&
        (serviceLabel == 'api' ||
            serviceLabel == 'weave' ||
            serviceLabel == 'auth' ||
            serviceLabel == 'keycloak')) {
      return labels.skip(1).join('.');
    }

    return host;
  }
}
