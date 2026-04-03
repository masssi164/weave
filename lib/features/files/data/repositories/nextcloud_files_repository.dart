import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_session.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/files/data/services/nextcloud_auth_client.dart';
import 'package:weave/features/files/data/services/nextcloud_client.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/entities/nextcloud_session.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/domain/repositories/nextcloud_session_repository.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

class NextcloudFilesRepository implements FilesRepository {
  const NextcloudFilesRepository({
    required NextcloudAuthClient authClient,
    required NextcloudClient client,
    required AuthSessionRepository authSessionRepository,
    required NextcloudSessionRepository sessionRepository,
    required ServerConfigurationRepository serverConfigurationRepository,
  }) : _authClient = authClient,
       _client = client,
       _authSessionRepository = authSessionRepository,
       _sessionRepository = sessionRepository,
       _serverConfigurationRepository = serverConfigurationRepository;

  final NextcloudAuthClient _authClient;
  final NextcloudClient _client;
  final AuthSessionRepository _authSessionRepository;
  final NextcloudSessionRepository _sessionRepository;
  final ServerConfigurationRepository _serverConfigurationRepository;

  @override
  Future<FilesConnectionState> restoreConnection() async {
    final configuration = await _loadConfiguration();
    if (configuration == null) {
      return const FilesConnectionState.misconfigured(
        message: 'Finish server setup before connecting Nextcloud.',
      );
    }

    if (!_usesHttps(configuration.serviceEndpoints.nextcloudBaseUrl)) {
      return const FilesConnectionState.misconfigured(
        message: 'Use an HTTPS Nextcloud URL before connecting files.',
      );
    }

    final configuredBaseUrl = configuration.serviceEndpoints.nextcloudBaseUrl;
    final storedSession = await _readStoredSession(configuredBaseUrl);
    if (storedSession == null) {
      return FilesConnectionState.disconnected(baseUrl: configuredBaseUrl);
    }

    final bearerSession = await _tryResolveBearerSession(
      configuration,
      validateDavAccess: true,
      accountLabelHint: storedSession.accountLabel,
    );
    if (bearerSession != null) {
      await _replaceStoredSession(
        previousSession: storedSession,
        nextSession: bearerSession.toPersistedSession(),
      );
      return FilesConnectionState.connected(
        baseUrl: bearerSession.baseUrl,
        accountLabel: bearerSession.accountLabel,
      );
    }

    if (storedSession.usesOidcBearer) {
      await _sessionRepository.clearSession();
      return FilesConnectionState.disconnected(baseUrl: configuredBaseUrl);
    }

    return FilesConnectionState.connected(
      baseUrl: storedSession.baseUrl,
      accountLabel: storedSession.accountLabel,
    );
  }

  @override
  Future<FilesConnectionState> connect() async {
    final configuration = await _loadConfiguration();
    if (configuration == null) {
      throw const FilesFailure.configuration(
        'Finish server setup before connecting Nextcloud.',
      );
    }

    if (!_usesHttps(configuration.serviceEndpoints.nextcloudBaseUrl)) {
      throw const FilesFailure.configuration(
        'Use an HTTPS Nextcloud URL before connecting files.',
      );
    }

    final configuredBaseUrl = configuration.serviceEndpoints.nextcloudBaseUrl;
    final previousSession = await _readStoredSession(configuredBaseUrl);
    final bearerSession = await _tryResolveBearerSession(
      configuration,
      validateDavAccess: true,
      accountLabelHint: previousSession?.accountLabel,
    );
    if (bearerSession != null) {
      await _replaceStoredSession(
        previousSession: previousSession,
        nextSession: bearerSession.toPersistedSession(),
      );
      return FilesConnectionState.connected(
        baseUrl: bearerSession.baseUrl,
        accountLabel: bearerSession.accountLabel,
      );
    }

    final fallbackSession = await _authClient.connect(configuredBaseUrl);
    await _replaceStoredSession(
      previousSession: previousSession,
      nextSession: fallbackSession.toPersistedSession(),
    );
    return FilesConnectionState.connected(
      baseUrl: fallbackSession.baseUrl,
      accountLabel: fallbackSession.accountLabel,
    );
  }

  @override
  Future<void> disconnect() async {
    final session = await _sessionRepository.readSession();
    await _sessionRepository.clearSession();

    if (session != null && session.usesAppPassword) {
      await _authClient.revokeAppPassword(session);
    }
  }

  @override
  Future<DirectoryListing> listDirectory(String path) async {
    final configuration = await _requireConfiguration();
    final storedSession = await _requireStoredSession(
      configuration.serviceEndpoints.nextcloudBaseUrl,
    );
    final liveSession = await _resolveLiveSession(configuration, storedSession);

    try {
      return await _client.listDirectory(liveSession, path);
    } on FilesFailure catch (failure) {
      if (failure.type == FilesFailureType.invalidCredentials) {
        await _clearStoredSession(storedSession);
      }
      rethrow;
    }
  }

