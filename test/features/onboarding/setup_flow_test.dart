import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/onboarding/presentation/setup_flow.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

void main() {
  group('SetupFlow', () {
    Widget buildApp() {
      return const ProviderScope(
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: SetupFlow(),
        ),
      );
    }

    testWidgets('renders first step — language step', (tester) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      expect(find.text('Your Language'), findsOneWidget);
      expect(find.text('Next'), findsOneWidget);
    });

    testWidgets('advances to confirmation step on Next tap', (tester) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();

      expect(find.text("You're All Set"), findsOneWidget);
      expect(find.text('Finish'), findsOneWidget);
    });

    testWidgets('goes back to language step from confirmation', (tester) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Back'));
      await tester.pumpAndSettle();

      expect(find.text('Your Language'), findsOneWidget);
    });

    testWidgets('meets androidTapTargetGuideline', (tester) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    });

    testWidgets('meets labeledTapTargetGuideline', (tester) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });
}
