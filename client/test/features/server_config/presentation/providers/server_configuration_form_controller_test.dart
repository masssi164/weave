import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';

void main() {
  group('ServerConfigurationFormController', () {
    test(
      'clears stale Matrix and Nextcloud errors when issuer derivation replaces them',
      () {
        final container = ProviderContainer.test();
        addTearDown(container.dispose);

        final controller = container.read(
          serverConfigurationFormControllerProvider.notifier,
        );

        controller.state = controller.state.copyWith(
          matrixHomeserverUrl: 'https://matrix.custom.example',
          nextcloudBaseUrl: 'https://files.custom.example',
          matrixError: 'matrix validation failed',
          nextcloudError: 'nextcloud validation failed',
        );

        controller.updateIssuerUrl('https://auth.example.com');

        final state = container.read(serverConfigurationFormControllerProvider);

        expect(state.derivedMatrixHomeserverUrl, 'https://matrix.example.com');
        expect(state.derivedNextcloudBaseUrl, 'https://files.example.com');
        expect(state.matrixHomeserverUrl, 'https://matrix.example.com');
        expect(state.nextcloudBaseUrl, 'https://files.example.com');
        expect(state.matrixError, isNull);
        expect(state.nextcloudError, isNull);
      },
    );
  });
}
