import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/auth/domain/entities/oidc_constants.dart';
import 'package:weave/features/server_config/data/services/service_endpoint_deriver.dart';
import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration_save_result.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

part 'server_configuration_form_controller.g.dart';

class ServerConfigurationFormState {
  const ServerConfigurationFormState({
    required this.initialized,
    required this.isSaving,
    required this.providerType,
    required this.issuerUrl,
    required this.clientId,
    required this.matrixHomeserverUrl,
    required this.backendApiBaseUrl,
    required this.derivedMatrixHomeserverUrl,
    required this.derivedBackendApiBaseUrl,
    required this.matrixOverridden,
    required this.backendApiOverridden,
    this.issuerError,
    this.clientIdError,
    this.matrixError,
    this.backendApiError,
    this.saveFailure,
  });

  const ServerConfigurationFormState.initial()
    : initialized = false,
      isSaving = false,
      providerType = OidcProviderType.oidc,
      issuerUrl = '',
      clientId = oidcDefaultClientId,
      matrixHomeserverUrl = '',
      backendApiBaseUrl = '',
      derivedMatrixHomeserverUrl = '',
      derivedBackendApiBaseUrl = '',
      matrixOverridden = false,
      backendApiOverridden = false,
      issuerError = null,
      clientIdError = null,
      matrixError = null,
      backendApiError = null,
      saveFailure = null;

  final bool initialized;
  final bool isSaving;
  final OidcProviderType providerType;
  final String issuerUrl;
  final String clientId;
  final String matrixHomeserverUrl;
  final String backendApiBaseUrl;
  final String derivedMatrixHomeserverUrl;
  final String derivedBackendApiBaseUrl;
  final bool matrixOverridden;
  final bool backendApiOverridden;
  final String? issuerError;
  final String? clientIdError;
  final String? matrixError;
  final String? backendApiError;
  final AppFailure? saveFailure;

  bool get hasDerivedDefaults =>
      derivedMatrixHomeserverUrl.isNotEmpty &&
      derivedBackendApiBaseUrl.isNotEmpty;

  ServerConfigurationFormState copyWith({
    bool? initialized,
    bool? isSaving,
    OidcProviderType? providerType,
    String? issuerUrl,
    String? clientId,
    String? matrixHomeserverUrl,
    String? backendApiBaseUrl,
    String? derivedMatrixHomeserverUrl,
    String? derivedBackendApiBaseUrl,
    bool? matrixOverridden,
    bool? backendApiOverridden,
    String? issuerError,
    String? clientIdError,
    String? matrixError,
    String? backendApiError,
    AppFailure? saveFailure,
    bool clearIssuerError = false,
    bool clearClientIdError = false,
    bool clearMatrixError = false,
    bool clearBackendApiError = false,
    bool clearSaveFailure = false,
  }) {
    return ServerConfigurationFormState(
      initialized: initialized ?? this.initialized,
      isSaving: isSaving ?? this.isSaving,
      providerType: providerType ?? this.providerType,
      issuerUrl: issuerUrl ?? this.issuerUrl,
      clientId: clientId ?? this.clientId,
      matrixHomeserverUrl: matrixHomeserverUrl ?? this.matrixHomeserverUrl,
      backendApiBaseUrl: backendApiBaseUrl ?? this.backendApiBaseUrl,
      derivedMatrixHomeserverUrl:
          derivedMatrixHomeserverUrl ?? this.derivedMatrixHomeserverUrl,
      derivedBackendApiBaseUrl:
          derivedBackendApiBaseUrl ?? this.derivedBackendApiBaseUrl,
      matrixOverridden: matrixOverridden ?? this.matrixOverridden,
      backendApiOverridden: backendApiOverridden ?? this.backendApiOverridden,
      issuerError: clearIssuerError ? null : (issuerError ?? this.issuerError),
      clientIdError: clearClientIdError
          ? null
          : (clientIdError ?? this.clientIdError),
      matrixError: clearMatrixError ? null : (matrixError ?? this.matrixError),
      backendApiError: clearBackendApiError
          ? null
          : (backendApiError ?? this.backendApiError),
      saveFailure: clearSaveFailure ? null : (saveFailure ?? this.saveFailure),
    );
  }
}

