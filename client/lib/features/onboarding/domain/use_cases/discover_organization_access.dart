import 'dart:convert';

import 'package:weave/core/persistence/preferences_store.dart';
import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/generated/user_api/api.dart' as user_api;
import 'package:weave/features/onboarding/domain/entities/member_auth_onboarding_state.dart';
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
    PreferencesStore? evidenceStore,
  }) : _repository = repository,
       _discoveryClient = discoveryClient,
       _evidenceStore = evidenceStore;

  final ServerConfigurationRepository _repository;
  final AppStartDiscoveryClient _discoveryClient;
  final PreferencesStore? _evidenceStore;

  Future<OrganizationAccess> call(Uri uri) async {
    final OrganizationAccess access;
    try {
      access = const OrganizationAccessParser().parse(uri);
    } catch (error) {
      await _recordRawAccessFailure(uri, error);
      rethrow;
    }
    try {
      await _recordAuthOnboardingState(
        MemberAuthOnboardingStage.handoffReceived,
        access,
      );
      final appStart = await _discoveryClient.fetch(access);
      await _recordAuthOnboardingState(
        MemberAuthOnboardingStage.platformConfigLoaded,
        access,
      );
      await _repository.saveConfiguration(
        ServerConfiguration(
          providerType: OidcProviderType.oidc,
          oidcIssuerUrl: appStart.oidcIssuerUrl,
          oidcClientRegistration: OidcClientRegistration.manual(
            clientId: appStart.oidcClientId,
          ),
          serviceEndpoints: ServiceEndpoints(
            matrixHomeserverUrl: appStart.matrixClientServerBaseUrl,
            backendApiBaseUrl: appStart.userApiBaseUrl,
          ),
        ),
      );
      await _recordAccessEvidence(access, result: 'saved_configuration');
      await _recordAuthOnboardingState(
        MemberAuthOnboardingStage.readyForSso,
        access,
      );
    } catch (error) {
      await _recordAccessEvidence(
        access,
        result: 'failed',
        errorCode: supportSafeHandoffErrorCode(error),
        phase: _supportSafeFailurePhase(error),
      );
      await _recordAuthOnboardingState(
        MemberAuthOnboardingStage.recoverableError,
        access,
        errorCode: supportSafeHandoffErrorCode(error),
      );
      rethrow;
    }
    return access;
  }

  Future<void> _recordAuthOnboardingState(
    MemberAuthOnboardingStage stage,
    OrganizationAccess access, {
    String? errorCode,
  }) async {
    final store = _evidenceStore;
    if (store == null) {
      return;
    }
    await MemberAuthOnboardingStateRecorder(
      store: store,
    ).record(stage, access: access, errorCode: errorCode);
  }

  Future<void> _recordAccessEvidence(
    OrganizationAccess access, {
    required String result,
    String? errorCode,
    String? phase,
  }) async {
    await _evidenceStore?.setString(
      lastHandoffConsumedStorageKey,
      jsonEncode(
        _accessEvidence(
          access,
          result: result,
          errorCode: errorCode,
          phase: phase,
        ),
      ),
    );
  }

  Future<void> _recordRawAccessFailure(Uri uri, Object error) async {
    await _evidenceStore?.setString(
      lastHandoffConsumedStorageKey,
      jsonEncode(<String, Object>{
        'schemaVersion': 'weave.client.last_handoff_consumed.v1',
        'recordedAt': DateTime.now().toUtc().toIso8601String(),
        'result': 'failed',
        'phase': 'parse',
        'accessScheme': uri.scheme,
        'accessHost': uri.host,
        'errorCode': supportSafeHandoffErrorCode(error),
        'supportSafe': true,
      }),
    );
  }

  Map<String, Object> _accessEvidence(
    OrganizationAccess access, {
    required String result,
    String? errorCode,
    String? phase,
  }) {
    final handoff = access.handoff;
    return <String, Object>{
      'schemaVersion': 'weave.client.last_handoff_consumed.v1',
      'recordedAt': DateTime.now().toUtc().toIso8601String(),
      'accessKind': handoff == null ? 'server_uri' : 'handoff',
      'organizationOriginHost': access.organizationOrigin.host,
      'platformConfigHost': access.platformConfigUrl.host,
      'platformConfigPath': access.platformConfigUrl.path,
      if (handoff != null) ...{
        'handoffRef': handoff.handoffRef,
        'runId': handoff.runId,
        'organizationSlug': handoff.organizationSlug,
        'workspaceSlug': handoff.workspaceSlug,
      },
      'result': result,
      if (errorCode != null) 'errorCode': errorCode,
      if (phase != null) 'phase': phase,
      'supportSafe': true,
    };
  }

  String _supportSafeFailurePhase(Object error) {
    final code = supportSafeHandoffErrorCode(error);
    if (code.startsWith('WEAVE-APP-START-')) {
      return 'app_start_discovery';
    }
    return 'save_configuration';
  }
}

const lastHandoffConsumedStorageKey = 'last_handoff_consumed_v1';

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
    required this.oidcIssuerUrl,
    required this.oidcClientId,
    required this.userApiBaseUrl,
    required this.matrixClientServerBaseUrl,
  });

  final Uri oidcIssuerUrl;
  final String oidcClientId;
  final Uri userApiBaseUrl;
  final Uri matrixClientServerBaseUrl;
}

class AppStartDiscoveryClient {
  const AppStartDiscoveryClient({required http.Client httpClient})
    : _httpClient = httpClient;

  final http.Client _httpClient;

