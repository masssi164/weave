import 'package:weave/features/files/domain/entities/file_download.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/files/data/services/file_picker_files_export_saver.dart';
import 'package:weave/features/files/data/services/file_picker_files_import_picker.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/domain/services/files_export_saver.dart';
import 'package:weave/features/files/domain/services/files_import_picker.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/presentation/providers/saved_server_configuration_provider.dart';

enum FilesWriteOutcome {
  notCommitted,
  uncertain,
  committed,
  committedRefreshPending,
}

enum FilesUploadPhase { idle, picking, uploading, completed, failed }

enum FilesEntryActionPhase {
  idle,
  creatingFolder,
  createdFolder,
  deletingEntry,
  deletedEntry,
  exportingEntry,
  exportedEntry,
  replacingContent,
  replacedContent,
  failed,
}

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

class FilesEntryActionStatus {
  const FilesEntryActionStatus({
    required this.phase,
    this.entryName,
    this.failure,
    this.destination,
    this.writeOutcome,
    this.createdEntry,
    this.parentFileId,
    this.parentPath,
    this.rootSpaceRef,
  });

  const FilesEntryActionStatus.idle() : this(phase: FilesEntryActionPhase.idle);

  final FilesEntryActionPhase phase;
  final String? entryName;
  final FilesFailure? failure;
  final String? destination;
  final FilesWriteOutcome? writeOutcome;
  final FileEntry? createdEntry;
  final String? parentFileId;
  final String? parentPath;
  final String? rootSpaceRef;
}

class FilesViewState {
  const FilesViewState({
    required this.connectionState,
    this.directoryListing,
    this.directoryFailure,
    this.uploadStatus = const FilesUploadStatus.idle(),
    this.entryActionStatus = const FilesEntryActionStatus.idle(),
    this.rootSpaceRef,
    this.isBusy = false,
  });

  final FilesConnectionState connectionState;
  final DirectoryListing? directoryListing;
  final FilesFailure? directoryFailure;
  final FilesUploadStatus uploadStatus;
  final FilesEntryActionStatus entryActionStatus;
  final String? rootSpaceRef;
  final bool isBusy;

  String get currentPath => directoryListing?.path ?? '/';

  FilesViewState copyWith({
    String? rootSpaceRef,
    bool clearRootSpaceRef = false,
    FilesConnectionState? connectionState,
    DirectoryListing? directoryListing,
    FilesFailure? directoryFailure,
    FilesUploadStatus? uploadStatus,
    FilesEntryActionStatus? entryActionStatus,
    bool? isBusy,
    bool clearDirectoryListing = false,
    bool clearDirectoryFailure = false,
    bool clearUploadStatus = false,
    bool clearEntryActionStatus = false,
  }) {
    return FilesViewState(
      rootSpaceRef: clearRootSpaceRef
          ? null
          : rootSpaceRef ?? this.rootSpaceRef,
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
      entryActionStatus: clearEntryActionStatus
          ? const FilesEntryActionStatus.idle()
          : (entryActionStatus ?? this.entryActionStatus),
      isBusy: isBusy ?? this.isBusy,
    );
  }
}

class FilesController extends AsyncNotifier<FilesViewState> {
  String? _selectedRootSpaceRef;
  int _directorySequence = 0;

