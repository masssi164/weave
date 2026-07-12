import 'package:weave/core/persistence/secure_store.dart';
import 'package:weave/features/app/domain/ports/client_upgrade_port.dart';

const obsoleteProviderSessionStorageKey = 'nextcloud_session_v1';

class PersistedClientUpgradeService implements ClientUpgradePort {
  const PersistedClientUpgradeService({required SecureStore secureStore})
    : _secureStore = secureStore;

  final SecureStore _secureStore;

  @override
  Future<void> removeObsoleteAuthenticatedState() async {
    try {
      await _secureStore.delete(obsoleteProviderSessionStorageKey);
    } catch (_) {
      // Obsolete provider state is never an application-entry gate. A cleanup
      // failure must not discard or hide the valid Weave OIDC session.
    }
  }
}
