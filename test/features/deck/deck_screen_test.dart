import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/deck/presentation/deck_screen.dart';

import '../../helpers/test_app.dart';

void main() {
  group('DeckScreen', () {
    testWidgets('renders without errors', (tester) async {
      await tester.pumpWidget(createTestApp(const DeckScreen()));
      await tester.pumpAndSettle();
    });

    testWidgets('meets androidTapTargetGuideline', (tester) async {
      await tester.pumpWidget(createTestApp(const DeckScreen()));
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    });

    testWidgets('meets labeledTapTargetGuideline', (tester) async {
      await tester.pumpWidget(createTestApp(const DeckScreen()));
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });
}