  @override
  Future<FilesViewState> build() async {
    _directorySequence++;
    ref.watch(integrationInvalidationProvider(WorkspaceIntegration.files));
    ref.listen(weaveApiMemberSpacesProvider, (_, next) {
      final selected = _selectedRootSpaceRef;
      if (selected == null || next.isLoading) return;
      if (next.asData?.value?.visibleSpaceRefs.contains(selected) != true) {
        _directorySequence++;
        final current = _currentStateOrNull();
        if (current != null) {
          state = AsyncData(
            current.copyWith(
              clearDirectoryListing: true,
              isBusy: false,
              directoryFailure: const FilesFailure.permissionDenied(
                'This Space is no longer available.',
              ),
            ),
          );
        }
      }
    });
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
      if (!connectionState.isConnected) {
        state = AsyncData(
          FilesViewState(
            connectionState: connectionState,
            rootSpaceRef: _selectedRootSpaceRef,
          ),
        );
        return;
      }
      final listing = await _listDirectory('/');
      state = AsyncData(
        FilesViewState(
          rootSpaceRef: _selectedRootSpaceRef,
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
    _directorySequence++;
    _selectedRootSpaceRef = null;
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

  Future<void> selectRootSpace(String? spaceRef) async {
    final current = _currentStateOrNull();
    if (current == null || current.isBusy) return;
    if (spaceRef != null &&
        ref
                .read(weaveApiMemberSpacesProvider)
                .asData
                ?.value
                ?.visibleSpaceRefs
                .contains(spaceRef) !=
            true) {
      state = AsyncData(
        current.copyWith(
          clearDirectoryListing: true,
          directoryFailure: const FilesFailure.permissionDenied(
            'This Space is not available.',
          ),
        ),
      );
      return;
    }
    _selectedRootSpaceRef = spaceRef;
    state = AsyncData(
      current.copyWith(
        rootSpaceRef: spaceRef,
        clearRootSpaceRef: spaceRef == null,
        clearDirectoryListing: true,
        clearDirectoryFailure: true,
        clearUploadStatus: true,
        clearEntryActionStatus: true,
      ),
    );
    await _loadDirectory('/');
  }

  Future<DirectoryListing> _listDirectory(String path) {
    final repository = _repository;
    if (repository is FilesSpaceRepository) {
      return (repository as FilesSpaceRepository).listDirectory(
        path,
        spaceRef: _selectedRootSpaceRef,
      );
    }
    if (_selectedRootSpaceRef != null) {
      throw const FilesFailure.unsupportedPlatform(
        'Shared Files is not available.',
      );
    }
    return repository.listDirectory(path);
  }

  Future<void> _uploadFile(
    String path,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
  }) {
    final repository = _repository;
    if (repository is FilesSpaceRepository) {
      return (repository as FilesSpaceRepository).uploadFile(
        path,
        request,
        spaceRef: _selectedRootSpaceRef,
        onProgress: onProgress,
      );
    }
    if (_selectedRootSpaceRef != null) {
      throw const FilesFailure.unsupportedPlatform(
        'Shared Files is not available.',
      );
    }
    return repository.uploadFile(path, request, onProgress: onProgress);
  }

  Future<void> pickAndReplace(FileEntry entry) async {
    final current = _currentStateOrNull();
    final repository = _repository;
    if (current == null || current.isBusy || !entry.allows('updateContent')) {
      return;
    }
    if (repository is! FilesContentReplacementRepository) return;
    final sequence = _directorySequence;
    state = AsyncData(
      current.copyWith(
        isBusy: true,
        entryActionStatus: FilesEntryActionStatus(
          phase: FilesEntryActionPhase.replacingContent,
          entryName: entry.name,
        ),
      ),
    );
    try {
      final request = await _importPicker.pickFile();
      if (sequence != _directorySequence || !ref.mounted) return;
      if (request == null) {
        state = AsyncData(current.copyWith(isBusy: false));
        return;
      }
      await (repository as FilesContentReplacementRepository).replaceContent(
        entry,
        request,
      );
      if (sequence != _directorySequence || !ref.mounted) return;
      final listing = await _listDirectory(current.currentPath);
      if (sequence != _directorySequence || !ref.mounted) return;
      state = AsyncData(
        current.copyWith(
          directoryListing: listing,
          isBusy: false,
          entryActionStatus: FilesEntryActionStatus(
            phase: FilesEntryActionPhase.replacedContent,
            entryName: entry.name,
          ),
        ),
      );
    } on FilesFailure catch (failure) {
      if (sequence != _directorySequence || !ref.mounted) return;
      state = AsyncData(
        current.copyWith(
          isBusy: false,
          clearDirectoryListing:
              failure.type == FilesFailureType.permissionDenied ||
              failure.type == FilesFailureType.invalidCredentials,
          entryActionStatus: FilesEntryActionStatus(
            phase: FilesEntryActionPhase.failed,
            entryName: entry.name,
            failure: failure,
          ),
        ),
      );
    } catch (error) {
      if (sequence != _directorySequence || !ref.mounted) return;
      state = AsyncData(
        current.copyWith(
          isBusy: false,
          entryActionStatus: FilesEntryActionStatus(
            phase: FilesEntryActionPhase.failed,
            entryName: entry.name,
            failure: FilesFailure.unknown(
              'Unable to update the file.',
              cause: error,
            ),
          ),
        ),
      );
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

    final sequence = _directorySequence;
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
      if (sequence != _directorySequence || !ref.mounted) return;
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
      if (sequence != _directorySequence || !ref.mounted) return;
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

    if (sequence != _directorySequence || !ref.mounted) return;
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
      await _uploadFile(
        directoryPath,
        uploadRequest,
        onProgress: (uploadedBytes, totalBytes) {
          final latest = _currentStateOrNull();
          if (latest == null ||
              sequence != _directorySequence ||
              !ref.mounted) {
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
      if (sequence != _directorySequence || !ref.mounted) return;
      final listing = await _listDirectory(directoryPath);
      if (sequence != _directorySequence || !ref.mounted) return;
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
      if (sequence != _directorySequence || !ref.mounted) return;
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

  Future<FilesEntryActionStatus?> createFolder(String rawName) async {
    final current = _currentStateOrNull();
    final sequence = _directorySequence;
    if (current == null || current.isBusy) {
      return null;
    }

    final folderName = rawName.trim();
    if (folderName.isEmpty) {
      state = AsyncData(
        current.copyWith(
          entryActionStatus: const FilesEntryActionStatus(
            phase: FilesEntryActionPhase.failed,
            failure: FilesFailure.configuration(
              'Enter a folder name before creating it.',
            ),
          ),
        ),
      );
      return null;
    }

    final mutationRepository = _mutationRepositoryOrNull();
    if (mutationRepository == null) {
      state = AsyncData(
        current.copyWith(
          entryActionStatus: const FilesEntryActionStatus(
            phase: FilesEntryActionPhase.failed,
            failure: FilesFailure.unsupportedPlatform(
              'Folder creation is unavailable for this files connection.',
            ),
          ),
        ),
      );
      return null;
    }

    final directoryPath = current.currentPath;
    final rootSpace = _selectedRootSpaceRef;
    final repository = _repository;
    bool currentContext() =>
        ref.mounted &&
        sequence == _directorySequence &&
        identical(_repository, repository) &&
        _selectedRootSpaceRef == rootSpace;
    FileEntry? created;
    state = AsyncData(
      current.copyWith(
        isBusy: true,
        entryActionStatus: FilesEntryActionStatus(
          phase: FilesEntryActionPhase.creatingFolder,
          entryName: folderName,
        ),
        clearDirectoryFailure: true,
      ),
    );

    try {
      if (mutationRepository is FilesCapturedParentRepository) {
        final parentFileId = current.directoryListing?.parentFileId;
        final ownerKey = current.directoryListing?.ownerKey;
        if (parentFileId == null || ownerKey == null) {
          throw const FilesFailure.protocol(
            'The folder identity is unavailable.',
          );
        }
        created = await (mutationRepository as FilesCapturedParentRepository)
            .createFolderInParent(
              parentFileId: parentFileId,
              ownerKey: ownerKey,
              parentPath: directoryPath,
              name: folderName,
              spaceRef: rootSpace,
            );
      } else if (mutationRepository is FilesSpaceRepository) {
        created = await (mutationRepository as FilesSpaceRepository)
            .createFolder(
              parentPath: directoryPath,
              name: folderName,
              spaceRef: rootSpace,
            );
      } else {
        if (_selectedRootSpaceRef != null) {
          throw const FilesFailure.unsupportedPlatform(
            'Shared Files is not available.',
          );
        }
        created = await mutationRepository.createFolder(
          parentPath: directoryPath,
          name: folderName,
        );
      }
      final acknowledgement = FilesEntryActionStatus(
        phase: FilesEntryActionPhase.createdFolder,
        entryName: folderName,
        writeOutcome: FilesWriteOutcome.committedRefreshPending,
        createdEntry: created,
        parentFileId: current.directoryListing?.parentFileId,
        parentPath: directoryPath,
        rootSpaceRef: rootSpace,
      );
      if (!currentContext()) return acknowledgement;
      DirectoryListing? listing;
      FilesFailure? refreshFailure;
      try {
        listing = await _listDirectory(directoryPath);
        if (listing.parentFileId != acknowledgement.parentFileId) {
          listing = null;
          throw const FilesFailure.protocol(
            'The folder changed before refresh completed.',
          );
        }
        if (!listing.entries.any((entry) => entry.id == created!.id)) {
          refreshFailure = const FilesFailure.unknown(
            'Created folder is not visible in the current listing yet.',
          );
        }
      } on FilesFailure catch (failure) {
        refreshFailure = failure;
      } catch (error) {
        refreshFailure = FilesFailure.unknown(
          'Unable to refresh the folder.',
          cause: error,
        );
      }
      if (!currentContext()) return acknowledgement;
      final latest = _currentStateOrNull() ?? current;
      state = AsyncData(
        latest.copyWith(
          directoryListing: listing,
          clearDirectoryListing:
              refreshFailure?.type == FilesFailureType.invalidCredentials ||
              refreshFailure?.type == FilesFailureType.permissionDenied,
          isBusy: false,
          directoryFailure: refreshFailure,
          clearDirectoryFailure: refreshFailure == null,
          entryActionStatus: FilesEntryActionStatus(
            phase: FilesEntryActionPhase.createdFolder,
            entryName: folderName,
            writeOutcome: refreshFailure == null
                ? FilesWriteOutcome.committed
                : FilesWriteOutcome.committedRefreshPending,
            createdEntry: created,
            parentFileId: current.directoryListing?.parentFileId,
            parentPath: directoryPath,
            rootSpaceRef: rootSpace,
            failure: refreshFailure,
          ),
        ),
      );
      return state.asData?.value.entryActionStatus;
    } on FilesFailure catch (failure) {
      if (!currentContext()) return null;
      final latest = _currentStateOrNull() ?? current;
      state = AsyncData(
        latest.copyWith(
          isBusy: false,
          clearDirectoryListing:
              failure.type == FilesFailureType.invalidCredentials ||
              failure.type == FilesFailureType.permissionDenied,
          entryActionStatus: FilesEntryActionStatus(
            phase: FilesEntryActionPhase.failed,
            entryName: folderName,
            writeOutcome: failure.writeMayHaveCommitted
                ? FilesWriteOutcome.uncertain
                : FilesWriteOutcome.notCommitted,
            parentFileId: current.directoryListing?.parentFileId,
            parentPath: directoryPath,
            rootSpaceRef: rootSpace,
            failure: failure,
          ),
        ),
      );
    } catch (error) {
      if (!currentContext()) return null;
      state = AsyncData(
        current.copyWith(
          isBusy: false,
          entryActionStatus: FilesEntryActionStatus(
            phase: FilesEntryActionPhase.failed,
            entryName: folderName,
            writeOutcome: FilesWriteOutcome.uncertain,
            parentFileId: current.directoryListing?.parentFileId,
            parentPath: directoryPath,
            rootSpaceRef: rootSpace,
            failure: FilesFailure.unknown(
              'Folder creation could not be confirmed.',
              cause: error,
            ),
          ),
        ),
      );
    }
    return state.asData?.value.entryActionStatus;
  }

  Future<void> retryFolderReadback() async {
    final current = _currentStateOrNull();
    final operation = current?.entryActionStatus;
    if (current == null ||
        current.isBusy ||
        operation?.createdEntry == null ||
        operation?.parentPath != current.currentPath ||
        operation?.rootSpaceRef != _selectedRootSpaceRef) {
      return;
    }
    final sequence = _directorySequence;
    final repository = _repository;
    bool isCurrent() =>
        ref.mounted &&
        sequence == _directorySequence &&
        identical(repository, _repository) &&
        operation?.rootSpaceRef == _selectedRootSpaceRef;
    state = AsyncData(current.copyWith(isBusy: true));
    try {
      final listing = await _listDirectory(operation!.parentPath!);
      if (!isCurrent()) return;
      if (listing.parentFileId != operation.parentFileId) {
        throw const FilesFailure.protocol(
          'The folder changed before refresh completed.',
        );
      }
      final confirmed = listing.entries.any(
        (entry) => entry.id == operation.createdEntry!.id,
      );
      state = AsyncData(
        current.copyWith(
          directoryListing: listing,
          isBusy: false,
          clearDirectoryFailure: confirmed,
          entryActionStatus: FilesEntryActionStatus(
            phase: FilesEntryActionPhase.createdFolder,
            entryName: operation.entryName,
            createdEntry: operation.createdEntry,
            parentFileId: operation.parentFileId,
            parentPath: operation.parentPath,
            rootSpaceRef: operation.rootSpaceRef,
            writeOutcome: confirmed
                ? FilesWriteOutcome.committed
                : FilesWriteOutcome.committedRefreshPending,
          ),
        ),
      );
    } on FilesFailure catch (failure) {
      if (!isCurrent()) return;
      state = AsyncData(
        current.copyWith(
          isBusy: false,
          directoryFailure: failure,
          clearDirectoryListing:
              failure.type == FilesFailureType.permissionDenied ||
              failure.type == FilesFailureType.invalidCredentials,
        ),
      );
    } catch (_) {
      if (!isCurrent()) return;
      state = AsyncData(
        current.copyWith(
          isBusy: false,
          directoryFailure: const FilesFailure.unknown(
            'Folder readback is unavailable.',
          ),
        ),
      );
    }
  }

  Future<void> deleteEntry(FileEntry entry) async {
    final current = _currentStateOrNull();
    if (current == null || current.isBusy) {
      return;
    }

    final mutationRepository = _mutationRepositoryOrNull();
    if (mutationRepository == null) {
      state = AsyncData(
        current.copyWith(
          entryActionStatus: FilesEntryActionStatus(
            phase: FilesEntryActionPhase.failed,
            entryName: entry.name,
            failure: const FilesFailure.unsupportedPlatform(
              'Delete is unavailable for this files connection.',
            ),
          ),
        ),
      );
      return;
    }

    final directoryPath = current.currentPath;
    state = AsyncData(
      current.copyWith(
        isBusy: true,
        entryActionStatus: FilesEntryActionStatus(
          phase: FilesEntryActionPhase.deletingEntry,
          entryName: entry.name,
        ),
        clearDirectoryFailure: true,
      ),
    );

    try {
      await mutationRepository.deleteEntry(entry);
      final listing = await _listDirectory(directoryPath);
      final latest = _currentStateOrNull() ?? current;
      state = AsyncData(
        latest.copyWith(
          directoryListing: listing,
          isBusy: false,
          entryActionStatus: FilesEntryActionStatus(
            phase: FilesEntryActionPhase.deletedEntry,
            entryName: entry.name,
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
          entryActionStatus: FilesEntryActionStatus(
            phase: FilesEntryActionPhase.failed,
            entryName: entry.name,
            failure: failure,
          ),
          clearDirectoryListing:
              failure.type == FilesFailureType.invalidCredentials,
        ),
      );
    }
  }

  Future<FileDownload> downloadForPreview(FileEntry entry) async {
    final current = _currentStateOrNull();
    final repository = _exportRepositoryOrNull();
    final sequence = _directorySequence;
    if (current == null ||
        repository == null ||
        entry.isDirectory ||
        !entry.allows('download')) {
      throw const FilesFailure.permissionDenied('This file cannot be opened.');
    }
    final download = await repository.downloadFile(entry);
    if (!ref.mounted ||
        sequence != _directorySequence ||
        _currentStateOrNull()?.directoryListing?.entries.any(
              (item) => item.id == entry.id,
            ) !=
            true) {
      throw const FilesFailure.permissionDenied(
        'This file is no longer available.',
      );
    }
    return download;
  }

  Future<void> exportEntry(FileEntry entry) async {
    final current = _currentStateOrNull();
    final sequence = _directorySequence;
    if (current == null || current.isBusy) {
      return;
    }

    if (entry.isDirectory) {
      state = AsyncData(
        current.copyWith(
          entryActionStatus: FilesEntryActionStatus(
            phase: FilesEntryActionPhase.failed,
            entryName: entry.name,
            failure: const FilesFailure.configuration(
              'Folders cannot be exported as a single file yet.',
            ),
          ),
        ),
      );
      return;
    }

    final exportRepository = _exportRepositoryOrNull();
    if (exportRepository == null) {
      state = AsyncData(
        current.copyWith(
          entryActionStatus: FilesEntryActionStatus(
            phase: FilesEntryActionPhase.failed,
            entryName: entry.name,
            failure: const FilesFailure.unsupportedPlatform(
              'File export is unavailable for this files connection.',
            ),
          ),
        ),
      );
      return;
    }

    state = AsyncData(
      current.copyWith(
        isBusy: true,
        entryActionStatus: FilesEntryActionStatus(
          phase: FilesEntryActionPhase.exportingEntry,
          entryName: entry.name,
        ),
        clearDirectoryFailure: true,
      ),
    );

    try {
      final download = await exportRepository.downloadFile(entry);
      if (sequence != _directorySequence || !ref.mounted) return;
      final result = await _exportSaver.save(download);
      if (sequence != _directorySequence || !ref.mounted) return;
      final latest = _currentStateOrNull() ?? current;
      state = AsyncData(
        latest.copyWith(
          isBusy: false,
          entryActionStatus: result == null
              ? const FilesEntryActionStatus.idle()
              : FilesEntryActionStatus(
                  phase: FilesEntryActionPhase.exportedEntry,
                  entryName: result.fileName,
                  destination: result.destination,
                ),
          clearDirectoryFailure: true,
        ),
      );
    } on FilesFailure catch (failure) {
      if (sequence != _directorySequence || !ref.mounted) return;
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
          entryActionStatus: FilesEntryActionStatus(
            phase: FilesEntryActionPhase.failed,
            entryName: entry.name,
            failure: failure,
          ),
          clearDirectoryListing:
              failure.type == FilesFailureType.invalidCredentials,
        ),
      );
    }
  }

  Future<void> _loadDirectory(String path) async {
    final sequence = ++_directorySequence;
    final current = _currentStateOrNull();
    if (current != null) {
      state = AsyncData(
        current.copyWith(isBusy: true, clearDirectoryFailure: true),
      );
    } else {
      state = const AsyncLoading();
    }

    try {
      final listing = await _listDirectory(path);
      if (sequence != _directorySequence || !ref.mounted) return;
      final baseState = current ?? await _loadInitialState();
      state = AsyncData(
        baseState.copyWith(
          directoryListing: listing,
          isBusy: false,
          clearDirectoryFailure: true,
        ),
      );
    } on FilesFailure catch (failure) {
      if (sequence != _directorySequence || !ref.mounted) return;
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
    final sequence = _directorySequence;
    final connectionState = await _repository.restoreConnection();
    if (!connectionState.isConnected) {
      return FilesViewState(
        connectionState: connectionState,
        rootSpaceRef: _selectedRootSpaceRef,
      );
    }

    try {
      final listing = await _listDirectory('/');
      if (sequence != _directorySequence) {
        throw const FilesFailure.permissionDenied(
          'This Space is no longer available.',
        );
      }
      return FilesViewState(
        rootSpaceRef: _selectedRootSpaceRef,
        connectionState: connectionState,
        directoryListing: listing,
      );
    } on FilesFailure catch (failure) {
      if (failure.type == FilesFailureType.invalidCredentials) {
        return FilesViewState(
          rootSpaceRef: _selectedRootSpaceRef,
          connectionState: _connectionStateForFailure(connectionState, failure),
          directoryFailure: failure,
        );
      }

      return FilesViewState(
        rootSpaceRef: _selectedRootSpaceRef,
        connectionState: connectionState,
        directoryFailure: failure,
      );
    }
  }

  FilesRepository get _repository => ref.read(filesRepositoryProvider);

  FilesExportRepository? _exportRepositoryOrNull() {
    final repository = _repository;
    if (repository is FilesExportRepository) {
      return repository as FilesExportRepository;
    }
    return null;
  }

  FilesEntryMutationRepository? _mutationRepositoryOrNull() {
    final repository = _repository;
    if (repository is FilesEntryMutationRepository) {
      return repository as FilesEntryMutationRepository;
    }
    return null;
  }

  FilesImportPicker get _importPicker => ref.read(filesImportPickerProvider);

  FilesExportSaver get _exportSaver => ref.read(filesExportSaverProvider);

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
