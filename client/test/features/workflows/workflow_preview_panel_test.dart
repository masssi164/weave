import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/workflows/presentation/providers/workflow_preview_provider.dart';
import 'package:weave/features/workflows/presentation/widgets/workflow_preview_panel.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

void main() {
  testWidgets('renders an accessible non-drag workflow preview', (
    tester,
  ) async {
    final snapshot = const WorkflowPreviewFacade().previewForWorkspace(
      contexts: const <WorkflowContextSeed>[
        WorkflowContextSeed(
          id: '!release:home.internal',
          kind: WorkflowContextSeedKind.channel,
          label: 'Release channel',
        ),
      ],
    );
    final semantics = tester.ensureSemantics();
    try {
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: SingleChildScrollView(
              child: WorkflowPreviewPanel(snapshot: snapshot),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Active workflows'), findsOneWidget);
      expect(find.text('Linear view first'), findsOneWidget);
      expect(find.text('Explicit context only'), findsOneWidget);
      expect(find.text('Governed actions'), findsOneWidget);
      expect(find.text('Prepare a release'), findsOneWidget);
      expect(find.text('Clear release blockers'), findsOneWidget);
      expect(
        find.textContaining('One checklist item still needs an owner'),
        findsOneWidget,
      );
      expect(find.text('Open step'), findsNWidgets(3));
      expect(find.text('Review evidence'), findsNWidgets(3));
      expect(
        find.textContaining('does not continuously read rooms'),
        findsOneWidget,
      );

      final panelSemantics = tester
          .getSemantics(find.byType(WorkflowPreviewPanel))
          .getSemanticsData();
      expect(panelSemantics.label, contains('1 active workflow'));
    } finally {
      semantics.dispose();
    }
  });
}