  Future<ServerConfiguration> _requireConfiguration() async {
    final configuration = await _loadConfiguration();
    if (configuration == null) {
      throw const FilesFailure.configuration(
        'Finish server setup before browsing Nextcloud files.',
      );
    }

    if (!_usesHttps(configuration.serviceEndpoints.nextcloudBaseUrl)) {
      throw const FilesFailure.configuration(
        'Use an HTTPS Nextcloud URL before browsing Nextcloud files.',
      );
    }

    return configuration;
  }

  Future<NextcloudSession> _requireStoredSession(Uri configuredBaseUrl) async {
    final session = await _readStoredSession(configuredBaseUrl);
    if (session == null) {
      throw const FilesFailure.sessionRequired(
        'Connect Nextcloud before browsing files.',
      );
    }

    return session;
  }

  Future<NextcloudSession> _resolveLiveSession(
    ServerConfiguration configuration,
    NextcloudSession storedSession,
  ) async {
    if (storedSession.usesAppPassword) {
      return storedSession;
    }

    final bearerSession = await _tryResolveBearerSession(
      configuration,
      validateDavAccess: false,
      accountLabelHint: storedSession.accountLabel,
    );
    if (bearerSession != null) {
      return bearerSession;
    }

    await _sessionRepository.clearSession();
    throw const FilesFailure.invalidCredentials(
      'Reconnect Nextcloud because bearer-token access is no longer available.',
    );
  }

  Future<NextcloudSession?> _readStoredSession(Uri configuredBaseUrl) async {
    final session = await _sessionRepository.readSession();
    if (session == null) {
      return null;
    }

    if (!session.matchesBaseUrl(configuredBaseUrl)) {
      await _clearStoredSession(session);
      return null;
    }

    return session;
  }

  Future<void> _replaceStoredSession({
    required NextcloudSession? previousSession,
    required NextcloudSession nextSession,
  }) async {
    try {
      await _sessionRepository.saveSession(nextSession);
    } on FilesFailure {
      if (nextSession.usesAppPassword) {
        await _authClient.revokeAppPassword(nextSession);
      }
      rethrow;
    }

    if (previousSession != null &&
        previousSession.usesAppPassword &&
        (nextSession.usesOidcBearer ||
            previousSession.appPassword != nextSession.appPassword)) {
      await _authClient.revokeAppPassword(previousSession);
    }
  }

  Future<void> _clearStoredSession(NextcloudSession session) async {
    await _sessionRepository.clearSession();
    if (session.usesAppPassword) {
      await _authClient.revokeAppPassword(session);
    }
  }

  Future<NextcloudSession?> _tryResolveBearerSession(
    ServerConfiguration configuration, {
    required bool validateDavAccess,
    String? accountLabelHint,
  }) async {
    if (!configuration.hasCompleteAuthConfiguration) {
      return null;
    }

    final authState = await _authSessionRepository.restoreSession(
      AuthConfiguration(
        issuer: configuration.oidcIssuerUrl,
        clientId: configuration.oidcClientRegistration.clientId.trim(),
      ),
    );
    final authSession = authState.session;
    if (!authState.isAuthenticated || authSession == null) {
      return null;
    }

    final configuredBaseUrl = configuration.serviceEndpoints.nextcloudBaseUrl;
    for (final token in _bearerTokenCandidates(authSession)) {
      try {
        final bearerSession = await _authClient.createBearerSession(
          configuredBaseUrl: configuredBaseUrl,
          bearerToken: token,
          accountLabelHint: accountLabelHint,
        );
        if (validateDavAccess) {
          await _client.listDirectory(bearerSession, '/');
        }
        return bearerSession;
      } on FilesFailure catch (failure) {
        if (failure.type == FilesFailureType.invalidCredentials ||
            failure.type == FilesFailureType.protocol) {
          continue;
        }
        rethrow;
      }
    }

    return null;
  }

  List<String> _bearerTokenCandidates(AuthSession authSession) {
    final candidates = <String>[];
    final idToken = authSession.idToken?.trim();
    if (idToken != null && idToken.isNotEmpty) {
      // Nextcloud's OCS docs show bearer ID tokens explicitly, with access
      // tokens also supported by user_oidc when the deployment enables it.
      candidates.add(idToken);
    }

    final accessToken = authSession.accessToken.trim();
    if (accessToken.isNotEmpty && !candidates.contains(accessToken)) {
      candidates.add(accessToken);
    }
    return candidates;
  }

  Future<ServerConfiguration?> _loadConfiguration() async {
    final configuration = await _serverConfigurationRepository
        .loadConfiguration();
    if (configuration == null) {
      return null;
    }

    final nextcloudUrl = configuration.serviceEndpoints.nextcloudBaseUrl
        .toString()
        .trim();
    if (nextcloudUrl.isEmpty) {
      return null;
    }

    return configuration;
  }

  bool _usesHttps(Uri uri) => uri.scheme.toLowerCase() == 'https';
}
