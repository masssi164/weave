import 'package:weave/features/boards/domain/entities/board_preview.dart';

abstract interface class BoardsPreviewRepository {
  Future<BoardPreview> loadPreview();
}
