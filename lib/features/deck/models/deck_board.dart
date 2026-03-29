/// A stub deck board model.
///
/// Will be replaced with the Deck API type once
/// the Nextcloud integration is built.
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
