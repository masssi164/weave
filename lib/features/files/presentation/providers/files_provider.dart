import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';

class FilesViewState {
  const FilesViewState({
    required this.connectionState,
    this.directoryListing,
    this.directoryFailure,
    this.isBusy = false,
  });

  final FilesConnectionState connectionState;
  final DirectoryListing? directoryListing;
  final FilesFailure? directoryFailure;
  final bool isBusy;

  String get currentPath => directoryListing?.path ?? '/';

  FilesViewState copyWith({
    FilesConnectionState? connectionState,
    DirectoryListing? directoryListing,
    FilesFailure? directoryFailure,
    bool? isBusy,
    bool clearDirectoryListing = false,
    bool clearDirectoryFailure = false,
  }) {
    return FilesViewState(
      connectionState: connectionState ?? this.connectionState,
      directoryListing: clearDirectoryListing
          ? null
          : (directoryListing ?? this.directoryListing),
      directoryFailure: clearDirectoryFailure
          ? null
          : (directoryFailure ?? this.directoryFailure),
      isBusy: isBusy ?? this.isBusy,
    );
  }
}

class FilesController extends AsyncNotifier<FilesViewState> {
  @override
  Future<FilesViewState> build() async {
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
