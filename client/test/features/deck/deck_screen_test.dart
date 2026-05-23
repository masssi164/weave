import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/deck/presentation/deck_screen.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

import '../../helpers/test_app.dart';

void main() {
  group('DeckScreen', () {
    testWidgets('renders the provider-neutral boards workspace', (
      tester,
    ) async {
      await tester.pumpWidget(
        createTestApp(const DeckScreen(), overrides: _staticPreviewOverrides),
      );
      await tester.pumpAndSettle();

      expect(find.text('Dogfood boards/tasks workspace'), findsOneWidget);
      expect(find.text('Provider-neutral model'), findsOneWidget);
    });

    testWidgets('meets androidTapTargetGuideline', (tester) async {
      await tester.pumpWidget(
        createTestApp(const DeckScreen(), overrides: _staticPreviewOverrides),
      );
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    });

    testWidgets('meets labeledTapTargetGuideline', (tester) async {
      await tester.pumpWidget(
        createTestApp(const DeckScreen(), overrides: _staticPreviewOverrides),
      );
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });
}

final _staticPreviewOverrides = [
  weaveAuthenticatedSessionProvider.overrideWith((ref) async => null),
];
