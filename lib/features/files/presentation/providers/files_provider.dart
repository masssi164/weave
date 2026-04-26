import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/files/data/services/file_picker_files_import_picker.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/domain/services/files_import_picker.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';

enum FilesUploadPhase { idle, picking, uploading, completed, failed }

class FilesUploadStatus {
  const FilesUploadStatus({
    required this.phase,
    this.fileName,
    this.uploadedBytes = 0,
    this.totalBytes = 0,
    this.failure,
  });

  const FilesUploadStatus.idle() : this(phase: FilesUploadPhase.idle);

  final FilesUploadPhase phase;
  final String? fileName;
  final int uploadedBytes;
  final int totalBytes;
  final FilesFailure? failure;

  bool get isActive =>
      phase == FilesUploadPhase.picking || phase == FilesUploadPhase.uploading;

  double? get progressFraction {
    if (phase == FilesUploadPhase.completed) {
      return 1;
    }
    if (phase != FilesUploadPhase.uploading || totalBytes <= 0) {
      return null;
    }
    return (uploadedBytes / totalBytes).clamp(0, 1).toDouble();
  }
}

class FilesViewState {
  const FilesViewState({
    required this.connectionState,
    this.directoryListing,
    this.directoryFailure,
    this.uploadStatus = const FilesUploadStatus.idle(),
    this.isBusy = false,
  });

  final FilesConnectionState connectionState;
  final DirectoryListing? directoryListing;
  final FilesFailure? directoryFailure;
  final FilesUploadStatus uploadStatus;
  final bool isBusy;

  String get currentPath => directoryListing?.path ?? '/';

  FilesViewState copyWith({
    FilesConnectionState? connectionState,
    DirectoryListing? directoryListing,
    FilesFailure? directoryFailure,
    FilesUploadStatus? uploadStatus,
    bool? isBusy,
    bool clearDirectoryListing = false,
    bool clearDirectoryFailure = false,
    bool clearUploadStatus = false,
  }) {
    return FilesViewState(
      connectionState: connectionState ?? this.connectionState,
      directoryListing: clearDirectoryListing
          ? null
          : (directoryListing ?? this.directoryListing),
      directoryFailure: clearDirectoryFailure
          ? null
          : (directoryFailure ?? this.directoryFailure),
      uploadStatus: clearUploadStatus
          ? const FilesUploadStatus.idle()
          : (uploadStatus ?? this.uploadStatus),
      isBusy: isBusy ?? this.isBusy,
    );
  }
}

class FilesController extends AsyncNotifier<FilesViewState> {
  @override
  Future<FilesViewState> build() async {
    ref.watch(integrationInvalidationProvider(WorkspaceIntegration.nextcloud));
    await ref.watch(savedServerConfigurationProvider.future);
    return _loadInitialState();
  }

  Future<void> connect() async {
    final current = _currentStateOrNull();
    if (current != null) {
      state = AsyncData(
        current.copyWith(isBusy: true, clearDirectoryFailure: true),
      );
    } else {
      state = const AsyncLoading();
    }

    try {
      final connectionState = await _repository.connect();
      final listing = await _repository.listDirectory('/');
      state = AsyncData(
        FilesViewState(
          connectionState: connectionState,
          directoryListing: listing,
        ),
      );
    } on FilesFailure catch (failure) {
      final fallbackState =
          current ??
          const FilesViewState(
            connectionState: FilesConnectionState.disconnected(),
          );
      state = AsyncData(
        fallbackState.copyWith(
          connectionState: _connectionStateForFailure(
            fallbackState.connectionState,
            failure,
          ),
          directoryFailure: failure,
          isBusy: false,
          clearDirectoryListing:
              failure.type == FilesFailureType.invalidCredentials,
        ),
      );
    }
  }

  Future<void> disconnect() async {
    final current = _currentStateOrNull();
    if (current != null) {
      state = AsyncData(
        current.copyWith(isBusy: true, clearDirectoryFailure: true),
      );
    }

    try {
      await _repository.disconnect();
      final restored = await _repository.restoreConnection();
      state = AsyncData(FilesViewState(connectionState: restored));
    } on FilesFailure catch (failure) {
      if (current != null) {
        state = AsyncData(
          current.copyWith(directoryFailure: failure, isBusy: false),
        );
      } else {
        state = AsyncError(failure, StackTrace.current);
      }
    }
  }

  Future<void> refresh() =>
      _loadDirectory(_currentStateOrNull()?.currentPath ?? '/');

  Future<void> openDirectory(String path) => _loadDirectory(path);

  Future<void> goUp() async {
    final currentPath = _currentStateOrNull()?.currentPath ?? '/';
    if (currentPath == '/') {
      return;
    }

    final segments = currentPath.split('/')
      ..removeWhere((segment) => segment.isEmpty);
    if (segments.isNotEmpty) {
      segments.removeLast();
    }
    final nextPath = segments.isEmpty ? '/' : '/${segments.join('/')}';
    await _loadDirectory(nextPath);
  }

