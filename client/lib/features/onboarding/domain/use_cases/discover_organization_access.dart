import 'dart:convert';
import 'dart:async';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/generated/user_api/api.dart' as user_api;
import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';
import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

class DiscoverOrganizationAccess {
  const DiscoverOrganizationAccess({
    required ServerConfigurationRepository repository,
    required AppStartDiscoveryClient discoveryClient,
  }) : _repository = repository,
       _discoveryClient = discoveryClient;

  final ServerConfigurationRepository _repository;
  final AppStartDiscoveryClient _discoveryClient;

  Future<OrganizationAccess> call(Uri uri) async {
    final access = const OrganizationAccessParser().parse(uri);
    final appStart = await _discoveryClient.fetch(access);
    await _repository.saveConfiguration(
      appStart.toServerConfiguration(
        access.organizationOrigin,
        validatedAt: DateTime.now().toUtc(),
      ),
    );
    return access;
  }
}

String supportSafeHandoffErrorCode(Object error) {
  if (error is AppFailure) {
    final message = error.message;
    final separator = message.indexOf(':');
    return separator > 0 ? message.substring(0, separator) : message;
  }
  return error.runtimeType.toString();
}

class AppStartConfiguration {
  const AppStartConfiguration({
    required this.manifest,
    required this.oidcIssuerUrl,
    required this.oidcClientId,
    required this.userApiBaseUrl,
    required this.matrixClientServerBaseUrl,
  });

  final Map<String, dynamic> manifest;
  final Uri oidcIssuerUrl;
  final String oidcClientId;
  final Uri userApiBaseUrl;
  final Uri matrixClientServerBaseUrl;

  ServerConfiguration toServerConfiguration(
    Uri origin, {
    required DateTime? validatedAt,
  }) => ServerConfiguration(
    organizationOrigin: origin,
    validatedManifest: manifest,
    manifestValidatedAt: validatedAt,
    providerType: OidcProviderType.oidc,
    oidcIssuerUrl: oidcIssuerUrl,
    oidcClientRegistration: OidcClientRegistration.manual(
      clientId: oidcClientId,
    ),
    serviceEndpoints: ServiceEndpoints(
      matrixHomeserverUrl: matrixClientServerBaseUrl,
      backendApiBaseUrl: userApiBaseUrl,
    ),
  );
}

/// Keep generated PlatformApi requests on the selected organization authority.
class _CanonicalBootstrapTransport extends http.BaseClient {
  _CanonicalBootstrapTransport(this.delegate, this.canonical);
  final http.Client delegate;
  final Uri canonical;
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    if (request.url != canonical || request.method != 'GET') {
      throw http.ClientException('Unexpected bootstrap endpoint.', request.url);
    }
    request.followRedirects = false;
    return delegate.send(request);
  }
}

class AppStartDiscoveryClient {
  const AppStartDiscoveryClient({required http.Client httpClient})
    : _httpClient = httpClient;

  final http.Client _httpClient;

