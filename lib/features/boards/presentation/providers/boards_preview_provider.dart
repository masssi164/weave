import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/boards/data/repositories/static_boards_preview_repository.dart';
import 'package:weave/features/boards/domain/entities/board_preview.dart';
import 'package:weave/features/boards/domain/repositories/boards_preview_repository.dart';

final boardsPreviewRepositoryProvider = Provider<BoardsPreviewRepository>(
  (ref) => const StaticBoardsPreviewRepository(),
);

final boardsPreviewProvider = FutureProvider<BoardPreview>((ref) async {
  final repository = ref.watch(boardsPreviewRepositoryProvider);
  return repository.loadPreview();
});