  Future<AppStartConfiguration> fetch(OrganizationAccess access) async {
    final primaryUri = access.platformConfigUrl;
    try {
      return await _fetchFrom(primaryUri, access);
    } on AppFailure catch (error) {
      final fallbackUri = _productOriginPlatformConfigUrl(access);
      if (!_shouldRetryOnProductOrigin(error, primaryUri, fallbackUri)) {
        rethrow;
      }
      return _fetchFrom(fallbackUri, access);
    }
  }

  Future<AppStartConfiguration> _fetchFrom(
    Uri uri,
    OrganizationAccess access,
  ) async {
    final user_api.PlatformConfigResponse? transport;
    try {
      final client = user_api.ApiClient(
        basePath: uri.replace(path: '', query: null, fragment: null).toString(),
      )..client = _httpClient;
      _headers(access).forEach(client.addDefaultHeader);
      transport = await user_api.PlatformApi(client).config();
    } on user_api.ApiException catch (error) {
      if (error.innerException case user_api.OrgManifestShapeException shape) {
        throw AppFailure.validation(
          'WEAVE-APP-START-DISCOVERY-INVALID: ${shape.message}.',
          cause: error,
        );
      }
      if (error.message == 'Exception during deserialization.') {
        throw AppFailure.validation(
          'WEAVE-APP-START-DISCOVERY-INVALID: The workspace start configuration is not a valid OrgManifest.',
          cause: error,
        );
      }
      if (error.innerException == null) {
        throw AppFailure.bootstrap(
          'WEAVE-APP-START-DISCOVERY-FAILED: The workspace start configuration could not be loaded.',
          cause: error.code,
        );
      }
      throw AppFailure.bootstrap(
        '${_transportErrorCode(error.innerException!)}: The workspace start configuration could not be reached.',
        cause: error,
      );
    } catch (error) {
      throw AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: The workspace start configuration is not a valid OrgManifest.',
        cause: error,
      );
    }
    if (transport == null) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: The workspace start configuration must be a JSON object.',
      );
    }
    return _configurationFromTransport(transport, access);
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

  Uri _productOriginPlatformConfigUrl(OrganizationAccess access) => Uri(
    scheme: access.organizationOrigin.scheme,
    host: access.organizationOrigin.host,
    port: access.organizationOrigin.hasPort
        ? access.organizationOrigin.port
        : null,
    path: '/api/platform/config',
  );

  bool _shouldRetryOnProductOrigin(
    AppFailure error,
    Uri primaryUri,
    Uri fallbackUri,
  ) {
    if (primaryUri == fallbackUri) {
      return false;
    }
    final code = _errorCode(error);
    return code == 'WEAVE-APP-START-DNS-FAILED' ||
        code == 'WEAVE-APP-START-TLS-FAILED' ||
        code == 'WEAVE-APP-START-NETWORK-FAILED' ||
        code == 'WEAVE-APP-START-TIMEOUT';
  }

  String _errorCode(AppFailure error) {
    final separator = error.message.indexOf(':');
    return separator > 0
        ? error.message.substring(0, separator)
        : error.message;
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

  AppStartConfiguration _configurationFromTransport(
    user_api.PlatformConfigResponse transport,
    OrganizationAccess access,
  ) {
    if (transport.schemaVersion != 2) {
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
    if (!releasePostures.contains(transport.releasePosture)) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: releasePosture is not supported.',
      );
    }
    final organizationOrigin = _uri(
      transport.organizationOrigin,
      fieldName: 'organizationOrigin',
    );
    if (_apiOrigin(organizationOrigin) !=
        _apiOrigin(access.organizationOrigin)) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: organizationOrigin must match the handoff origin.',
      );
    }
    _uri(transport.oidc.issuer, fieldName: 'oidc.issuer');
    _clientId(transport.oidc.clientId);
    final userApiBaseUrl = _uri(
      transport.userApiBaseUrl,
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
      transport.protocols.matrixClientServerBaseUrl,
      fieldName: 'protocols.matrixClientServerBaseUrl',
    );
    final matrixPath = matrixClientServerBaseUrl.path;
    if (matrixPath.isNotEmpty && matrixPath != '/') {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: protocols.matrixClientServerBaseUrl must be a homeserver origin.',
      );
    }
    _validateDomains(transport.domains);
    _validateRecoveryActions(transport.recoveryActions);
    final transportOidc = transport.oidc;
    final transportProtocols = transport.protocols;

    return AppStartConfiguration(
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

  void _validateDomains(List<user_api.DomainCapability> value) {
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
    for (final domain in value) {
      if (domain.domain case final String name
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
      final state = domain.state;
      final capabilities = domain.capabilities;
      if (!allowedStates.contains(state) ||
          capabilities.any((capability) => capability.trim().isEmpty) ||
          capabilities.toSet().length != capabilities.length ||
          (domain.supportReference != null &&
              domain.supportReference!.isEmpty)) {
        throw const AppFailure.validation(
          'WEAVE-APP-START-DISCOVERY-INVALID: domains entries are incomplete.',
        );
      }
    }
  }

  void _validateRecoveryActions(List<user_api.RecoveryAction> value) {
    for (final action in value) {
      if (action.code == null ||
          action.code!.isEmpty ||
          action.label == null ||
          action.label!.isEmpty ||
          (action.supportReference != null &&
              action.supportReference!.isEmpty)) {
        throw const AppFailure.validation(
          'WEAVE-APP-START-DISCOVERY-INVALID: recoveryActions entries are incomplete.',
        );
      }
    }
  }

  Uri _apiOrigin(Uri backendApiBaseUrl) => Uri(
    scheme: backendApiBaseUrl.scheme,
    host: backendApiBaseUrl.host,
    port: backendApiBaseUrl.hasPort ? backendApiBaseUrl.port : null,
  );

  Uri _uri(Object? rawValue, {required String fieldName}) {
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

  String _clientId(Object? rawValue, {String fieldName = 'oidc.clientId'}) {
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