  Future<void> pickAndUpload() async {
    final current = _currentStateOrNull();
    if (current == null || current.isBusy) {
      return;
    }

    state = AsyncData(
      current.copyWith(
        isBusy: true,
        uploadStatus: const FilesUploadStatus(phase: FilesUploadPhase.picking),
        clearDirectoryFailure: true,
      ),
    );

    FileUploadRequest? request;
    try {
      request = await _importPicker.pickFile();
    } on FilesFailure catch (failure) {
      state = AsyncData(
        current.copyWith(
          isBusy: false,
          uploadStatus: FilesUploadStatus(
            phase: FilesUploadPhase.failed,
            failure: failure,
          ),
        ),
      );
      return;
    } catch (error) {
      state = AsyncData(
        current.copyWith(
          isBusy: false,
          uploadStatus: FilesUploadStatus(
            phase: FilesUploadPhase.failed,
            failure: FilesFailure.unknown(
              'Unable to choose a file for upload.',
              cause: error,
            ),
          ),
        ),
      );
      return;
    }

    if (request == null) {
      state = AsyncData(
        current.copyWith(isBusy: false, clearUploadStatus: true),
      );
      return;
    }

    final uploadRequest = request;
    final directoryPath = current.currentPath;
    state = AsyncData(
      current.copyWith(
        isBusy: true,
        uploadStatus: FilesUploadStatus(
          phase: FilesUploadPhase.uploading,
          fileName: uploadRequest.fileName,
          totalBytes: uploadRequest.sizeInBytes,
        ),
      ),
    );

    try {
      await _repository.uploadFile(
        directoryPath,
        uploadRequest,
        onProgress: (uploadedBytes, totalBytes) {
          final latest = _currentStateOrNull();
          if (latest == null) {
            return;
          }
          state = AsyncData(
            latest.copyWith(
              uploadStatus: FilesUploadStatus(
                phase: FilesUploadPhase.uploading,
                fileName: uploadRequest.fileName,
                uploadedBytes: uploadedBytes,
                totalBytes: totalBytes,
              ),
            ),
          );
        },
      );
      final listing = await _repository.listDirectory(directoryPath);
      final latest = _currentStateOrNull() ?? current;
      state = AsyncData(
        latest.copyWith(
          directoryListing: listing,
          isBusy: false,
          uploadStatus: FilesUploadStatus(
            phase: FilesUploadPhase.completed,
            fileName: uploadRequest.fileName,
            uploadedBytes: uploadRequest.sizeInBytes,
            totalBytes: uploadRequest.sizeInBytes,
          ),
          clearDirectoryFailure: true,
        ),
      );
    } on FilesFailure catch (failure) {
      final latest = _currentStateOrNull() ?? current;
      state = AsyncData(
        latest.copyWith(
          connectionState: _connectionStateForFailure(
            latest.connectionState,
            failure,
          ),
          directoryFailure: failure.type == FilesFailureType.invalidCredentials
              ? failure
              : latest.directoryFailure,
          isBusy: false,
          uploadStatus: FilesUploadStatus(
            phase: FilesUploadPhase.failed,
            fileName: uploadRequest.fileName,
            failure: failure,
          ),
          clearDirectoryListing:
              failure.type == FilesFailureType.invalidCredentials,
        ),
      );
    }
  }

  Future<void> _loadDirectory(String path) async {
    final current = _currentStateOrNull();
    if (current != null) {
      state = AsyncData(
        current.copyWith(isBusy: true, clearDirectoryFailure: true),
      );
    } else {
      state = const AsyncLoading();
    }

    try {
      final listing = await _repository.listDirectory(path);
      final baseState = current ?? await _loadInitialState();
      state = AsyncData(
        baseState.copyWith(
          directoryListing: listing,
          isBusy: false,
          clearDirectoryFailure: true,
        ),
      );
    } on FilesFailure catch (failure) {
      final baseState = current ?? await _loadInitialState();
      if (failure.type == FilesFailureType.invalidCredentials) {
        state = AsyncData(
          FilesViewState(
            connectionState: _connectionStateForFailure(
              baseState.connectionState,
              failure,
            ),
            directoryFailure: failure,
          ),
        );
        return;
      }

      state = AsyncData(
        baseState.copyWith(directoryFailure: failure, isBusy: false),
      );
    }
  }

  Future<FilesViewState> _loadInitialState() async {
    final connectionState = await _repository.restoreConnection();
    if (!connectionState.isConnected) {
      return FilesViewState(connectionState: connectionState);
    }

    try {
      final listing = await _repository.listDirectory('/');
      return FilesViewState(
        connectionState: connectionState,
        directoryListing: listing,
      );
    } on FilesFailure catch (failure) {
      if (failure.type == FilesFailureType.invalidCredentials) {
        return FilesViewState(
          connectionState: _connectionStateForFailure(connectionState, failure),
          directoryFailure: failure,
        );
      }

      return FilesViewState(
        connectionState: connectionState,
        directoryFailure: failure,
      );
    }
  }

  FilesRepository get _repository => ref.read(filesRepositoryProvider);

  FilesImportPicker get _importPicker => ref.read(filesImportPickerProvider);

  FilesViewState? _currentStateOrNull() {
    return state.hasValue ? state.requireValue : null;
  }

  FilesConnectionState _connectionStateForFailure(
    FilesConnectionState currentConnectionState,
    FilesFailure failure,
  ) {
    if (failure.type != FilesFailureType.invalidCredentials ||
        currentConnectionState.baseUrl == null) {
      return currentConnectionState;
    }

    return FilesConnectionState.invalid(
      baseUrl: currentConnectionState.baseUrl!,
      accountLabel: currentConnectionState.accountLabel,
      message: failure.message,
    );
  }
}

final filesProvider = AsyncNotifierProvider<FilesController, FilesViewState>(
  FilesController.new,
);
