import 'package:weave/features/profile/domain/entities/user_profile.dart';

class UserProfileDto {
  const UserProfileDto({
    required this.userId,
    required this.username,
    required this.emailVerified,
    required this.displayName,
    required this.locale,
    required this.timezone,
    required this.roles,
    required this.groups,
    this.email,
  });

  factory UserProfileDto.fromJson(Map<String, dynamic> json) {
    return UserProfileDto(
      userId: json['userId'] as String,
      username: json['username'] as String,
      email: json['email'] as String?,
      emailVerified: json['emailVerified'] as bool? ?? false,
      displayName: json['displayName'] as String? ?? json['username'] as String,
      locale: json['locale'] as String? ?? 'en',
      timezone: json['timezone'] as String? ?? 'UTC',
      roles: _stringList(json['roles']),
      groups: _stringList(json['groups']),
    );
  }

  final String userId;
  final String username;
  final String? email;
  final bool emailVerified;
  final String displayName;
  final String locale;
  final String timezone;
  final List<String> roles;
  final List<String> groups;

  UserProfile toDomain() {
    return UserProfile(
      userId: userId,
      username: username,
      email: email,
      emailVerified: emailVerified,
      displayName: displayName,
      locale: locale,
      timezone: timezone,
      roles: roles,
      groups: groups,
    );
  }

  static List<String> _stringList(Object? value) {
    if (value is! List) {
      return const <String>[];
    }
    return value.whereType<String>().toList(growable: false);
  }
}

Map<String, Object?> userProfileUpdateToJson(UserProfileUpdate update) {
  return {
    if (update.displayName != null) 'displayName': update.displayName,
    if (update.locale != null) 'locale': update.locale,
    if (update.timezone != null) 'timezone': update.timezone,
  };
}
