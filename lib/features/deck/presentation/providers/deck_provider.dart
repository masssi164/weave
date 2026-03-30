import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/features/deck/data/repositories/stub_deck_repository.dart';
import 'package:weave/features/deck/data/services/deck_client.dart';
import 'package:weave/features/deck/domain/entities/deck_board.dart';
import 'package:weave/features/deck/domain/repositories/deck_repository.dart';

part 'deck_provider.g.dart';

@Riverpod(keepAlive: true)
DeckClient deckClient(Ref ref) => const DeckClient();

@Riverpod(keepAlive: true)
DeckRepository deckRepository(Ref ref) {
  final client = ref.watch(deckClientProvider);
  return StubDeckRepository(client: client);
}

@riverpod
class DeckNotifier extends _$DeckNotifier {
  @override
  Future<List<DeckBoard>> build() async {
    final repository = ref.watch(deckRepositoryProvider);
    return repository.loadBoards();
  }
}