  Future<AppStartConfiguration> fetch(OrganizationAccess access) async {
    final origin = _uri(
      access.organizationOrigin,
      fieldName: 'organizationOrigin',
    );
    if ((origin.path.isNotEmpty && origin.path != '/') ||
        (origin.hasPort && (origin.port < 1 || origin.port > 65535))) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: organizationOrigin must be a secure origin.',
      );
    }
    return _fetchFrom(origin.replace(path: '/api/platform/config'), access);
  }

  Future<AppStartConfiguration> _fetchFrom(
    Uri uri,
    OrganizationAccess access,
  ) async {
    final http.Response response;
    try {
      final client = user_api.ApiClient(
        basePath: uri.replace(path: '', query: null, fragment: null).toString(),
      )..client = _CanonicalBootstrapTransport(_httpClient, uri);
      _headers(access).forEach(client.addDefaultHeader);
      response = await user_api.PlatformApi(
        client,
      ).configWithHttpInfo().timeout(const Duration(seconds: 15));
    } catch (error) {
      throw AppFailure.bootstrap(
        '${_transportErrorCode(error)}: The workspace start configuration could not be reached.',
        cause: error,
      );
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw AppFailure.bootstrap(
        'WEAVE-APP-START-DISCOVERY-FAILED: The workspace start configuration could not be loaded.',
        cause: response.statusCode,
      );
    }

    final payload = _decodeJsonObject(response.body);
    return validateManifest(payload, access);
  }

  Map<String, String> _headers(OrganizationAccess access) {
    final handoff = access.handoff;
    return {
      'Accept': 'application/json',
      if (handoff != null) ...{
        'X-Weave-Handoff-Ref': handoff.handoffRef,
        'X-Weave-Handoff-Run-Id': handoff.runId,
      },
    };
  }

  String _transportErrorCode(Object error) {
    final type = error.runtimeType.toString();
    final message = error.toString().toLowerCase();
    if (type.contains('TimeoutException') ||
        message.contains('timeoutexception') ||
        message.contains('timed out')) {
      return 'WEAVE-APP-START-TIMEOUT';
    }
    if (type.contains('HandshakeException') ||
        message.contains('handshake') ||
        message.contains('certificate') ||
        message.contains('cert_verify') ||
        message.contains('trust')) {
      return 'WEAVE-APP-START-TLS-FAILED';
    }
    if (type.contains('SocketException') ||
        message.contains('failed host lookup') ||
        message.contains('nodename nor servname') ||
        message.contains('name or service not known')) {
      return 'WEAVE-APP-START-DNS-FAILED';
    }
    return 'WEAVE-APP-START-NETWORK-FAILED';
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

  AppStartConfiguration validateManifest(
    Map<String, dynamic> json,
    OrganizationAccess access,
  ) => parseManifest(json, access);

  // The persisted cache and network response use the same strict contract.
  static AppStartConfiguration parseManifest(
    Map<String, dynamic> json,
    OrganizationAccess access,
  ) {
    _requireExactKeys(json, const {
      'schemaVersion',
      'organizationOrigin',
      'userApiBaseUrl',
      'oidc',
      'protocols',
      'releasePosture',
      'domains',
      'recoveryActions',
    });
    if (json['schemaVersion'] != 2) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: schemaVersion must be 2.',
      );
    }
    const releasePostures = {
      'development',
      'dogfood',
      'release_candidate',
      'stable',
    };
    if (!releasePostures.contains(json['releasePosture'])) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: releasePosture is not supported.',
      );
    }
    if (json.containsKey('recoveryActions')) {
      _validateRecoveryActions(json['recoveryActions']);
    }
    final organizationOrigin = _uri(
      json['organizationOrigin'],
      fieldName: 'organizationOrigin',
    );
    if ((organizationOrigin.path.isNotEmpty &&
            organizationOrigin.path != '/') ||
        _apiOrigin(organizationOrigin) !=
            _apiOrigin(access.organizationOrigin)) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: organizationOrigin must match the handoff origin.',
      );
    }
    final oidc = _object(json['oidc'], fieldName: 'oidc');
    _requireExactKeys(oidc, const {'issuer', 'clientId'});
    final protocols = _object(json['protocols'], fieldName: 'protocols');
    _requireExactKeys(protocols, const {'matrixClientServerBaseUrl'});
    _uri(oidc['issuer'], fieldName: 'oidc.issuer');
    _clientId(oidc['clientId']);
    final userApiBaseUrl = _uri(
      json['userApiBaseUrl'],
      fieldName: 'userApiBaseUrl',
    );
    final userApiSegments = userApiBaseUrl.pathSegments
        .where((segment) => segment.trim().isNotEmpty)
        .toList(growable: false);
    if (userApiSegments.isEmpty || userApiSegments.last != 'api') {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: userApiBaseUrl must end in /api.',
      );
    }
    final matrixClientServerBaseUrl = _uri(
      protocols['matrixClientServerBaseUrl'],
      fieldName: 'protocols.matrixClientServerBaseUrl',
    );
    final matrixPath = matrixClientServerBaseUrl.path;
    if (matrixPath.isNotEmpty && matrixPath != '/') {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: protocols.matrixClientServerBaseUrl must be a homeserver origin.',
      );
    }
    _validateDomains(json['domains']);

    // Exact field validation above rejects unknown public protocol surfaces;
    // the generated transport model then supplies the values consumed by the
    // client configuration. Its decoder alone tolerates unknown JSON fields.
    final transport = user_api.OrgManifestResponse.fromJson(json)!;
    final transportOidc = transport.oidc;
    final transportProtocols = transport.protocols;

    return AppStartConfiguration(
      manifest: jsonDecode(jsonEncode(json)) as Map<String, dynamic>,
      oidcIssuerUrl: _uri(transportOidc.issuer, fieldName: 'oidc.issuer'),
      oidcClientId: _clientId(transportOidc.clientId),
      userApiBaseUrl: _uri(
        transport.userApiBaseUrl,
        fieldName: 'userApiBaseUrl',
      ),
      matrixClientServerBaseUrl: _uri(
        transportProtocols.matrixClientServerBaseUrl,
        fieldName: 'protocols.matrixClientServerBaseUrl',
      ).replace(path: ''),
    );
  }

  static Map<String, dynamic> _object(
    Object? value, {
    required String fieldName,
  }) {
    if (value is Map<String, dynamic>) {
      return value;
    }
    throw AppFailure.validation(
      'WEAVE-APP-START-DISCOVERY-INVALID: $fieldName must be an object.',
    );
  }

  static void _requireExactKeys(
    Map<String, dynamic> value,
    Set<String> allowed,
  ) {
    final unexpected = value.keys.where((key) => !allowed.contains(key));
    if (unexpected.isNotEmpty) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: The organization manifest contains unsupported fields.',
      );
    }
  }

  static void _validateDomains(Object? value) {
    if (value is! List) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: domains must be an array.',
      );
    }
    if (value.isEmpty) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: domains must not be empty.',
      );
    }
    const allowedDomains = {
      'identity',
      'chat',
      'files',
      'calendar',
      'calls',
      'boards',
      'agent-runtime-control',
      'health',
    };
    const allowedStates = {
      'available',
      'degraded',
      'unavailable',
      'disabled_by_policy',
      'not_entitled',
      'not_configured',
      'migration_blocked',
      'unsupported',
    };
    final observed = <String>{};
    for (final item in value) {
      final domain = _object(item, fieldName: 'domains[]');
      _requireExactKeys(domain, const {
        'domain',
        'state',
        'capabilities',
        'supportReference',
      });
      if (domain['domain'] case final String name
          when allowedDomains.contains(name)) {
        if (!observed.add(name)) {
          throw const AppFailure.validation(
            'WEAVE-APP-START-DISCOVERY-INVALID: domains must be unique.',
          );
        }
      } else {
        throw const AppFailure.validation(
          'WEAVE-APP-START-DISCOVERY-INVALID: domains contains an unsupported domain.',
        );
      }
      final state = domain['state'];
      final capabilities = domain['capabilities'];
      if (state is! String ||
          !allowedStates.contains(state) ||
          capabilities is! List ||
          capabilities.any(
            (capability) => capability is! String || capability.trim().isEmpty,
          ) ||
          capabilities.toSet().length != capabilities.length ||
          (domain['supportReference'] != null &&
              (domain['supportReference'] is! String ||
                  (domain['supportReference'] as String).isEmpty))) {
        throw const AppFailure.validation(
          'WEAVE-APP-START-DISCOVERY-INVALID: domains entries are incomplete.',
        );
      }
    }
  }

  static void _validateRecoveryActions(Object? value) {
    if (value is! List) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: recoveryActions must be an array.',
      );
    }
    for (final item in value) {
      final action = _object(item, fieldName: 'recoveryActions[]');
      _requireExactKeys(action, const {'code', 'label', 'supportReference'});
      if (action['code'] is! String ||
          (action['code'] as String).isEmpty ||
          action['label'] is! String ||
          (action['label'] as String).isEmpty ||
          (action['supportReference'] != null &&
              (action['supportReference'] is! String ||
                  (action['supportReference'] as String).isEmpty))) {
        throw const AppFailure.validation(
          'WEAVE-APP-START-DISCOVERY-INVALID: recoveryActions entries are incomplete.',
        );
      }
    }
  }

  static Uri _apiOrigin(Uri backendApiBaseUrl) => Uri(
    scheme: backendApiBaseUrl.scheme,
    host: backendApiBaseUrl.host,
    port: backendApiBaseUrl.hasPort ? backendApiBaseUrl.port : null,
  );

  static Uri _uri(Object? rawValue, {required String fieldName}) {
    final value = rawValue is Uri
        ? rawValue.toString()
        : rawValue is String && rawValue.trim().isNotEmpty
        ? rawValue.trim()
        : null;
    if (value == null) {
      throw AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: $fieldName is required.',
      );
    }
    final uri = Uri.tryParse(value);
    if (uri == null || !uri.isAbsolute || uri.host.isEmpty) {
      throw AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: $fieldName must be an absolute URL.',
      );
    }
    if (uri.scheme != 'https') {
      throw AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: $fieldName must use HTTPS.',
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

  static String _clientId(
    Object? rawValue, {
    String fieldName = 'oidc.clientId',
  }) {
    if (rawValue is! String || rawValue.trim().isEmpty) {
      throw AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: $fieldName is required.',
      );
    }
    final value = rawValue.trim();
    if (!RegExp(r'^[A-Za-z0-9._:-]{3,80}$').hasMatch(value)) {
      throw AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: $fieldName is not support-safe.',
      );
    }
    return value;
  }
}
