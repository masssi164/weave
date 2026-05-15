import 'package:flutter/material.dart';
import 'package:weave/features/boards/presentation/boards_preview_screen.dart';

/// Compatibility wrapper for the hidden legacy Deck route.
///
/// Boards/tasks are active feature-gated scope and use Weave-owned,
/// provider-neutral language. This route is not present in the shell navigation
/// and remains redirected away in the app router until a later promotion spec
/// explicitly enables a boards/tasks module.
class DeckScreen extends StatelessWidget {
  const DeckScreen({super.key});

  @override
  Widget build(BuildContext context) => const BoardsPreviewScreen();
}
