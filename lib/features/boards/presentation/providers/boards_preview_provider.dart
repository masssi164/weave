import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/boards/data/repositories/backend_boards_preview_repository.dart';
import 'package:weave/features/boards/data/repositories/static_boards_preview_repository.dart';
import 'package:weave/features/boards/domain/entities/board_preview.dart';
import 'package:weave/features/boards/domain/repositories/boards_preview_repository.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

final boardsPreviewRepositoryProvider = Provider<BoardsPreviewRepository>(
  (ref) => const StaticBoardsPreviewRepository(),
);

final boardsPreviewProvider = FutureProvider<BoardPreview>((ref) async {
  final staticRepository = ref.watch(boardsPreviewRepositoryProvider);
  final WeaveAuthenticatedSession? session;
  try {
    session = await ref.watch(weaveAuthenticatedSessionProvider.future);
  } on AppFailure {
    return staticRepository.loadPreview();
  }
  if (session == null) {
    return staticRepository.loadPreview();
  }

  final backendCapabilities = await _loadBackendCapabilities(ref);
  if (_backendBoardsCapabilityBlocksPreview(backendCapabilities)) {
    return const BoardPreview.backendBlocked();
  }

  final backendRepository = BackendBoardsPreviewRepository(
    httpClient: ref.watch(weaveApiHttpClientProvider),
    apiBaseUrl: session.apiBaseUrl,
    accessToken: session.accessToken,
  );

  try {
    return await backendRepository.loadPreview();
  } on AppFailure {
    return staticRepository.loadPreview();
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

bool _backendBoardsCapabilityBlocksPreview(_BackendCapabilityGate gate) {
  if (gate.failed) {
    return true;
  }
  return gate.snapshot != null && !gate.snapshot!.boards.isReady;
}