@riverpod
class ServerConfigurationFormController
    extends _$ServerConfigurationFormController {
  String? _initialAuthSignature;
  String? _initialMatrixSignature;
  String? _initialBackendApiSignature;

  @override
  ServerConfigurationFormState build() =>
      const ServerConfigurationFormState.initial();

  void initialize(ServerConfiguration? configuration) {
    if (state.initialized) {
      return;
    }

    if (configuration == null) {
      _initialAuthSignature = null;
      _initialMatrixSignature = null;
      _initialBackendApiSignature = null;
      state = state.copyWith(initialized: true, clientId: oidcDefaultClientId);
      return;
    }

    final derivedEndpoints = _tryDeriveFromIssuer(
      configuration.oidcIssuerUrl.toString(),
    );
    final matrixUrl = configuration.serviceEndpoints.matrixHomeserverUrl
        .toString();
    final backendApiUrl = configuration.serviceEndpoints.backendApiBaseUrl
        .toString();
    _initialAuthSignature = _authSignature(
      configuration.oidcIssuerUrl.toString(),
      configuration.oidcClientRegistration.clientId,
    );
    _initialMatrixSignature = _matrixSignature(matrixUrl);
    _initialBackendApiSignature = _backendApiSignature(backendApiUrl);

    state = state.copyWith(
      initialized: true,
      providerType: configuration.providerType,
      issuerUrl: configuration.oidcIssuerUrl.toString(),
      clientId: configuration.oidcClientRegistration.clientId,
      matrixHomeserverUrl: matrixUrl,
      backendApiBaseUrl: backendApiUrl,
      derivedMatrixHomeserverUrl:
          derivedEndpoints?.matrixHomeserverUrl.toString() ?? '',
      derivedBackendApiBaseUrl:
          derivedEndpoints?.backendApiBaseUrl.toString() ?? '',
      matrixOverridden:
          derivedEndpoints != null &&
          matrixUrl != derivedEndpoints.matrixHomeserverUrl.toString(),
      backendApiOverridden:
          derivedEndpoints != null &&
          backendApiUrl != derivedEndpoints.backendApiBaseUrl.toString(),
      clearIssuerError: true,
      clearClientIdError: true,
      clearMatrixError: true,
      clearBackendApiError: true,
      clearSaveFailure: true,
    );
  }

  void updateProviderType(OidcProviderType providerType) {
    state = state.copyWith(providerType: providerType, clearSaveFailure: true);
  }

  void updateIssuerUrl(String issuerUrl) {
    final derivedEndpoints = _tryDeriveFromIssuer(issuerUrl);
    final matrixWillBeReplaced = !state.matrixOverridden;
    final backendApiWillBeReplaced = !state.backendApiOverridden;

    state = state.copyWith(
      issuerUrl: issuerUrl,
      clientId: _validateClientId(state.clientId),
      derivedMatrixHomeserverUrl:
          derivedEndpoints?.matrixHomeserverUrl.toString() ?? '',
      derivedBackendApiBaseUrl:
          derivedEndpoints?.backendApiBaseUrl.toString() ?? '',
      matrixHomeserverUrl: state.matrixOverridden
          ? state.matrixHomeserverUrl
          : (derivedEndpoints?.matrixHomeserverUrl.toString() ?? ''),
      backendApiBaseUrl: state.backendApiOverridden
          ? state.backendApiBaseUrl
          : (derivedEndpoints?.backendApiBaseUrl.toString() ?? ''),
      clearIssuerError: true,
      clearClientIdError: true,
      clearMatrixError: matrixWillBeReplaced,
      clearBackendApiError: backendApiWillBeReplaced,
      clearSaveFailure: true,
    );
  }

  void updateClientId(String clientId) {
    state = state.copyWith(
      clientId: clientId,
      clearClientIdError: true,
      clearSaveFailure: true,
    );
  }

  void updateMatrixHomeserverUrl(String value) {
    final trimmed = value.trim();
    final derivedValue = state.derivedMatrixHomeserverUrl;

    state = state.copyWith(
      matrixHomeserverUrl: value,
      matrixOverridden: derivedValue.isEmpty
          ? trimmed.isNotEmpty
          : trimmed != derivedValue,
      clearMatrixError: true,
      clearSaveFailure: true,
    );
  }

  void updateBackendApiBaseUrl(String value) {
    final trimmed = value.trim();
    final derivedValue = state.derivedBackendApiBaseUrl;

    state = state.copyWith(
      backendApiBaseUrl: value,
      backendApiOverridden: derivedValue.isEmpty
          ? trimmed.isNotEmpty
          : trimmed != derivedValue,
      clearBackendApiError: true,
      clearSaveFailure: true,
    );
  }

  bool validateProviderAndIssuerStep() {
    try {
      final issuerUrl = ref
          .read(serviceEndpointDeriverProvider)
          .parseIssuerUrl(state.issuerUrl);
      final clientId = _validateClientId(state.clientId);
      final defaults = ref
          .read(serviceEndpointDeriverProvider)
          .derive(issuerUrl);

      state = state.copyWith(
        clientId: clientId,
        derivedMatrixHomeserverUrl: defaults.matrixHomeserverUrl.toString(),
        derivedBackendApiBaseUrl: defaults.backendApiBaseUrl.toString(),
        matrixHomeserverUrl: state.matrixOverridden
            ? state.matrixHomeserverUrl
            : defaults.matrixHomeserverUrl.toString(),
        backendApiBaseUrl: state.backendApiOverridden
            ? state.backendApiBaseUrl
            : defaults.backendApiBaseUrl.toString(),
        clearIssuerError: true,
        clearClientIdError: true,
        clearMatrixError: !state.matrixOverridden,
        clearBackendApiError: !state.backendApiOverridden,
      );
      return true;
    } on AppFailure catch (failure) {
      state = state.copyWith(
        issuerError: failure.message,
        clearClientIdError: true,
      );
      return false;
    }
  }

  Future<ServerConfigurationSaveResult?> save() async {
    final deriver = ref.read(serviceEndpointDeriverProvider);

    try {
      final issuerUrl = deriver.parseIssuerUrl(state.issuerUrl);
      final clientId = _validateClientId(state.clientId);
      final backendApiUrl = deriver.parseServiceUrl(
        state.backendApiBaseUrl,
        fieldName: 'the backend API URL',
      );
      final matrixUrl = deriver.parseMatrixHomeserverUrl(
        state.matrixHomeserverUrl,
      );

      state = state.copyWith(
        isSaving: true,
        clearIssuerError: true,
        clearClientIdError: true,
        clearMatrixError: true,
        clearBackendApiError: true,
        clearSaveFailure: true,
      );

      final configuration = ServerConfiguration(
        providerType: state.providerType,
        oidcIssuerUrl: issuerUrl,
        oidcClientRegistration: OidcClientRegistration.manual(
          clientId: clientId,
        ),
        serviceEndpoints: ServiceEndpoints(
          matrixHomeserverUrl: matrixUrl,
          backendApiBaseUrl: backendApiUrl,
        ),
      );

      await ref
          .read(serverConfigurationRepositoryProvider)
          .saveConfiguration(configuration);

      state = state.copyWith(
        initialized: true,
        isSaving: false,
        clearSaveFailure: true,
      );

      final nextAuthSignature = _authSignature(issuerUrl.toString(), clientId);
      final authConfigurationChanged =
          _initialAuthSignature != null &&
          _initialAuthSignature != nextAuthSignature;
      final nextMatrixSignature = _matrixSignature(matrixUrl.toString());
      final matrixHomeserverChanged =
          _initialMatrixSignature != null &&
          _initialMatrixSignature != nextMatrixSignature;
      final nextBackendApiSignature = _backendApiSignature(
        backendApiUrl.toString(),
      );
      final backendApiBaseUrlChanged =
          _initialBackendApiSignature != null &&
          _initialBackendApiSignature != nextBackendApiSignature;
      _initialAuthSignature = nextAuthSignature;
      _initialMatrixSignature = nextMatrixSignature;
      _initialBackendApiSignature = nextBackendApiSignature;

      return ServerConfigurationSaveResult(
        configuration: configuration,
        authConfigurationChanged: authConfigurationChanged,
        matrixHomeserverChanged: matrixHomeserverChanged,
        backendApiBaseUrlChanged: backendApiBaseUrlChanged,
      );
    } on AppFailure catch (failure) {
      final issuerMessage =
          failure.type == AppFailureType.validation &&
              failure.message.contains('issuer')
          ? failure.message
          : null;
      final clientIdMessage =
          failure.type == AppFailureType.validation &&
              failure.message.contains('client ID')
          ? failure.message
          : null;
      final matrixMessage =
          failure.type == AppFailureType.validation &&
              failure.message.contains('Matrix')
          ? failure.message
          : null;
      final backendApiMessage =
          failure.type == AppFailureType.validation &&
              failure.message.contains('backend API')
          ? failure.message
          : null;

      state = state.copyWith(
        isSaving: false,
        issuerError: issuerMessage,
        clientIdError: clientIdMessage,
        matrixError: matrixMessage,
        backendApiError: backendApiMessage,
        saveFailure: failure.type == AppFailureType.validation ? null : failure,
        clearIssuerError: issuerMessage == null,
        clearClientIdError: clientIdMessage == null,
        clearMatrixError: matrixMessage == null,
        clearBackendApiError: backendApiMessage == null,
        clearSaveFailure: failure.type == AppFailureType.validation,
      );
      return null;
    } catch (error) {
      state = state.copyWith(
        isSaving: false,
        saveFailure: AppFailure.unknown(
          'Unable to save the server configuration.',
          cause: error,
        ),
      );
      return null;
    }
  }

  ServiceEndpoints? _tryDeriveFromIssuer(String issuerUrl) {
    if (issuerUrl.trim().isEmpty) {
      return null;
    }

    try {
      final parsedIssuer = ref
          .read(serviceEndpointDeriverProvider)
          .parseIssuerUrl(issuerUrl);
      return ref.read(serviceEndpointDeriverProvider).derive(parsedIssuer);
    } on AppFailure {
      return null;
    }
  }

  String _validateClientId(String clientId) {
    final trimmed = clientId.trim();
    return trimmed.isEmpty ? oidcDefaultClientId : trimmed;
  }

  String _authSignature(String issuerUrl, String clientId) {
    return '${issuerUrl.trim()}::${clientId.trim()}';
  }

  String _matrixSignature(String matrixHomeserverUrl) {
    return matrixHomeserverUrl.trim();
  }

  String _backendApiSignature(String backendApiBaseUrl) {
    return backendApiBaseUrl.trim();
  }
}

@riverpod
Future<ServerConfiguration?> savedServerConfiguration(Ref ref) {
  return ref.watch(serverConfigurationRepositoryProvider).loadConfiguration();
}
