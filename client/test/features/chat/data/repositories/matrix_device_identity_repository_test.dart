import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/data/repositories/matrix_device_identity_repository.dart';

import '../../../../helpers/in_memory_stores.dart';

void main() {
  group('MatrixDeviceIdentityRepository', () {
    test(
      'preserves one device identity across repository relaunches',
      () async {
        final secureStore = InMemorySecureStore();
        final firstRepository = MatrixDeviceIdentityRepository(
          secureStore: secureStore,
          random: Random(7),
        );

        final first = await firstRepository.loadOrCreate();
        final relaunched = await MatrixDeviceIdentityRepository(
          secureStore: secureStore,
          random: Random(99),
        ).loadOrCreate();

        expect(first, matches(RegExp(r'^WEAVE[0-9a-f]{36}$')));
        expect(relaunched, first);
      },
    );

    test('repairs invalid stored device state without clearing auth', () async {
      final secureStore = InMemorySecureStore({
        matrixDeviceIdentityStorageKey: 'weave-oidc',
        'auth_session_v1': 'preserved-auth-session',
      });
      final repository = MatrixDeviceIdentityRepository(
        secureStore: secureStore,
        random: Random(11),
      );

      final deviceId = await repository.loadOrCreate();

      expect(deviceId, matches(RegExp(r'^WEAVE[0-9a-f]{36}$')));
      expect(
        await secureStore.read('auth_session_v1'),
        'preserved-auth-session',
      );
    });

    test('deletes identity only through explicit account removal', () async {
      final secureStore = InMemorySecureStore();
      final repository = MatrixDeviceIdentityRepository(
        secureStore: secureStore,
        random: Random(17),
      );
      final first = await repository.loadOrCreate();

      await repository.removeForExplicitAccountRemoval();
      final replacement = await repository.loadOrCreate();

      expect(replacement, isNot(first));
    });
  });
}
