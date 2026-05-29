import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/core/widgets/state_panel.dart';
import 'package:weave/core/widgets/success_state.dart';

void main() {
  group('StatePanel', () {
    testWidgets('supports success recovery states through the shared path', (
      tester,
    ) async {
      var acknowledged = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: StatePanel(
              variant: StatePanelVariant.success,
              message: 'Upload complete',
              guidance: 'The file is ready for your team.',
              actionLabel: 'Done',
              onAction: () => acknowledged = true,
            ),
          ),
        ),
      );

      expect(find.byIcon(Icons.check_circle_outline), findsOneWidget);
      expect(
        find.bySemanticsLabel(
          'Upload complete. The file is ready for your team. Action: Done',
        ),
        findsOneWidget,
      );

      await tester.tap(find.text('Done'));
      expect(acknowledged, isTrue);
    });
  });

  group('SuccessState', () {
    testWidgets('displays shared success chrome with guidance', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: SuccessState(
              message: 'Saved',
              guidance: 'Your changes are available to the workspace.',
            ),
          ),
        ),
      );

      expect(find.text('Saved'), findsOneWidget);
      expect(
        find.text('Your changes are available to the workspace.'),
        findsOneWidget,
      );
      expect(find.byType(Card), findsOneWidget);
      expect(find.byIcon(Icons.check_circle_outline), findsOneWidget);
      expect(
        find.bySemanticsLabel(
          'Saved. Your changes are available to the workspace.',
        ),
        findsOneWidget,
      );
    });

    testWidgets('renders follow-up action when provided', (tester) async {
      var opened = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SuccessState(
              message: 'Saved',
              actionLabel: 'Open',
              onAction: () => opened = true,
            ),
          ),
        ),
      );

      expect(find.text('Open'), findsOneWidget);
      expect(find.bySemanticsLabel('Saved. Action: Open'), findsOneWidget);
      expect(find.bySemanticsLabel('Open'), findsOneWidget);
      await tester.tap(find.text('Open'));
      expect(opened, isTrue);
    });

    testWidgets('accepts an explicit screen-reader label', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: SuccessState(
              message: 'Saved',
              guidance: 'Your changes are available to the workspace.',
              semanticLabel: 'Settings saved successfully.',
            ),
          ),
        ),
      );

      expect(
        find.bySemanticsLabel('Settings saved successfully.'),
        findsOneWidget,
      );
    });

    testWidgets('meets labeledTapTargetGuideline', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SuccessState(
              message: 'Saved',
              actionLabel: 'Open',
              onAction: () {},
            ),
          ),
        ),
      );

      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });

  group('LoadingState', () {
    testWidgets('displays shared loading chrome and supporting hint', (
      tester,
    ) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: LoadingState(
              message: 'Loading…',
              hint: 'Checking for changes.',
              icon: Icons.folder_outlined,
            ),
          ),
        ),
      );

      expect(find.text('Loading…'), findsOneWidget);
      expect(find.text('Checking for changes.'), findsOneWidget);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.byType(Card), findsOneWidget);
      expect(find.byIcon(Icons.folder_outlined), findsOneWidget);
      expect(
        find.bySemanticsLabel('Loading. Checking for changes.'),
        findsOneWidget,
      );
    });

    testWidgets('accepts an explicit screen-reader label', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: LoadingState(
              message: 'Loading…',
              hint: 'Checking for changes.',
              semanticLabel: 'Files are loading. You can keep waiting.',
            ),
          ),
        ),
      );

      expect(
        find.bySemanticsLabel('Files are loading. You can keep waiting.'),
        findsOneWidget,
      );
    });

    testWidgets('meets labeledTapTargetGuideline', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(body: LoadingState(message: 'Loading…')),
        ),
      );

      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });

  group('EmptyState', () {
    testWidgets('displays shared empty chrome with guidance', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: EmptyState(
              message: 'Nothing here',
              guidance: 'Add something when you are ready.',
              icon: Icons.inbox_outlined,
            ),
          ),
        ),
      );

      expect(find.text('Nothing here'), findsOneWidget);
      expect(find.text('Add something when you are ready.'), findsOneWidget);
      expect(find.byType(Card), findsOneWidget);
      expect(find.byIcon(Icons.inbox_outlined), findsOneWidget);
      expect(
        find.bySemanticsLabel(
          'Nothing here. Add something when you are ready.',
        ),
        findsOneWidget,
      );
    });

    testWidgets('renders CTA when actionLabel and onAction are provided', (
      tester,
    ) async {
      var tapped = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: EmptyState(
              message: 'Empty',
              actionLabel: 'Add',
              onAction: () => tapped = true,
            ),
          ),
        ),
      );

      expect(find.text('Add'), findsOneWidget);
      expect(find.bySemanticsLabel('Empty. Action: Add'), findsOneWidget);
      expect(find.bySemanticsLabel('Add'), findsOneWidget);
      await tester.tap(find.text('Add'));
      expect(tapped, isTrue);
    });

    testWidgets('meets labeledTapTargetGuideline', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(body: EmptyState(message: 'Nothing here')),
        ),
      );

      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });

  group('ErrorState', () {
    testWidgets('displays shared error chrome with guidance', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: ErrorState(
              message: 'Something went wrong',
              guidance: 'Try again in a moment.',
            ),
          ),
        ),
      );

      expect(find.text('Something went wrong'), findsOneWidget);
      expect(find.text('Try again in a moment.'), findsOneWidget);
      expect(find.byType(Card), findsOneWidget);
      expect(find.byIcon(Icons.error_outline), findsOneWidget);
      expect(
        find.bySemanticsLabel('Something went wrong. Try again in a moment.'),
        findsOneWidget,
      );
    });

    testWidgets('renders retry button when onRetry is provided', (
      tester,
    ) async {
      var retried = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ErrorState(
              message: 'Error',
              retryLabel: 'Retry',
              onRetry: () => retried = true,
            ),
          ),
        ),
      );

      expect(find.text('Retry'), findsOneWidget);
      expect(find.bySemanticsLabel('Error. Action: Retry'), findsOneWidget);
      expect(find.bySemanticsLabel('Retry'), findsOneWidget);
      await tester.tap(find.text('Retry'));
      expect(retried, isTrue);
    });

    testWidgets('meets androidTapTargetGuideline', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ErrorState(
              message: 'Error',
              retryLabel: 'Retry',
              onRetry: () {},
            ),
          ),
        ),
      );

      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    });

    testWidgets('meets labeledTapTargetGuideline', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ErrorState(
              message: 'Error',
              retryLabel: 'Retry',
              onRetry: () {},
            ),
          ),
        ),
      );

      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });
}
