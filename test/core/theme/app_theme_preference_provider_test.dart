import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/theme/app_theme_preference.dart';
import 'package:weave/core/theme/app_theme_preference_provider.dart';
import 'package:weave/core/theme/shared_preferences_app_theme_preference_repository.dart';

import '../../helpers/in_memory_stores.dart';

void main() {
  group('AppThemePreferenceController', () {
    ProviderContainer buildContainer(InMemoryPreferencesStore store) {
      return ProviderContainer.test(
        overrides: [preferencesStoreProvider.overrideWith((ref) => store)],
      );
    }

    test('loads system/default when no user preference is stored', () async {
      final container = buildContainer(InMemoryPreferencesStore());
      addTearDown(container.dispose);

      final selection = await container.read(appThemePreferenceProvider.future);

      expect(selection.userPreference, isNull);
      expect(selection.workspaceDefault, AppThemePreference.system);
      expect(selection.effectivePreference, AppThemePreference.system);
    });

    test('loads and persists a personal theme choice', () async {
      final store = InMemoryPreferencesStore({
        appThemePreferenceStorageKey: AppThemePreference.dark.storageKey,
      });
      final container = buildContainer(store);
      addTearDown(container.dispose);

      final initial = await container.read(appThemePreferenceProvider.future);
      expect(initial.userPreference, AppThemePreference.dark);

      await container
          .read(appThemePreferenceProvider.notifier)
          .setUserPreference(AppThemePreference.highContrast);

      final updated = container.read(appThemePreferenceProvider).requireValue;
      expect(updated.userPreference, AppThemePreference.highContrast);
      expect(updated.workspaceDefault, AppThemePreference.system);
      expect(updated.effectivePreference, AppThemePreference.highContrast);
      expect(
        store.rawString(appThemePreferenceStorageKey),
        AppThemePreference.highContrast.storageKey,
      );
    });
  });
}
