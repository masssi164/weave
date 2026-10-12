import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';

void main() {
  group('ServerConfigurationFormController', () {
    test('derives the Weave Matrix facade from the API origin', () {
      final container = ProviderContainer.test();
      addTearDown(container.dispose);

      final controller = container.read(
        serverConfigurationFormControllerProvider.notifier,
      );

      controller.state = controller.state.copyWith(
        matrixHomeserverUrl: 'https://matrix.custom.example',
        matrixError: 'matrix validation failed',
      );

      controller.updateIssuerUrl('https://auth.example.com');

      final state = container.read(serverConfigurationFormControllerProvider);

      expect(state.derivedMatrixHomeserverUrl, 'https://api.example.com');
      expect(state.matrixHomeserverUrl, 'https://api.example.com');
      expect(state.matrixError, isNull);
    });
  });
}
