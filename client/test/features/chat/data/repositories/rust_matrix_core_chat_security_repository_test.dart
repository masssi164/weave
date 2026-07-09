import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/data/repositories/rust_matrix_core_chat_security_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/'
    'server_configuration_repository.dart';

import '../../../../helpers/server_config_test_data.dart';

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository(this.configuration);

  ServerConfiguration? configuration;

  @override
  Future<void> clearConfiguration() async {
    configuration = null;
  }

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    this.configuration = configuration;
  }
}

void main() {
  group('RustMatrixCoreChatSecurityRepository', () {
    test('fails E2EE state closed until the Rust bridge owns it', () async {
      // MATRIX_E2EE_STATE_CONTRACT
      final repository = RustMatrixCoreChatSecurityRepository(
        serverConfigurationRepository: _FakeServerConfigurationRepository(
          buildTestConfiguration(),
        ),
      );

      final security = await repository.loadSecurityState();

      expect(security.bootstrapState, ChatSecurityBootstrapState.unavailable);
      expect(
        security.accountVerificationState,
        ChatAccountVerificationState.unavailable,
      );
      expect(
        security.deviceVerificationState,
        ChatDeviceVerificationState.unavailable,
      );
      expect(security.keyBackupState, ChatKeyBackupState.unavailable);
      expect(
        security.roomEncryptionReadiness,
        ChatRoomEncryptionReadiness.unavailable,
      );
      expect(security.readinessState, ChatReadinessState.unsupportedDevice);
    });

    test('security actions stay blocked without Matrix SDK fallback', () async {
      final repository = RustMatrixCoreChatSecurityRepository(
        serverConfigurationRepository: _FakeServerConfigurationRepository(
          buildTestConfiguration(),
        ),
      );

      await expectLater(
        repository.startVerification(),
        throwsA(
          isA<ChatFailure>()
              .having(
                (failure) => failure.type,
                'type',
                ChatFailureType.unsupportedConfiguration,
              )
              .having(
                (failure) => failure.message,
                'message',
                contains('Rust Matrix core Flutter bridge'),
              )
              .having(
                (failure) => failure.message,
                'message',
                isNot(contains('access_token')),
              ),
        ),
      );
    });

    test('fails clearly when setup is missing', () async {
      final repository = RustMatrixCoreChatSecurityRepository(
        serverConfigurationRepository: _FakeServerConfigurationRepository(null),
      );

      await expectLater(
        repository.loadSecurityState(),
        throwsA(
          isA<ChatFailure>().having(
            (failure) => failure.type,
            'type',
            ChatFailureType.configuration,
          ),
        ),
      );
    });
  });
}
