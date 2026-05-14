import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/boards/presentation/boards_preview_screen.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

import '../../helpers/test_app.dart';

void main() {
  group('BoardsPreviewScreen', () {
    testWidgets('labels boards as future provider-neutral preview', (
      tester,
    ) async {
      _setCompactPreviewSurface(tester);
      await tester.pumpWidget(
        createTestApp(
          const BoardsPreviewScreen(),
          overrides: _staticPreviewOverrides,
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Boards preview'), findsWidgets);
      expect(find.text('Future boards/tasks preview'), findsOneWidget);
      expect(find.text('Post-Release-1'), findsOneWidget);
      expect(find.text('Provider-neutral model'), findsOneWidget);
      expect(find.text('No drag required'), findsOneWidget);
      expect(find.text('Vikunja adapter spike'), findsOneWidget);
      expect(find.text('Move menu instead of drag-only'), findsOneWidget);
    });

    testWidgets('offers non-drag task actions with preview-only feedback', (
      tester,
    ) async {
      _setCompactPreviewSurface(tester);
      await tester.pumpWidget(
        createTestApp(
          const BoardsPreviewScreen(),
          overrides: _staticPreviewOverrides,
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.more_vert).first);
      await tester.pumpAndSettle();

      expect(find.text('Move to another column'), findsOneWidget);
      expect(find.text('Mark done'), findsOneWidget);
      expect(find.text('Mark blocked'), findsOneWidget);

      await tester.tap(find.text('Move to another column'));
      await tester.pumpAndSettle();

      expect(find.text('Preview only — no task was changed.'), findsOneWidget);
    });

    testWidgets('exposes screen-reader summaries for board, columns, and tasks', (
      tester,
    ) async {
      _setCompactPreviewSurface(tester);
      await tester.pumpWidget(
        createTestApp(
          const BoardsPreviewScreen(),
          overrides: _staticPreviewOverrides,
        ),
      );
      await tester.pumpAndSettle();

      expect(
        find.bySemanticsLabel(
          'Future boards/tasks preview. Hidden from Release 1 navigation. Provider-neutral Weave model with keyboard and screen-reader alternatives; no provider is connected yet.',
        ),
        findsOneWidget,
      );
      expect(
        find.bySemanticsLabel(
          'Board Release readiness board, 4 columns, 5 tasks.',
        ),
        findsOneWidget,
      );

      await tester.drag(find.byType(CustomScrollView), const Offset(0, -1200));
      await tester.pumpAndSettle();

      expect(
        find.bySemanticsLabel('Column Blocked, status Blocked, 1 task.'),
        findsOneWidget,
      );
      expect(
        find.bySemanticsLabel(
          'Task Runtime enablement spec. Column Blocked. Status Blocked. Assignee Cross-repo owner. Due Needs promotion spec. Priority High priority.',
        ),
        findsOneWidget,
      );
    });

    testWidgets('meets tap-target accessibility guidelines', (tester) async {
      _setCompactPreviewSurface(tester);
      await tester.pumpWidget(
        createTestApp(
          const BoardsPreviewScreen(),
          overrides: _staticPreviewOverrides,
        ),
      );
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });

    testWidgets('keeps critical preview copy reachable with large text', (
      tester,
    ) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(900, 1600);
      tester.platformDispatcher.textScaleFactorTestValue = 2;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

      await tester.pumpWidget(
        createTestApp(
          const BoardsPreviewScreen(),
          overrides: _staticPreviewOverrides,
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Future boards/tasks preview'), findsOneWidget);

      await tester.drag(find.byType(CustomScrollView), const Offset(0, -1800));
      await tester.pumpAndSettle();

      expect(find.text('Runtime enablement spec'), findsOneWidget);
      expect(find.byIcon(Icons.more_vert), findsWidgets);
    });
  });
}

final _staticPreviewOverrides = [
  weaveAuthenticatedSessionProvider.overrideWith((ref) async => null),
];

void _setCompactPreviewSurface(WidgetTester tester) {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = const Size(900, 1600);
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}
