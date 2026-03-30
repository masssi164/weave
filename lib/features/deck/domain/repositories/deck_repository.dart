import 'package:weave/features/deck/domain/entities/deck_board.dart';

abstract interface class DeckRepository {
  Future<List<DeckBoard>> loadBoards();
}
