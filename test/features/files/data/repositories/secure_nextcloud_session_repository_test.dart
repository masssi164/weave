import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/files/data/repositories/secure_nextcloud_session_repository.dart';
import 'package:weave/features/files/domain/entities/nextcloud_session.dart';

import '../../../../helpers/in_memory_stores.dart';

void main() {
  group('SecureNextcloudSessionRepository', () {
    late InMemorySecureStore secureStore;
    late SecureNextcloudSessionRepository repository;

    setUp(() {
      secureStore = InMemorySecureStore();
      repository = SecureNextcloudSessionRepository(secureStore: secureStore);
    });

    test(
      'saveSession persists app-password fallback sessions in secure storage',
      () async {
        final session = NextcloudSession.appPassword(
          baseUrl: Uri.parse('https://nextcloud.home.internal/'),
          loginName: 'alice@example.com',
          userId: 'alice',
          appPassword: 'app-password',
        );

        await repository.saveSession(session);

        expect(secureStore.rawValue(nextcloudSessionStorageKey), isNotNull);
        expect(
          secureStore.rawValue(nextcloudSessionStorageKey),
          contains('app-password'),
        );
      },
    );

    test('saveSession does not persist ephemeral bearer tokens', () async {
      final session = NextcloudSession.oidcBearer(
        baseUrl: Uri.parse('https://nextcloud.home.internal/'),
        userId: 'alice',
        accountLabel: 'Alice Example',
        bearerToken: 'oidc-access-token',
      );

      await repository.saveSession(session);

      expect(
        secureStore.rawValue(nextcloudSessionStorageKey),
        isNot(contains('oidc-access-token')),
      );

      final restored = await repository.readSession();
      expect(restored?.usesOidcBearer, isTrue);
      expect(restored?.accountLabel, 'Alice Example');
      expect(restored?.bearerToken, isNull);
    });

    test(
      'readSession restores a previously saved app-password session',
      () async {
        final session = NextcloudSession.appPassword(
          baseUrl: Uri.parse('https://nextcloud.home.internal/'),
          loginName: 'alice@example.com',
          userId: 'alice',
          appPassword: 'app-password',
        );
        await repository.saveSession(session);

        final restored = await repository.readSession();

        expect(restored?.baseUrl, session.baseUrl);
        expect(restored?.loginName, session.loginName);
        expect(restored?.userId, session.userId);
        expect(restored?.appPassword, session.appPassword);
        expect(restored?.usesAppPassword, isTrue);
      },
    );

    test('readSession restores legacy stored app-password data', () async {
      await secureStore.write(
        nextcloudSessionStorageKey,
        '{"baseUrl":"https://nextcloud.home.internal/","loginName":"alice@example.com","userId":"alice","appPassword":"app-password"}',
      );

      final restored = await repository.readSession();

      expect(restored?.usesAppPassword, isTrue);
      expect(restored?.loginName, 'alice@example.com');
      expect(restored?.appPassword, 'app-password');
    });

    test('clearSession deletes persisted data', () async {
      await secureStore.write(nextcloudSessionStorageKey, 'value');

      await repository.clearSession();

      expect(await secureStore.read(nextcloudSessionStorageKey), isNull);
    });
  });
}
