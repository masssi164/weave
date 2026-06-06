import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';
import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

class ConsumeMemberHandoff {
  const ConsumeMemberHandoff({
    required ServerConfigurationRepository repository,
    required AppStartDiscoveryClient discoveryClient,
  }) : _repository = repository,
       _discoveryClient = discoveryClient;

  final ServerConfigurationRepository _repository;
  final AppStartDiscoveryClient _discoveryClient;

  Future<MemberHandoff> call(Uri uri) async {
    final handoff = const MemberHandoffParser().parse(uri);
    final appStart = await _discoveryClient.fetch(handoff);
    await _repository.saveConfiguration(
      ServerConfiguration(
        providerType: OidcProviderType.keycloak,
        oidcIssuerUrl: appStart.oidcIssuerUrl,
        oidcClientRegistration: OidcClientRegistration.manual(
          clientId: appStart.oidcClientId,
        ),
        serviceEndpoints: ServiceEndpoints(
          matrixHomeserverUrl: appStart.matrixHomeserverUrl,
          nextcloudBaseUrl: appStart.nextcloudBaseUrl,
          backendApiBaseUrl: appStart.backendApiBaseUrl,
        ),
      ),
    );
    return handoff;
  }
}

class AppStartConfiguration {
  const AppStartConfiguration({
    required this.oidcIssuerUrl,
    required this.oidcClientId,
    required this.backendApiBaseUrl,
    required this.matrixHomeserverUrl,
    required this.nextcloudBaseUrl,
  });

  final Uri oidcIssuerUrl;
  final String oidcClientId;
  final Uri backendApiBaseUrl;
  final Uri matrixHomeserverUrl;
  final Uri nextcloudBaseUrl;
}

class AppStartDiscoveryClient {
  const AppStartDiscoveryClient({required http.Client httpClient})
    : _httpClient = httpClient;

  final http.Client _httpClient;

  Future<AppStartConfiguration> fetch(MemberHandoff handoff) async {
    final response = await _httpClient.get(
      handoff.platformConfigUrl,
      headers: const {'Accept': 'application/json'},
    );
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw AppFailure.bootstrap(
        'WEAVE-APP-START-DISCOVERY-FAILED: The workspace start configuration could not be loaded.',
        cause: response.statusCode,
      );
    }

    final payload = _decodeJsonObject(response.body);
    return _configurationFromJson(payload, handoff);
  }

  Map<String, dynamic> _decodeJsonObject(String body) {
    try {
      final decoded = jsonDecode(body);
      if (decoded is Map<String, dynamic>) {
        return decoded;
      }
    } catch (error) {
      throw AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: The workspace start configuration is not valid JSON.',
        cause: error,
      );
    }
    throw const AppFailure.validation(
      'WEAVE-APP-START-DISCOVERY-INVALID: The workspace start configuration must be a JSON object.',
    );
  }

  AppStartConfiguration _configurationFromJson(
    Map<String, dynamic> json,
    MemberHandoff handoff,
  ) {
    final oidcIssuerUrl = _uri(
      json['oidcIssuerUrl'] ?? _issuerFromAuthBaseUrl(json['authBaseUrl']),
      fieldName: 'oidcIssuerUrl',
      fallback: handoff.fallbackIssuerUrl,
    );
    final oidcClientId = _clientId(json['oidcClientId'], fallback: 'weave-app');
    final backendApiBaseUrl = _uri(
      json['apiBaseUrl'],
      fieldName: 'apiBaseUrl',
      fallback: _backendFallbackFromPlatformConfig(handoff),
    );
    final matrixHomeserverUrl = _uri(
      json['matrixHomeserverUrl'],
      fieldName: 'matrixHomeserverUrl',
      fallback: handoff.fallbackProviderNeutralServiceUrl,
    );
    final nextcloudBaseUrl = _uri(
      json['filesProductUrl'] ?? json['nextcloudBaseUrl'],
      fieldName: 'filesProductUrl',
      fallback: handoff.fallbackProviderNeutralServiceUrl,
    );

    return AppStartConfiguration(
      oidcIssuerUrl: oidcIssuerUrl,
      oidcClientId: oidcClientId,
      backendApiBaseUrl: backendApiBaseUrl,
      matrixHomeserverUrl: matrixHomeserverUrl,
      nextcloudBaseUrl: nextcloudBaseUrl,
    );
  }

  Uri? _issuerFromAuthBaseUrl(Object? rawValue) {
    if (rawValue is! String || rawValue.trim().isEmpty) {
      return null;
    }
    final authBaseUrl = Uri.tryParse(rawValue.trim());
    if (authBaseUrl == null ||
        !authBaseUrl.isAbsolute ||
        authBaseUrl.host.isEmpty) {
      return null;
    }
    if (authBaseUrl.userInfo.isNotEmpty) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: authBaseUrl must not embed credentials.',
      );
    }
    return Uri(
      scheme: authBaseUrl.scheme,
      host: authBaseUrl.host,
      port: authBaseUrl.hasPort ? authBaseUrl.port : null,
      path: _joinPath(authBaseUrl.path, '/realms/weave'),
    );
  }

  Uri _backendFallbackFromPlatformConfig(MemberHandoff handoff) {
    final path = handoff.platformConfigUrl.path;
    if (path.endsWith('/platform/config')) {
      return Uri(
        scheme: handoff.platformConfigUrl.scheme,
        host: handoff.platformConfigUrl.host,
        port: handoff.platformConfigUrl.hasPort
            ? handoff.platformConfigUrl.port
            : null,
        path: path.substring(0, path.length - '/platform/config'.length),
      );
    }
    return handoff.fallbackBackendApiBaseUrl;
  }

  Uri _uri(
    Object? rawValue, {
    required String fieldName,
    required Uri fallback,
  }) {
    final value = rawValue is Uri
        ? rawValue.toString()
        : rawValue is String && rawValue.trim().isNotEmpty
        ? rawValue.trim()
        : fallback.toString();
    final uri = Uri.tryParse(value);
    if (uri == null || !uri.isAbsolute || uri.host.isEmpty) {
      throw AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: $fieldName must be an absolute URL.',
      );
    }
    if (uri.scheme != 'http' && uri.scheme != 'https') {
      throw AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: $fieldName must use HTTP or HTTPS.',
      );
    }
    if (uri.userInfo.isNotEmpty) {
      throw AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: $fieldName must not embed credentials.',
      );
    }
    if (uri.hasQuery || uri.hasFragment) {
      throw AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: $fieldName must not include query or fragment data.',
      );
    }
    return uri;
  }

  String _clientId(Object? rawValue, {required String fallback}) {
    final value = rawValue is String && rawValue.trim().isNotEmpty
        ? rawValue.trim()
        : fallback;
    if (!RegExp(r'^[A-Za-z0-9._:-]{3,80}$').hasMatch(value)) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: oidcClientId is not support-safe.',
      );
    }
    return value;
  }

  String _joinPath(String left, String right) {
    final normalizedLeft = left.endsWith('/')
        ? left.substring(0, left.length - 1)
        : left;
    return '$normalizedLeft$right';
  }
}
