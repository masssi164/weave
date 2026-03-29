import 'package:flutter_test/flutter_test.dart';
import 'package:riverpod/riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:weave/features/onboarding/providers/setup_state_provider.dart';

void main() {
  group('SetupState', () {
    setUp(() {
      SharedPreferences.setMockInitialValues({});
    });

    test('defaults to false when no persisted value exists', () {
      final container = ProviderContainer.test();

      expect(container.read(setupStateProvider), isFalse);
    });

    test('loads persisted completion state from SharedPreferences', () async {
      SharedPreferences.setMockInitialValues({'setup_complete': true});
      final container = ProviderContainer.test();

      expect(container.read(setupStateProvider), isFalse);

      await Future<void>.delayed(Duration.zero);

      expect(container.read(setupStateProvider), isTrue);
    });

    test('completeSetup persists and updates state', () async {
      final container = ProviderContainer.test();

      await container.read(setupStateProvider.notifier).completeSetup();

      expect(container.read(setupStateProvider), isTrue);
      expect(
        (await SharedPreferences.getInstance()).getBool('setup_complete'),
        isTrue,
      );
    });
  });

  group('SetupStep', () {
    test('advances and never goes below zero', () {
      final container = ProviderContainer.test();
      final notifier = container.read(setupStepProvider.notifier);

      expect(container.read(setupStepProvider), 0);

      notifier.next();
      expect(container.read(setupStepProvider), 1);

      notifier.previous();
      expect(container.read(setupStepProvider), 0);

      notifier.previous();
      expect(container.read(setupStepProvider), 0);
    });
  });
}
