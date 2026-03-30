/// Stub deck entity to be replaced by Deck-backed domain models later.
class DeckBoard {
  const DeckBoard({
    required this.id,
    required this.title,
    required this.color,
    required this.cardCount,
  });

  final String id;
  final String title;
  final String color;
  final int cardCount;
}
