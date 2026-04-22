import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';

void main() {
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
    testWidgets('displays message and icon', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: EmptyState(
              message: 'Nothing here',
              icon: Icons.inbox_outlined,
            ),
          ),
        ),
      );

      expect(find.text('Nothing here'), findsOneWidget);
      expect(find.byIcon(Icons.inbox_outlined), findsOneWidget);
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
    testWidgets('displays error message', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(body: ErrorState(message: 'Something went wrong')),
        ),
      );

      expect(find.text('Something went wrong'), findsOneWidget);
      expect(find.byIcon(Icons.error_outline), findsOneWidget);
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
