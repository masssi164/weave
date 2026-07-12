import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/app/data/services/persisted_client_upgrade_service.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';

import '../../../../helpers/in_memory_stores.dart';

void main() {
  test('removes only the obsolete provider session', () async {
    final secureStore = InMemorySecureStore({
      authSessionStorageKey: 'current-oidc-session',
      obsoleteProviderSessionStorageKey: 'obsolete-provider-session',
    });
    final service = PersistedClientUpgradeService(secureStore: secureStore);

    await service.removeObsoleteAuthenticatedState();

    expect(secureStore.rawValue(obsoleteProviderSessionStorageKey), isNull);
    expect(secureStore.rawValue(authSessionStorageKey), 'current-oidc-session');
  });
}
