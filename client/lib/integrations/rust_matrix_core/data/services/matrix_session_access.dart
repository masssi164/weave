import 'package:http/http.dart' as http;
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/generated/user_api/api.dart' as user_api;
import 'package:weave/integrations/weave_api/data/services/weave_user_api_client.dart';

class MatrixSessionAccess {
  const MatrixSessionAccess({
    required this.organizationId,
    required this.subject,
    required this.matrixClientServerBaseUrl,
  });

  final String organizationId;
  final String subject;
  final Uri matrixClientServerBaseUrl;
}

abstract interface class MatrixSessionAccessPort {
  Future<MatrixSessionAccess> authorize({
    required Uri userApiBaseUrl,
    required String weaveAccessToken,
    required String expectedSubject,
    required Uri expectedIssuer,
  });
}

/// Checks current member access before opening or reusing a Matrix session.
/// Admission uses generated User API operations before the same member bearer
/// is handed to the Rust SDK for the Weave Matrix facade.
class GeneratedMatrixSessionAccess implements MatrixSessionAccessPort {
  const GeneratedMatrixSessionAccess({required http.Client httpClient})
    : _httpClient = httpClient;

  final http.Client _httpClient;

  @override
  Future<MatrixSessionAccess> authorize({
    required Uri userApiBaseUrl,
    required String weaveAccessToken,
    required String expectedSubject,
    required Uri expectedIssuer,
  }) async {
    try {
      final api = weaveUserApiClient(
        apiBaseUrl: userApiBaseUrl,
        accessToken: weaveAccessToken,
        httpClient: _httpClient,
      );
      final identity = await user_api.IdentityApi(
        api,
      ).me().timeout(const Duration(seconds: 8));
      final capabilities = await user_api.WorkspaceApi(
        api,
      ).capabilities().timeout(const Duration(seconds: 8));
      final organizationId = identity?.organizationId?.trim();
      final subject = identity?.subject?.trim();
      final chat = capabilities?.chat;
      if (organizationId == null ||
          organizationId.isEmpty ||
          subject == null ||
          subject != expectedSubject ||
          identity?.identityIssuer != expectedIssuer.toString() ||
          chat?.enabled != true ||
          chat?.policyState !=
              user_api
                  .WorkspaceCapabilityStatusResponsePolicyStateEnum
                  .allowed ||
          (chat?.readiness !=
                  user_api
                      .WorkspaceCapabilityStatusResponseReadinessEnum
                      .ready &&
              chat?.readiness !=
                  user_api
                      .WorkspaceCapabilityStatusResponseReadinessEnum
                      .degraded) ||
          !chat!.grantedCapabilities.contains('chat.read')) {
        throw const ChatFailure.sessionRequired('M_WEAVE_MATRIX_ACCESS_DENIED');
      }
      final publicApi = user_api.ApiClient(basePath: userApiBaseUrl.origin)
        ..client = _httpClient
        ..addDefaultHeader('Accept', 'application/json');
      final platform = await user_api.PlatformApi(
        publicApi,
      ).config().timeout(const Duration(seconds: 8));
      final advertised = Uri.tryParse(
        platform?.protocols.matrixClientServerBaseUrl ?? '',
      );
      if (advertised == null ||
          advertised.scheme != 'https' ||
          advertised.host.isEmpty ||
          advertised.userInfo.isNotEmpty ||
          (advertised.path.isNotEmpty && advertised.path != '/') ||
          advertised.hasQuery ||
          advertised.hasFragment) {
        throw const ChatFailure.configuration(
          'M_WEAVE_MATRIX_ENDPOINT_UNCONFIRMED',
        );
      }
      return MatrixSessionAccess(
        organizationId: organizationId,
        subject: subject,
        matrixClientServerBaseUrl: advertised.replace(path: ''),
      );
    } on ChatFailure {
      rethrow;
    } on user_api.ApiException catch (error) {
      throw ChatFailure.sessionRequired(
        'M_WEAVE_MATRIX_ACCESS_UNCONFIRMED',
        cause: error.code,
      );
    } catch (error) {
      throw ChatFailure.sessionRequired(
        'M_WEAVE_MATRIX_ACCESS_UNCONFIRMED',
        cause: error,
      );
    }
  }
}
