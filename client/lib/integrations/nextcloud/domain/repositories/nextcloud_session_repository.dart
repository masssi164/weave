import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_session.dart';

abstract interface class NextcloudSessionRepository {
  Future<NextcloudSession?> readSession();

  Future<void> saveSession(NextcloudSession session);

  Future<void> clearSession();
}
