import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/deck/presentation/deck_screen.dart';

import '../../helpers/test_app.dart';

void main() {
  group('DeckScreen', () {
    testWidgets('renders the hidden provider-neutral boards preview', (
      tester,
    ) async {
      await tester.pumpWidget(createTestApp(const DeckScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Future boards/tasks preview'), findsOneWidget);
      expect(find.text('Provider-neutral model'), findsOneWidget);
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
