enum NextcloudSessionAuthMethod { oidcBearer, appPassword }

Uri normalizeNextcloudBaseUrl(Uri uri) {
  final path = uri.path.endsWith('/') ? uri.path : '${uri.path}/';
  return uri.replace(path: path, query: null, fragment: null);
}

class NextcloudSession {
  const NextcloudSession._({
    required this.baseUrl,
    required this.userId,
    required this.accountLabel,
    required this.authMethod,
    this.loginName,
    this.appPassword,
    this.bearerToken,
  });

  const NextcloudSession.oidcBearer({
    required Uri baseUrl,
    required String userId,
    String? accountLabel,
    String? bearerToken,
  }) : this._(
         baseUrl: baseUrl,
         userId: userId,
         accountLabel: accountLabel ?? userId,
         authMethod: NextcloudSessionAuthMethod.oidcBearer,
         bearerToken: bearerToken,
       );

  const NextcloudSession.appPassword({
    required Uri baseUrl,
    required String loginName,
    required String userId,
    required String appPassword,
  }) : this._(
         baseUrl: baseUrl,
         userId: userId,
         accountLabel: userId,
         authMethod: NextcloudSessionAuthMethod.appPassword,
         loginName: loginName,
         appPassword: appPassword,
       );

  final Uri baseUrl;
  final String userId;
  final String accountLabel;
  final NextcloudSessionAuthMethod authMethod;
  final String? loginName;
  final String? appPassword;
  final String? bearerToken;

  bool get usesOidcBearer =>
      authMethod == NextcloudSessionAuthMethod.oidcBearer;

  bool get usesAppPassword =>
      authMethod == NextcloudSessionAuthMethod.appPassword;

  NextcloudSession withBearerToken(String token) {
    return NextcloudSession.oidcBearer(
      baseUrl: baseUrl,
      userId: userId,
      accountLabel: accountLabel,
      bearerToken: token,
    );
  }

  NextcloudSession toPersistedSession() {
    if (usesOidcBearer) {
      return NextcloudSession.oidcBearer(
        baseUrl: baseUrl,
        userId: userId,
        accountLabel: accountLabel,
      );
    }

    return NextcloudSession.appPassword(
      baseUrl: baseUrl,
      loginName: loginName!,
      userId: userId,
      appPassword: appPassword!,
    );
  }

  bool matchesBaseUrl(Uri configuredBaseUrl) {
    return normalizeNextcloudBaseUrl(baseUrl) ==
        normalizeNextcloudBaseUrl(configuredBaseUrl);
  }
}
