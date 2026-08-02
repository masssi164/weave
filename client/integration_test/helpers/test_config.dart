import 'package:weave/features/auth/domain/entities/oidc_constants.dart';

/// Non-secret endpoint configuration shared by client contract tests.
///
/// Human credentials deliberately do not belong to Flutter build arguments.
/// Interactive authentication is performed by the production AppAuth client
/// through the operating-system browser.
class TestConfig {
  const TestConfig({
    required this.issuerUrl,
    required this.clientId,
    required this.matrixHomeserverUrl,
    required this.nextcloudBaseUrl,
    required this.backendApiBaseUrl,
    required this.offlineContractOnly,
  });

  factory TestConfig.fromEnvironment() {
    const apiBaseUrl = String.fromEnvironment(
      'WEAVE_API_BASE_URL',
      defaultValue: 'https://api.weave.test/api',
    );
    final backendApiBaseUrl = _parseUrl(
      apiBaseUrl,
      variableName: 'WEAVE_API_BASE_URL',
    );
    final workspaceHost = _workspaceHost(backendApiBaseUrl.host);

    return TestConfig(
      issuerUrl: _configuredOrDefault(
        'WEAVE_OIDC_ISSUER_URL',
        const String.fromEnvironment('WEAVE_OIDC_ISSUER_URL'),
        backendApiBaseUrl.replace(
          host: 'auth.$workspaceHost',
          pathSegments: const ['realms', 'weave'],
          query: null,
          fragment: null,
        ),
      ),
      clientId: const String.fromEnvironment(
        'WEAVE_OIDC_CLIENT_ID',
        defaultValue: oidcDefaultClientId,
      ).trim(),
      matrixHomeserverUrl: _configuredOrDefault(
        'WEAVE_MATRIX_HOMESERVER_URL',
        const String.fromEnvironment('WEAVE_MATRIX_HOMESERVER_URL'),
        _apiOrigin(backendApiBaseUrl),
      ),
      nextcloudBaseUrl: _configuredOrDefault(
        'WEAVE_NEXTCLOUD_BASE_URL',
        const String.fromEnvironment('WEAVE_NEXTCLOUD_BASE_URL'),
        backendApiBaseUrl.replace(
          host: 'files.$workspaceHost',
          pathSegments: const [],
          query: null,
          fragment: null,
        ),
      ),
      backendApiBaseUrl: backendApiBaseUrl,
      offlineContractOnly:
          const String.fromEnvironment(
            'WEAVE_OFFLINE_CONTRACT_ONLY',
          ).trim().toLowerCase() ==
          'true',
    );
  }

  final Uri issuerUrl;
  final String clientId;
  final Uri matrixHomeserverUrl;
  final Uri nextcloudBaseUrl;
  final Uri backendApiBaseUrl;
  final bool offlineContractOnly;

  TestConfig copyWith({Uri? backendApiBaseUrl}) => TestConfig(
    issuerUrl: issuerUrl,
    clientId: clientId,
    matrixHomeserverUrl: matrixHomeserverUrl,
    nextcloudBaseUrl: nextcloudBaseUrl,
    backendApiBaseUrl: backendApiBaseUrl ?? this.backendApiBaseUrl,
    offlineContractOnly: offlineContractOnly,
  );

  Uri apiUri(String path) {
    final baseSegments = backendApiBaseUrl.pathSegments
        .where((segment) => segment.isNotEmpty)
        .toList(growable: true);
    final requestedSegments = path
        .split('/')
        .where((segment) => segment.isNotEmpty)
        .toList(growable: false);
    if (baseSegments.isNotEmpty &&
        requestedSegments.isNotEmpty &&
        baseSegments.last == 'api' &&
        requestedSegments.first == 'api') {
      baseSegments.addAll(requestedSegments.skip(1));
    } else {
      baseSegments.addAll(requestedSegments);
    }
    return backendApiBaseUrl.replace(pathSegments: baseSegments);
  }

  static Uri _configuredOrDefault(
    String variableName,
    String configured,
    Uri fallback,
  ) => configured.trim().isEmpty
      ? fallback
      : _parseUrl(configured, variableName: variableName);

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

  static Uri _apiOrigin(Uri uri) {
    final segments = uri.pathSegments
        .where((segment) => segment.isNotEmpty)
        .toList(growable: true);
    if (segments.isNotEmpty && segments.last == 'api') {
      segments.removeLast();
    }
    return uri.replace(pathSegments: segments, query: null, fragment: null);
  }

  static String _workspaceHost(String host) {
    final labels = host.split('.');
    if (labels.length > 2 &&
        const {
          'api',
          'auth',
          'files',
          'matrix',
          'weave',
        }.contains(labels.first.toLowerCase())) {
      return labels.skip(1).join('.');
    }
    return host;
  }
}
