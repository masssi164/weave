import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/help/presentation/help_screen.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

Future<void> _expectVisibleAfterScroll(WidgetTester tester, String text) async {
  final finder = find.text(text);
  await tester.scrollUntilVisible(
    finder,
    300,
    scrollable: find.byType(Scrollable),
  );
  expect(finder, findsOneWidget);
}

void main() {
  group('HelpScreen', () {
    testWidgets('renders the English handbook sections with header semantics', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();

      await tester.pumpWidget(
        const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: HelpScreen(),
        ),
      );

      expect(find.text('Help'), findsWidgets);
      expect(find.text('User handbook'), findsOneWidget);
      expect(find.text('Embedded user manual'), findsOneWidget);
      expect(find.text('Manual source: docs/user-handbook.md'), findsOneWidget);
      expect(
        find.text(
          'Constrained embed: no broad script, camera, microphone, or provider access',
        ),
        findsOneWidget,
      );
      await _expectVisibleAfterScroll(tester, 'What Weave is');
      final handbookSemantics = tester
          .getSemantics(find.text('User handbook'))
          .getSemanticsData();
      expect(handbookSemantics.flagsCollection.isHeader, isTrue);

      await _expectVisibleAfterScroll(tester, 'Sign in basics');
      await _expectVisibleAfterScroll(tester, 'Chat');
      await _expectVisibleAfterScroll(tester, 'Files');
      await _expectVisibleAfterScroll(tester, 'Settings, account, and session');
      await _expectVisibleAfterScroll(
        tester,
        'Calendar and Boards availability',
      );
      await _expectVisibleAfterScroll(tester, 'Troubleshooting and recovery');
      await _expectVisibleAfterScroll(tester, 'Privacy and security basics');
      semantics.dispose();
    });

    testWidgets('renders localized German handbook copy', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          locale: Locale('de'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: HelpScreen(),
        ),
      );

      expect(find.text('Hilfe'), findsWidgets);
      expect(find.text('Benutzerhandbuch'), findsOneWidget);
      expect(find.text('Eingebettetes Benutzerhandbuch'), findsOneWidget);
      expect(
        find.text('Handbuchquelle: docs/user-handbook.md'),
        findsOneWidget,
      );
      await _expectVisibleAfterScroll(tester, 'Was Weave ist');
      await _expectVisibleAfterScroll(tester, 'Anmelden: Grundlagen');
      await _expectVisibleAfterScroll(
        tester,
        'Verfügbarkeit von Kalender und Boards',
      );
      await _expectVisibleAfterScroll(
        tester,
        'Datenschutz und Sicherheit: Grundlagen',
      );
    });
  });
}
