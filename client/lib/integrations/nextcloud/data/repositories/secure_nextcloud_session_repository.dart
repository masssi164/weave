import 'package:weave/core/persistence/secure_store.dart';
import 'package:weave/integrations/nextcloud/data/dtos/nextcloud_session_dto.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_failure.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_session.dart';
import 'package:weave/integrations/nextcloud/domain/repositories/nextcloud_session_repository.dart';

const nextcloudSessionStorageKey = 'nextcloud_session_v1';

class SecureNextcloudSessionRepository implements NextcloudSessionRepository {
  const SecureNextcloudSessionRepository({required SecureStore secureStore})
    : _secureStore = secureStore;

  final SecureStore _secureStore;

  @override
  Future<NextcloudSession?> readSession() async {
    try {
      final raw = await _secureStore.read(nextcloudSessionStorageKey);
      if (raw == null || raw.isEmpty) {
        return null;
      }

      return NextcloudSessionDto.decode(raw).toSession();
    } catch (error) {
      throw NextcloudFailure.storage(
        'Unable to read the saved Nextcloud session.',
        cause: error,
      );
    }
  }

  @override
  Future<void> saveSession(NextcloudSession session) async {
    try {
      await _secureStore.write(
        nextcloudSessionStorageKey,
        NextcloudSessionDto.fromSession(session).encode(),
      );
    } catch (error) {
      throw NextcloudFailure.storage(
        'Unable to save the Nextcloud session.',
        cause: error,
      );
    }
  }

  @override
  Future<void> clearSession() async {
    try {
      await _secureStore.delete(nextcloudSessionStorageKey);
    } catch (error) {
      throw NextcloudFailure.storage(
        'Unable to clear the saved Nextcloud session.',
        cause: error,
      );
    }
  }
}
