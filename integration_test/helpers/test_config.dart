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
        defaultValue: 'https://weave.local',
      ),
      variableName: 'WEAVE_BASE_URL',
    );
    final workspaceHost = _workspaceHost(baseUrl.host);

    return TestConfig(
      baseUrl: baseUrl,
      username: const String.fromEnvironment('WEAVE_TEST_USERNAME'),
      password: const String.fromEnvironment('WEAVE_TEST_PASSWORD'),
      issuerUrl: _serviceUri(
        baseUrl,
        host: 'auth.$workspaceHost',
        pathSegments: const <String>['realms', 'weave'],
      ),
      clientId: oidcDefaultClientId,
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
      host: 'unreachable.${backendApiBaseUrl.host}.invalid',
    );
  }

  void requireCredentials() {
    final missing = <String>[
      if (username.trim().isEmpty) 'WEAVE_TEST_USERNAME',
      if (password.isEmpty) 'WEAVE_TEST_PASSWORD',
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

  static String _workspaceHost(String host) {
    final labels = host.split('.');
    final serviceLabel = labels.first.toLowerCase();
    if (labels.length > 2 &&
        (serviceLabel == 'api' ||
            serviceLabel == 'weave' ||
            serviceLabel == 'auth')) {
      return labels.skip(1).join('.');
    }

    return host;
  }
}
