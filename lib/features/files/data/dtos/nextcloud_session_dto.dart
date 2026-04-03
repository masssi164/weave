import 'dart:convert';

import 'package:weave/features/files/domain/entities/nextcloud_session.dart';

class NextcloudSessionDto {
  const NextcloudSessionDto({
    required this.baseUrl,
    required this.loginName,
    required this.userId,
    required this.appPassword,
  });

  factory NextcloudSessionDto.fromSession(NextcloudSession session) {
    return NextcloudSessionDto(
      baseUrl: session.baseUrl.toString(),
      loginName: session.loginName,
      userId: session.userId,
      appPassword: session.appPassword,
    );
  }

  factory NextcloudSessionDto.decode(String raw) {
    final json = jsonDecode(raw) as Map<String, dynamic>;
    return NextcloudSessionDto(
      baseUrl: json['baseUrl'] as String,
      loginName: json['loginName'] as String,
      userId: json['userId'] as String,
      appPassword: json['appPassword'] as String,
    );
  }

  final String baseUrl;
  final String loginName;
  final String userId;
  final String appPassword;

  NextcloudSession toSession() {
    return NextcloudSession(
      baseUrl: Uri.parse(baseUrl),
      loginName: loginName,
      userId: userId,
      appPassword: appPassword,
    );
  }

  String encode() {
    return jsonEncode({
      'baseUrl': baseUrl,
      'loginName': loginName,
      'userId': userId,
      'appPassword': appPassword,
    });
  }
}
