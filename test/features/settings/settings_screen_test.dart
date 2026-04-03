import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';
import 'package:weave/features/settings/presentation/settings_screen.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../helpers/fake_chat_security_repository.dart';
import '../../helpers/in_memory_stores.dart';
import '../../helpers/server_config_test_data.dart';

Finder _textFieldWithLabel(String label) {
  return find.byWidgetPredicate(
    (widget) => widget is TextField && widget.decoration?.labelText == label,
  );
}

void main() {
  group('SettingsScreen', () {
    testWidgets('loads the saved configuration and persists edits', (
      tester,
    ) async {
      final store = InMemoryPreferencesStore(buildStoredConfiguration());
      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith((ref) => store),
          chatSecurityRepositoryProvider.overrideWithValue(
            FakeChatSecurityRepository(),
          ),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(body: SettingsScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Server Configuration'), findsOneWidget);
      expect(find.text('https://auth.home.internal'), findsWidgets);
      expect(find.text('weave-mobile'), findsWidgets);

      await tester.enterText(
        _textFieldWithLabel('Nextcloud Base URL'),
        'https://nextcloud-alt.home.internal',
      );
      await tester.pump();
      expect(find.text('https://nextcloud-alt.home.internal'), findsWidgets);

      expect(
        container
            .read(serverConfigurationFormControllerProvider)
            .nextcloudBaseUrl,
        'https://nextcloud-alt.home.internal',
      );

      await container
          .read(serverConfigurationFormControllerProvider.notifier)
          .save();
      await tester.pumpAndSettle();

      final raw = store.rawString(serverConfigurationStorageKey);
      final json = jsonDecode(raw!) as Map<String, dynamic>;

      expect(json['nextcloudBaseUrl'], 'https://nextcloud-alt.home.internal');
    });

    testWidgets('preserves overridden service URLs when the issuer changes', (
      tester,
    ) async {
      final store = InMemoryPreferencesStore(
        buildStoredConfiguration(
          nextcloudBaseUrl: 'https://cloud.custom.internal',
        ),
      );
      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith((ref) => store),
          chatSecurityRepositoryProvider.overrideWithValue(
            FakeChatSecurityRepository(),
          ),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(body: SettingsScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        _textFieldWithLabel('OIDC Issuer URL'),
        'https://sso.example.com',
      );
      await tester.pumpAndSettle();

      expect(find.text('https://matrix.example.com'), findsWidgets);
      expect(find.text('https://cloud.custom.internal'), findsWidgets);
    });
  });
}
