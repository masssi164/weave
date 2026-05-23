import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/boards/data/repositories/backend_boards_workspace_repository.dart';
import 'package:weave/features/boards/data/repositories/static_boards_workspace_repository.dart';
import 'package:weave/features/boards/domain/entities/board_workspace.dart';
import 'package:weave/features/boards/domain/repositories/boards_workspace_repository.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

final boardsWorkspaceRepositoryProvider = Provider<BoardsWorkspaceRepository>(
  (ref) => const StaticBoardsWorkspaceRepository(),
);

final boardsWorkspaceProvider = FutureProvider<BoardWorkspace>((ref) async {
  final staticRepository = ref.watch(boardsWorkspaceRepositoryProvider);
  final WeaveAuthenticatedSession? session;
  try {
    session = await ref.watch(weaveAuthenticatedSessionProvider.future);
  } on AppFailure {
    return staticRepository.loadWorkspace();
  }
  if (session == null) {
    return staticRepository.loadWorkspace();
  }

  final backendCapabilities = await _loadBackendCapabilities(ref);
  if (_backendBoardsCapabilityBlocksWorkspace(backendCapabilities)) {
    return const BoardWorkspace.backendBlocked();
  }

  final backendRepository = BackendBoardsWorkspaceRepository(
    httpClient: ref.watch(weaveApiHttpClientProvider),
    apiBaseUrl: session.apiBaseUrl,
    accessToken: session.accessToken,
  );

  try {
    return await backendRepository.loadWorkspace();
  } on AppFailure {
    return const BoardWorkspace.backendBlocked();
  }
});

typedef _BackendCapabilityGate = ({
  bool failed,
  WorkspaceCapabilitySnapshot? snapshot,
});

Future<_BackendCapabilityGate> _loadBackendCapabilities(Ref ref) async {
  try {
    return (
      failed: false,
      snapshot: await ref.watch(
        weaveApiWorkspaceCapabilitySnapshotProvider.future,
      ),
    );
  } on AppFailure {
    return (failed: true, snapshot: null);
  } catch (_) {
    return (failed: true, snapshot: null);
  }
}

bool _backendBoardsCapabilityBlocksWorkspace(_BackendCapabilityGate gate) {
  if (gate.failed) {
    return true;
  }
  return gate.snapshot != null && !gate.snapshot!.boards.isReady;
}
