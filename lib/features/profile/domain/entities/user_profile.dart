class UserProfile {
  const UserProfile({
    required this.userId,
    required this.username,
    required this.displayName,
    required this.locale,
    required this.timezone,
    required this.emailVerified,
    this.email,
    this.roles = const <String>[],
    this.groups = const <String>[],
  });

  final String userId;
  final String username;
  final String? email;
  final bool emailVerified;
  final String displayName;
  final String locale;
  final String timezone;
  final List<String> roles;
  final List<String> groups;
}

class UserProfileUpdate {
  const UserProfileUpdate({this.displayName, this.locale, this.timezone});

  final String? displayName;
  final String? locale;
  final String? timezone;
}
