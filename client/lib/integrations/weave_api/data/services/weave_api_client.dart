import 'dart:async';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/matrix_e2ee_diagnostic.dart';
import 'package:weave/features/app/domain/entities/member_space_access_snapshot.dart';
import 'package:weave/features/app/domain/entities/organization_manifest_snapshot.dart';
import 'package:weave/features/app/domain/entities/provider_stack_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
import 'package:weave/generated/user_api/api.dart' as user_api;
import 'package:weave/integrations/weave_api/data/dtos/organization_manifest_response_dto.dart';
import 'package:weave/integrations/weave_api/data/dtos/platform_status_response_dto.dart';
import 'package:weave/integrations/weave_api/data/dtos/provider_stack_openapi_mappers.dart';
import 'package:weave/integrations/weave_api/data/dtos/workspace_capabilities_response_dto.dart';
import 'package:weave/integrations/weave_api/data/dtos/workspace_home_response_dto.dart';
import 'package:weave/integrations/weave_api/data/services/weave_user_api_client.dart';

enum IdentitySessionReconcileResult { unchanged, reauthorizationRequired }

abstract interface class WeaveApiClient {
  Future<IdentitySessionReconcileResult> reconcileIdentitySession({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<OrganizationManifestSnapshot> fetchOrganizationManifest({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<WorkspaceCapabilitySnapshot> fetchWorkspaceCapabilities({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<MemberSpaceAccessSnapshot> fetchMemberSpaces({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<WorkspaceHomeSnapshot> fetchWorkspaceHome({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<MatrixE2eeDiagnostic> fetchMatrixE2eeDiagnostic({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<DevopsProviderSummarySnapshot> fetchDevopsSummary({
    required Uri baseUrl,
    required String accessToken,
    required String workspaceId,
    required String channelId,
  });

  Future<OfficeCapabilitiesSnapshot> fetchOfficeCapabilities({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<OfficeLaunchSnapshot> launchOfficeSession({
    required Uri baseUrl,
    required String accessToken,
    required String fileId,
    required String requestedMode,
  });
}

class HttpWeaveApiClient implements WeaveApiClient {
  HttpWeaveApiClient({required http.Client httpClient})
    : _httpClient = httpClient;

  final http.Client _httpClient;

  user_api.ApiClient _client(Uri baseUrl, String accessToken) =>
      weaveUserApiClient(
        apiBaseUrl: baseUrl,
        accessToken: accessToken,
        httpClient: _httpClient,
      );

  Future<T> _read<T>(
    Future<T?> Function() operation, {
    required String failureMessage,
    required String invalidPayloadMessage,
  }) async {
    try {
      final value = await operation().timeout(const Duration(seconds: 5));
      if (value == null) throw AppFailure.unknown(invalidPayloadMessage);
      return value;
    } on AppFailure {
      rethrow;
    } on user_api.ApiException catch (error) {
      if (error.code == 401 || error.code == 403) {
        throw AppFailure.unknown(
          'The Weave backend rejected the current session.',
          cause: error.code,
        );
      }
      throw AppFailure.unknown(failureMessage, cause: error.code);
    } on TimeoutException {
      throw const AppFailure.unknown(
        'Unable to reach the Weave backend right now.',
      );
    } on ArgumentError {
      rethrow;
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to decode a Weave backend response right now.',
        cause: error,
      );
    }
  }

  @override
  Future<IdentitySessionReconcileResult> reconcileIdentitySession({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final response = await _read(
      () => user_api.IdentitySessionApi(
        _client(baseUrl, accessToken),
      ).reconcileIdentitySession(),
      failureMessage:
          'The Weave backend failed to reconcile organization access.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid identity-session reconciliation payload.',
    );
    final result = switch (response.state.value) {
      'unchanged' => IdentitySessionReconcileResult.unchanged,
      'access_updated' =>
        IdentitySessionReconcileResult.reauthorizationRequired,
      _ => throw const AppFailure.unknown(
        'The Weave backend returned an unknown identity-session reconciliation state.',
      ),
    };
    if (response.reauthorizationRequired !=
        (result == IdentitySessionReconcileResult.reauthorizationRequired)) {
      throw const AppFailure.unknown(
        'The Weave backend returned an inconsistent identity-session reconciliation state.',
      );
    }
    return result;
  }

  @override
  Future<OrganizationManifestSnapshot> fetchOrganizationManifest({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final response = await _read(
      () => user_api.WorkspaceApi(
        _client(baseUrl, accessToken),
      ).organizationManifest(),
      failureMessage:
          'The Weave backend failed to return the organization manifest.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid organization manifest payload.',
    );
    return response.toSnapshot();
  }

  @override
  Future<WorkspaceCapabilitySnapshot> fetchWorkspaceCapabilities({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final response = await _read(
      () => user_api.WorkspaceApi(_client(baseUrl, accessToken)).capabilities(),
      failureMessage:
          'The Weave backend failed to return workspace capabilities.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid workspace capabilities payload.',
    );
    return response.toSnapshot();
  }

  @override
  Future<MemberSpaceAccessSnapshot> fetchMemberSpaces({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final api = user_api.SpacesUserApi(_client(baseUrl, accessToken));
    final refs = <String>{};
    String? cursor;
    // A malformed or cycling cursor must not turn a partial list into access.
    for (var page = 0; page < 100; page++) {
      final response = await _read(
        () => api.listSpaces(afterSpaceRef: cursor, limit: 100),
        failureMessage: 'The Weave backend failed to list member Spaces.',
        invalidPayloadMessage: 'The Weave backend returned no member Spaces.',
      );
      for (final space in response.spaces) {
        if (space.spaceRef.trim().isEmpty) {
          throw const AppFailure.unknown(
            'The Weave backend returned an invalid Space reference.',
          );
        }
        refs.add(space.spaceRef);
      }
      final next = response.nextAfterSpaceRef;
      if (next == null) {
        break;
      }
      if (next.isEmpty || next == cursor || page == 99) {
        throw const AppFailure.unknown(
          'The Weave backend returned an invalid Spaces cursor.',
        );
      }
      cursor = next;
    }

    var defaultReadable = false;
    if (refs.contains(MemberSpaceAccessSnapshot.defaultSpaceRef)) {
      try {
        final response = await api
            .getSpace(MemberSpaceAccessSnapshot.defaultSpaceRef)
            .timeout(const Duration(seconds: 5));
        defaultReadable =
            response?.spaceRef == MemberSpaceAccessSnapshot.defaultSpaceRef;
        if (!defaultReadable) {
          throw const AppFailure.unknown(
            'The Weave backend returned an invalid default Space.',
          );
        }
      } on user_api.ApiException catch (error) {
        if (error.code == 401 || error.code == 403) {
          throw AppFailure.unknown(
            'The Weave backend rejected the current session.',
            cause: error.code,
          );
        }
        if (error.code != 404) {
          throw AppFailure.unknown(
            'The Weave backend failed to read the default Space.',
            cause: error.code,
          );
        }
        // A grant may be revoked between list and read.
        refs.remove(MemberSpaceAccessSnapshot.defaultSpaceRef);
      } on TimeoutException {
        throw const AppFailure.unknown(
          'Unable to reach the Weave backend right now.',
        );
      }
    }
    return MemberSpaceAccessSnapshot(
      visibleSpaceRefs: Set.unmodifiable(refs),
      defaultSpaceReadable: defaultReadable,
    );
  }

  @override
  Future<WorkspaceHomeSnapshot> fetchWorkspaceHome({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final response = await _read(
      () => user_api.WorkspaceApi(_client(baseUrl, accessToken)).home(),
      failureMessage: 'Weave Home could not be loaded right now.',
      invalidPayloadMessage: 'The backend returned no Weave Home snapshot.',
    );
    return response.toSnapshot();
  }

  @override
  Future<MatrixE2eeDiagnostic> fetchMatrixE2eeDiagnostic({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final response = await _read(
      () => user_api.PlatformApi(
        _client(baseUrl, accessToken),
      ).getPlatformStatus(),
      failureMessage: 'The Weave backend failed to return platform status.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid platform status response.',
    );
    return response.toMatrixDiagnostic();
  }

  @override
  Future<DevopsProviderSummarySnapshot> fetchDevopsSummary({
    required Uri baseUrl,
    required String accessToken,
    required String workspaceId,
    required String channelId,
  }) async {
    final response = await _read(
      () => user_api.DevOpsFacadeApi(
        _client(baseUrl, accessToken),
      ).summary(workspaceId, channelId),
      failureMessage: 'The Weave backend failed to return DevOps readiness.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid DevOps readiness payload.',
    );
    return response.toSnapshot();
  }

  @override
  Future<OfficeCapabilitiesSnapshot> fetchOfficeCapabilities({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final response = await _read(
      () => user_api.OfficeFacadeApi(
        _client(baseUrl, accessToken),
      ).getOfficeCapabilities(),
      failureMessage: 'The Weave backend failed to return Office capabilities.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid Office capabilities payload.',
    );
    return response.toSnapshot();
  }

  @override
  Future<OfficeLaunchSnapshot> launchOfficeSession({
    required Uri baseUrl,
    required String accessToken,
    required String fileId,
    required String requestedMode,
  }) async {
    final apiClient = _client(baseUrl, accessToken);
    try {
      final response = await user_api.OfficeFacadeApi(apiClient)
          .launch(
            user_api.OfficeLaunchRequest(
              fileId: fileId,
              requestedMode: requestedMode,
            ),
          )
          .timeout(const Duration(seconds: 5));
      if (response == null) {
        throw const AppFailure.unknown(
          'The Weave backend returned an invalid Office launch payload.',
        );
      }
      return response.toSnapshot();
    } on user_api.ApiException catch (error) {
      if (error.code == 503) {
        user_api.ApiErrorResponse? errorResponse;
        try {
          final decoded = await apiClient.deserializeAsync(
            error.message ?? '',
            'ApiErrorResponse',
          );
          if (decoded is user_api.ApiErrorResponse) {
            errorResponse = decoded;
          }
        } catch (_) {
          // A malformed error body still leaves the Office launch fail-closed.
        }
        return officeLaunchFailClosedSnapshot(errorResponse);
      }
      if (error.code == 401 || error.code == 403) {
        throw AppFailure.unknown(
          'The Weave backend rejected the current session.',
          cause: error.code,
        );
      }
      throw AppFailure.unknown(
        'The Weave backend refused the Office launch request.',
        cause: error.code,
      );
    } on AppFailure {
      rethrow;
    } on TimeoutException {
      throw const AppFailure.unknown(
        'Unable to reach the Weave backend right now.',
      );
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to decode Office launch from the Weave backend.',
        cause: error,
      );
    }
  }
}
