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

    test('saveSession persists the session in secure storage', () async {
      final session = NextcloudSession(
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
    });

    test('readSession restores a previously saved session', () async {
      final session = NextcloudSession(
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
    });

    test('clearSession deletes persisted data', () async {
      await secureStore.write(nextcloudSessionStorageKey, 'value');

      await repository.clearSession();

      expect(await secureStore.read(nextcloudSessionStorageKey), isNull);
    });
  });
}
