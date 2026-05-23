import 'package:weave/features/boards/domain/entities/board_workspace.dart';

abstract interface class BoardsWorkspaceRepository {
  Future<BoardWorkspace> loadWorkspace();
}
