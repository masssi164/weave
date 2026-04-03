class NextcloudSession {
  const NextcloudSession({
    required this.baseUrl,
    required this.loginName,
    required this.userId,
    required this.appPassword,
  });

  final Uri baseUrl;
  final String loginName;
  final String userId;
  final String appPassword;

  String get accountLabel => userId.isNotEmpty ? userId : loginName;

  bool matchesBaseUrl(Uri configuredBaseUrl) {
    return _normalizeBaseUrl(baseUrl) == _normalizeBaseUrl(configuredBaseUrl);
  }

  static Uri _normalizeBaseUrl(Uri uri) {
    final path = uri.path.endsWith('/') ? uri.path : '${uri.path}/';
    return uri.replace(path: path, query: null, fragment: null);
  }
}
