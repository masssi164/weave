import 'package:weave/features/files/data/services/nextcloud_dav_client.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_connection_state.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_failure.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_session.dart';
import 'package:weave/integrations/nextcloud/domain/services/nextcloud_connection_service.dart';

class NextcloudFilesRepository implements FilesRepository {
  const NextcloudFilesRepository({
    required NextcloudConnectionService connectionService,
    required NextcloudDavClient client,
  }) : _connectionService = connectionService,
       _client = client;

  final NextcloudConnectionService _connectionService;
  final NextcloudDavClient _client;

  @override
  Future<FilesConnectionState> restoreConnection() async {
    try {
      return _mapConnectionState(await _connectionService.restoreConnection());
    } on NextcloudFailure catch (failure) {
      throw _mapFailure(failure);
    }
  }

  @override
  Future<FilesConnectionState> connect() async {
    try {
      return _mapConnectionState(await _connectionService.connect());
    } on NextcloudFailure catch (failure) {
      throw _mapFailure(failure);
    }
  }

  @override
  Future<void> disconnect() async {
    try {
      await _connectionService.disconnect();
    } on NextcloudFailure catch (failure) {
      throw _mapFailure(failure);
    }
  }

  @override
  Future<DirectoryListing> listDirectory(String path) async {
    late NextcloudSession liveSession;
    try {
      liveSession = await _connectionService.requireLiveSession();
    } on NextcloudFailure catch (failure) {
      throw _mapFailure(failure);
    }

    try {
      return await _client.listDirectory(liveSession, path);
    } on NextcloudFailure catch (failure) {
      if (failure.type == NextcloudFailureType.invalidCredentials) {
        try {
          await _connectionService.invalidateSession(liveSession);
        } on NextcloudFailure catch (clearFailure) {
          throw _mapFailure(clearFailure);
        }
      }
      throw _mapFailure(failure);
    }
  }

  @override
  Future<void> uploadFile(
    String directoryPath,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
  }) async {
    late NextcloudSession liveSession;
    try {
      liveSession = await _connectionService.requireLiveSession();
    } on NextcloudFailure catch (failure) {
      throw _mapFailure(failure);
    }

    try {
      await _client.uploadFile(
        liveSession,
        directoryPath: directoryPath,
        fileName: request.fileName,
        sizeInBytes: request.sizeInBytes,
        byteStream: request.byteStream,
        onProgress: onProgress,
      );
    } on NextcloudFailure catch (failure) {
      if (failure.type == NextcloudFailureType.invalidCredentials) {
        try {
          await _connectionService.invalidateSession(liveSession);
        } on NextcloudFailure catch (clearFailure) {
          throw _mapFailure(clearFailure);
        }
      }
      throw _mapFailure(failure);
    }
  }

  FilesConnectionState _mapConnectionState(NextcloudConnectionState state) {
    return switch (state.status) {
      NextcloudConnectionStatus.misconfigured =>
        FilesConnectionState.misconfigured(message: state.message),
      NextcloudConnectionStatus.disconnected =>
        FilesConnectionState.disconnected(
          baseUrl: state.baseUrl,
          message: state.message,
        ),
      NextcloudConnectionStatus.connected => FilesConnectionState.connected(
        baseUrl: state.baseUrl!,
        accountLabel: state.accountLabel!,
      ),
      NextcloudConnectionStatus.invalid => FilesConnectionState.invalid(
        baseUrl: state.baseUrl!,
        accountLabel: state.accountLabel,
        message: state.message,
      ),
    };
  }

  FilesFailure _mapFailure(NextcloudFailure failure) {
    return switch (failure.type) {
      NextcloudFailureType.cancelled => FilesFailure.cancelled(
        failure.message,
        cause: failure.cause,
      ),
      NextcloudFailureType.configuration => FilesFailure.configuration(
        failure.message,
        cause: failure.cause,
      ),
      NextcloudFailureType.sessionRequired => FilesFailure.sessionRequired(
        failure.message,
        cause: failure.cause,
      ),
      NextcloudFailureType.invalidCredentials =>
        FilesFailure.invalidCredentials(failure.message, cause: failure.cause),
      NextcloudFailureType.protocol => FilesFailure.protocol(
        failure.message,
        cause: failure.cause,
      ),
      NextcloudFailureType.storage => FilesFailure.storage(
        failure.message,
        cause: failure.cause,
      ),
      NextcloudFailureType.unsupportedPlatform =>
        FilesFailure.unsupportedPlatform(failure.message, cause: failure.cause),
      NextcloudFailureType.unknown => FilesFailure.unknown(
        failure.message,
        cause: failure.cause,
      ),
    };
  }
}
