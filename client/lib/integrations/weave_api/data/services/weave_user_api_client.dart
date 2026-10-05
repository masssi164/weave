import 'package:http/http.dart' as http;
import 'package:weave/generated/user_api/api.dart' as user_api;

/// Binds an authenticated member session to the generated User HTTP client.
user_api.ApiClient weaveUserApiClient({
  required Uri apiBaseUrl,
  required String accessToken,
  required http.Client httpClient,
}) {
  if (apiBaseUrl.pathSegments
              .where((segment) => segment.isNotEmpty)
              .join('/') !=
          'api' ||
      apiBaseUrl.hasQuery ||
      apiBaseUrl.hasFragment) {
    throw ArgumentError.value(
      apiBaseUrl,
      'apiBaseUrl',
      'WEAVE_API_BASE_PATH_INVALID',
    );
  }
  final authentication = user_api.HttpBearerAuth()..accessToken = accessToken;
  return user_api.ApiClient(
    basePath: apiBaseUrl.origin,
    authentication: authentication,
  )..client = httpClient;
}
