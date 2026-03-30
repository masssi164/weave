import 'package:weave/features/deck/data/services/deck_client.dart';
import 'package:weave/features/deck/domain/entities/deck_board.dart';
import 'package:weave/features/deck/domain/repositories/deck_repository.dart';

class StubDeckRepository implements DeckRepository {
  const StubDeckRepository({required DeckClient client}) : _client = client;

  final DeckClient _client;

  @override
  Future<List<DeckBoard>> loadBoards() async {
    final _ = _client;
    return const [];
  }
}
