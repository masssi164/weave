import 'dart:convert';

import 'package:weave/core/persistence/preferences_store.dart';
import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/onboarding/domain/entities/member_auth_onboarding_state.dart';
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
    PreferencesStore? evidenceStore,
  }) : _repository = repository,
       _discoveryClient = discoveryClient,
       _evidenceStore = evidenceStore;

  final ServerConfigurationRepository _repository;
  final AppStartDiscoveryClient _discoveryClient;
  final PreferencesStore? _evidenceStore;

  Future<MemberHandoff> call(Uri uri) async {
    final MemberHandoff handoff;
    try {
      handoff = const MemberHandoffParser().parse(uri);
    } catch (error) {
      await _recordRawHandoffFailure(uri, error);
      rethrow;
    }
    try {
      await _recordAuthOnboardingState(
        MemberAuthOnboardingStage.handoffReceived,
        handoff,
      );
      final appStart = await _discoveryClient.fetch(handoff);
      await _recordAuthOnboardingState(
        MemberAuthOnboardingStage.platformConfigLoaded,
        handoff,
      );
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
      await _recordHandoffEvidence(handoff, result: 'saved_configuration');
      await _recordAuthOnboardingState(
        MemberAuthOnboardingStage.readyForSso,
        handoff,
      );
    } catch (error) {
      await _recordHandoffEvidence(
        handoff,
        result: 'failed',
        errorCode: supportSafeHandoffErrorCode(error),
        phase: _supportSafeFailurePhase(error),
      );
      await _recordAuthOnboardingState(
        MemberAuthOnboardingStage.recoverableError,
        handoff,
        errorCode: supportSafeHandoffErrorCode(error),
      );
      rethrow;
    }
    return handoff;
  }

  Future<void> _recordAuthOnboardingState(
    MemberAuthOnboardingStage stage,
    MemberHandoff handoff, {
    String? errorCode,
  }) async {
    final store = _evidenceStore;
    if (store == null) {
      return;
    }
    await MemberAuthOnboardingStateRecorder(
      store: store,
    ).record(stage, handoff: handoff, errorCode: errorCode);
  }

  Future<void> _recordHandoffEvidence(
    MemberHandoff handoff, {
    required String result,
    String? errorCode,
    String? phase,
  }) async {
    await _evidenceStore?.setString(
      lastHandoffConsumedStorageKey,
      jsonEncode(
        _handoffEvidence(
          handoff,
          result: result,
          errorCode: errorCode,
          phase: phase,
        ),
      ),
    );
  }

  Future<void> _recordRawHandoffFailure(Uri uri, Object error) async {
    await _evidenceStore?.setString(
      lastHandoffConsumedStorageKey,
      jsonEncode(<String, Object>{
        'schemaVersion': 'weave.client.last_handoff_consumed.v1',
        'recordedAt': DateTime.now().toUtc().toIso8601String(),
        'result': 'failed',
        'phase': 'parse',
        'inviteScheme': uri.scheme,
        'inviteHost': uri.host,
        'errorCode': supportSafeHandoffErrorCode(error),
        'supportSafe': true,
      }),
    );
  }

  Map<String, Object> _handoffEvidence(
    MemberHandoff handoff, {
    required String result,
    String? errorCode,
    String? phase,
  }) {
    return <String, Object>{
      'schemaVersion': 'weave.client.last_handoff_consumed.v1',
      'recordedAt': DateTime.now().toUtc().toIso8601String(),
      'handoffRef': handoff.handoffRef,
      'runId': handoff.runId,
      'organizationSlug': handoff.organizationSlug,
      'workspaceSlug': handoff.workspaceSlug,
      'profile': handoff.profile,
      'platformConfigHost': handoff.platformConfigUrl.host,
      'platformConfigPath': handoff.platformConfigUrl.path,
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
    final primaryUri = handoff.platformConfigUrl;
    try {
      return await _fetchFrom(primaryUri, handoff);
    } on AppFailure catch (error) {
      final fallbackUri = _productOriginPlatformConfigUrl(handoff);
      if (!_shouldRetryOnProductOrigin(error, primaryUri, fallbackUri)) {
        rethrow;
      }
      return _fetchFrom(fallbackUri, handoff);
    }
  }

  Future<AppStartConfiguration> _fetchFrom(
    Uri uri,
    MemberHandoff handoff,
  ) async {
    final http.Response response;
    try {
      response = await _httpClient.get(uri, headers: _headers(handoff));
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
    return _configurationFromJson(payload);
  }

  Map<String, String> _headers(MemberHandoff handoff) => {
    'Accept': 'application/json',
    'X-Weave-Handoff-Ref': handoff.handoffRef,
    'X-Weave-Handoff-Run-Id': handoff.runId,
  };

  Uri _productOriginPlatformConfigUrl(MemberHandoff handoff) => Uri(
    scheme: handoff.productBaseUrl.scheme,
    host: handoff.productBaseUrl.host,
    port: handoff.productBaseUrl.hasPort ? handoff.productBaseUrl.port : null,
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
    if (type.contains('TimeoutException')) {
      return 'WEAVE-APP-START-TIMEOUT';
    }
    if (type.contains('HandshakeException') ||
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

  AppStartConfiguration _configurationFromJson(Map<String, dynamic> json) {
    final oidcIssuerUrl = _uri(
      json['oidcIssuerUrl'],
      fieldName: 'oidcIssuerUrl',
    );
    final oidcClientId = _clientId(json['oidcClientId']);
    final backendApiBaseUrl = _uri(json['apiBaseUrl'], fieldName: 'apiBaseUrl');
    final advertisedMatrixFacade = json['matrixHomeserverUrl'];
    if (advertisedMatrixFacade is! String ||
        advertisedMatrixFacade.trim().isEmpty) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: matrixHomeserverUrl is required.',
      );
    }
    final matrixHomeserverUrl = _uri(
      advertisedMatrixFacade,
      fieldName: 'matrixHomeserverUrl',
    );
    final expectedMatrixFacadeUrl = _apiOrigin(backendApiBaseUrl);
    final matrixPath = matrixHomeserverUrl.path;
    if (_apiOrigin(matrixHomeserverUrl) != expectedMatrixFacadeUrl ||
        (matrixPath.isNotEmpty && matrixPath != '/')) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: matrixHomeserverUrl must be the Weave API origin.',
      );
    }
    final nextcloudBaseUrl = _uri(
      json['filesProductUrl'],
      fieldName: 'filesProductUrl',
    );

    return AppStartConfiguration(
      oidcIssuerUrl: oidcIssuerUrl,
      oidcClientId: oidcClientId,
      backendApiBaseUrl: backendApiBaseUrl,
      matrixHomeserverUrl: expectedMatrixFacadeUrl,
      nextcloudBaseUrl: nextcloudBaseUrl,
    );
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

  String _clientId(Object? rawValue) {
    if (rawValue is! String || rawValue.trim().isEmpty) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: oidcClientId is required.',
      );
    }
    final value = rawValue.trim();
    if (!RegExp(r'^[A-Za-z0-9._:-]{3,80}$').hasMatch(value)) {
      throw const AppFailure.validation(
        'WEAVE-APP-START-DISCOVERY-INVALID: oidcClientId is not support-safe.',
      );
    }
    return value;
  }
}
