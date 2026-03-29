import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/a11y/semantic_button.dart';

void main() {
  group('AccessibleButton', () {
    testWidgets('renders with correct semantic label', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: AccessibleButton(
              onPressed: () {},
              semanticLabel: 'Test Action',
              child: const Text('Tap Me'),
            ),
          ),
        ),
      );

      final semantics = tester.getSemantics(find.byType(AccessibleButton));
      expect(semantics.label, 'Test Action');
    });

    testWidgets('enforces minimum tap target of 48x48', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Center(
              child: AccessibleButton(
                onPressed: () {},
                semanticLabel: 'Small Button',
                child: const Text('X'),
              ),
            ),
          ),
        ),
      );

      final button = tester.getSize(find.byType(FilledButton));
      expect(button.width, greaterThanOrEqualTo(48));
      expect(button.height, greaterThanOrEqualTo(48));
    });

    testWidgets('renders as OutlinedButton when outlined is true', (
      tester,
    ) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: AccessibleButton(
              onPressed: () {},
              semanticLabel: 'Outlined',
              outlined: true,
              child: const Text('Outlined'),
            ),
          ),
        ),
      );

      expect(find.byType(OutlinedButton), findsOneWidget);
      expect(find.byType(FilledButton), findsNothing);
    });

    testWidgets('meets androidTapTargetGuideline', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: AccessibleButton(
              onPressed: () {},
              semanticLabel: 'Tap Target Test',
              child: const Text('Test'),
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
            body: AccessibleButton(
              onPressed: () {},
              semanticLabel: 'Labeled Test',
              child: const Text('Test'),
            ),
          ),
        ),
      );

      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });
}
