import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/files/data/repositories/secure_nextcloud_session_repository.dart';
import 'package:weave/features/files/data/services/nextcloud_auth_client.dart';
import 'package:weave/features/files/data/services/nextcloud_client.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/entities/nextcloud_session.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/domain/repositories/nextcloud_session_repository.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

class NextcloudFilesRepository implements FilesRepository {
  const NextcloudFilesRepository({
    required NextcloudAuthClient authClient,
    required NextcloudClient client,
    required NextcloudSessionRepository sessionRepository,
    required ServerConfigurationRepository serverConfigurationRepository,
  }) : _authClient = authClient,
       _client = client,
       _sessionRepository = sessionRepository,
       _serverConfigurationRepository = serverConfigurationRepository;

  final NextcloudAuthClient _authClient;
  final NextcloudClient _client;
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

    final session = await _sessionRepository.readSession();
    if (session == null) {
      return FilesConnectionState.disconnected(
        baseUrl: configuration.serviceEndpoints.nextcloudBaseUrl,
      );
    }

    if (!session.matchesBaseUrl(configuration.serviceEndpoints.nextcloudBaseUrl)) {
      await _sessionRepository.clearSession();
      return FilesConnectionState.disconnected(
        baseUrl: configuration.serviceEndpoints.nextcloudBaseUrl,
      );
    }

    return FilesConnectionState.connected(
      baseUrl: session.baseUrl,
      accountLabel: session.accountLabel,
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

    final session = await _authClient.connect(
      configuration.serviceEndpoints.nextcloudBaseUrl,
    );
    try {
      await _sessionRepository.saveSession(session);
    } on FilesFailure {
      await _authClient.revokeAppPassword(session);
      rethrow;
    }
    return FilesConnectionState.connected(
      baseUrl: session.baseUrl,
      accountLabel: session.accountLabel,
    );
  }

  @override
  Future<void> disconnect() async {
    final session = await _sessionRepository.readSession();
    await _sessionRepository.clearSession();

    if (session != null) {
      await _authClient.revokeAppPassword(session);
    }
  }

  @override
  Future<DirectoryListing> listDirectory(String path) async {
    final session = await _requireSession();
    return _client.listDirectory(session, path);
  }

  Future<NextcloudSession> _requireSession() async {
    final session = await _sessionRepository.readSession();
    if (session == null) {
      throw const FilesFailure.sessionRequired(
        'Connect Nextcloud before browsing files.',
      );
    }

    final configuration = await _loadConfiguration();
    if (configuration == null) {
      throw const FilesFailure.configuration(
        'Finish server setup before browsing Nextcloud files.',
      );
    }

    if (!session.matchesBaseUrl(configuration.serviceEndpoints.nextcloudBaseUrl)) {
      await _sessionRepository.clearSession();
      throw const FilesFailure.sessionRequired(
        'Reconnect Nextcloud because the configured server changed.',
      );
    }

    return session;
  }

  Future<ServerConfiguration?> _loadConfiguration() async {
    final configuration = await _serverConfigurationRepository.loadConfiguration();
    if (configuration == null) {
      return null;
    }

    final nextcloudUrl = configuration.serviceEndpoints.nextcloudBaseUrl.toString().trim();
    if (nextcloudUrl.isEmpty) {
      return null;
    }

    return configuration;
  }
}

final filesRepositoryProvider = Provider<FilesRepository>((ref) {
  return NextcloudFilesRepository(
    authClient: ref.watch(nextcloudAuthClientProvider),
    client: ref.watch(nextcloudClientProvider),
    sessionRepository: ref.watch(nextcloudSessionRepositoryProvider),
    serverConfigurationRepository: ref.watch(serverConfigurationRepositoryProvider),
  );
});
