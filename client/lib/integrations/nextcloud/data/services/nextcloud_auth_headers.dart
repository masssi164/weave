import 'dart:convert';

import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_failure.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_session.dart';

Map<String, String> buildNextcloudAuthHeaders(NextcloudSession session) {
  if (session.usesOidcBearer) {
    final bearerToken = session.bearerToken;
    if (bearerToken == null || bearerToken.isEmpty) {
      throw const NextcloudFailure.sessionRequired(
        'Reconnect Nextcloud because the saved bearer session is incomplete.',
      );
    }
    return {'Authorization': 'Bearer $bearerToken'};
  }

  final loginName = session.loginName;
  final appPassword = session.appPassword;
  if (loginName == null ||
      loginName.isEmpty ||
      appPassword == null ||
      appPassword.isEmpty) {
    throw const NextcloudFailure.sessionRequired(
      'Reconnect Nextcloud because the saved app password is incomplete.',
    );
  }

  final encoded = base64Encode(utf8.encode('$loginName:$appPassword'));
  return {'Authorization': 'Basic $encoded'};
}
