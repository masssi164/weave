import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/features/deck/models/deck_board.dart';

part 'deck_provider.g.dart';

/// Manages the list of deck boards.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Nextcloud Deck API calls
@riverpod
class DeckNotifier extends _$DeckNotifier {
  @override
  Future<List<DeckBoard>> build() async => [];
}
