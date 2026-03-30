import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:weave/main.dart';

import '../../helpers/server_config_test_data.dart';

void main() {
  group('AppShell', () {
    testWidgets('renders five bottom navigation destinations', (tester) async {
      SharedPreferences.setMockInitialValues(buildStoredConfiguration());

      await tester.pumpWidget(const ProviderScope(child: WeaveApp()));
      await tester.pumpAndSettle();

      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.byIcon(Icons.chat_bubble), findsOneWidget);
      expect(find.byIcon(Icons.folder_outlined), findsOneWidget);
      expect(find.byIcon(Icons.calendar_today_outlined), findsOneWidget);
      expect(find.byIcon(Icons.dashboard_outlined), findsOneWidget);
      expect(find.byIcon(Icons.settings_outlined), findsOneWidget);
    });

    testWidgets('navigates to settings from the bottom navigation bar', (
      tester,
    ) async {
      SharedPreferences.setMockInitialValues(buildStoredConfiguration());

      await tester.pumpWidget(const ProviderScope(child: WeaveApp()));
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.settings_outlined));
      await tester.pumpAndSettle();

      expect(find.text('Server Configuration'), findsOneWidget);
    });
  });
}
