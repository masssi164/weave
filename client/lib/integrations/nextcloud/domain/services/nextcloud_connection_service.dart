import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_connection_state.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_session.dart';

abstract interface class NextcloudConnectionService {
  Future<NextcloudConnectionState> restoreConnection();

  Future<NextcloudConnectionState> connect();

  Future<void> disconnect();

  Future<NextcloudSession> requireLiveSession();

  Future<void> invalidateSession(NextcloudSession session);
}
