import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/failures/app_failure.dart';
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
