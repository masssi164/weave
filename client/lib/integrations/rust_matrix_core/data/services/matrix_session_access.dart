import 'chat_operation_diagnostics.dart';
import 'package:http/http.dart' as http;
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/generated/user_api/api.dart' as user_api;
import 'package:weave/integrations/weave_api/data/services/weave_user_api_client.dart';

class MatrixSessionAccess {
  const MatrixSessionAccess({
    required this.organizationId,
    required this.subject,
  });

  final String organizationId;
  final String subject;
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
      final chat = capabilities?.domains['chat'];
      if (organizationId == null ||
          organizationId.isEmpty ||
          subject == null ||
          subject != expectedSubject ||
          identity?.identityIssuer != expectedIssuer.toString()) {
        throw const ChatFailure.sessionRequired(
          'M_WEAVE_MATRIX_MEMBER_SESSION_REQUIRED',
        );
      }
      if (chat == null) {
        throw const ChatFailure.protocol('M_WEAVE_MATRIX_ACCESS_UNCONFIRMED');
      }
      if (chat.policyState !=
              user_api
                  .WorkspaceCapabilityStatusResponsePolicyStateEnum
                  .allowed ||
          !chat.grantedCapabilities.contains('chat.read')) {
        throw const ChatFailure.accessDenied('M_WEAVE_MATRIX_ACCESS_DENIED');
      }
      if (chat.enabled != true ||
          (chat.readiness !=
                  user_api
                      .WorkspaceCapabilityStatusResponseReadinessEnum
                      .ready &&
              chat.readiness !=
                  user_api
                      .WorkspaceCapabilityStatusResponseReadinessEnum
                      .degraded)) {
        throw const ChatFailure.serviceUnavailable(
          'M_WEAVE_MATRIX_ACCESS_UNCONFIRMED',
        );
      }
      return MatrixSessionAccess(
        organizationId: organizationId,
        subject: subject,
      );
    } on ChatFailure {
      rethrow;
    } on user_api.ApiException catch (error) {
      if (error.innerException != null) {
        throw classifyChatFailure(
          ChatFailure.protocol(
            'M_WEAVE_MATRIX_ACCESS_UNCONFIRMED',
            cause: error.innerException,
          ),
        );
      }
      ChatOperationDiagnostics.status(error.code);
      throw ChatFailure(
        type: error.code == 401
            ? ChatFailureType.sessionRequired
            : error.code == 403
            ? ChatFailureType.accessDenied
            : error.code >= 500
            ? ChatFailureType.serviceUnavailable
            : ChatFailureType.protocol,
        message: 'M_WEAVE_MATRIX_ACCESS_UNCONFIRMED',
        cause: error.code,
      );
    } catch (error) {
      throw classifyChatFailure(
        ChatFailure.protocol('M_WEAVE_MATRIX_ACCESS_UNCONFIRMED', cause: error),
      );
    }
  }
}
