import 'dart:convert';

import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_session.dart';

class NextcloudSessionDto {
  const NextcloudSessionDto({
    required this.baseUrl,
    required this.authMethod,
    required this.userId,
    required this.accountLabel,
    this.loginName,
    this.appPassword,
  });

  factory NextcloudSessionDto.fromSession(NextcloudSession session) {
    return NextcloudSessionDto(
      baseUrl: session.baseUrl.toString(),
      authMethod: session.authMethod.name,
      userId: session.userId,
      accountLabel: session.accountLabel,
      loginName: session.loginName,
      appPassword: session.appPassword,
    );
  }

  factory NextcloudSessionDto.decode(String raw) {
    final json = jsonDecode(raw) as Map<String, dynamic>;
    final authMethod =
        json['authMethod'] as String? ??
        NextcloudSessionAuthMethod.appPassword.name;
    return NextcloudSessionDto(
      baseUrl: json['baseUrl'] as String,
      authMethod: authMethod,
      userId: json['userId'] as String,
      accountLabel:
          json['accountLabel'] as String? ??
          (json['userId'] as String? ?? json['loginName'] as String),
      loginName: json['loginName'] as String?,
      appPassword: json['appPassword'] as String?,
    );
  }

  final String baseUrl;
  final String authMethod;
  final String userId;
  final String accountLabel;
  final String? loginName;
  final String? appPassword;

  NextcloudSession toSession() {
    final parsedBaseUrl = Uri.parse(baseUrl);
    switch (NextcloudSessionAuthMethod.values.byName(authMethod)) {
      case NextcloudSessionAuthMethod.oidcBearer:
        return NextcloudSession.oidcBearer(
          baseUrl: parsedBaseUrl,
          userId: userId,
          accountLabel: accountLabel,
        );
      case NextcloudSessionAuthMethod.appPassword:
        return NextcloudSession.appPassword(
          baseUrl: parsedBaseUrl,
          loginName: loginName!,
          userId: userId,
          appPassword: appPassword!,
        );
    }
  }

  String encode() {
    return jsonEncode({
      'baseUrl': baseUrl,
      'authMethod': authMethod,
      'userId': userId,
      'accountLabel': accountLabel,
      'loginName': loginName,
      'appPassword': appPassword,
    });
  }
}
