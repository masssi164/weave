import 'dart:async';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/profile/data/dtos/user_profile_dto.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/generated/user_api/api.dart' as user_api;
import 'package:weave/integrations/weave_api/data/services/weave_user_api_client.dart';

class BackendProfileClient {
  BackendProfileClient({required http.Client httpClient})
    : _httpClient = httpClient;

  final http.Client _httpClient;

  Future<UserProfile> fetchProfile({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final api = weaveUserApiClient(
      apiBaseUrl: baseUrl,
      accessToken: accessToken,
      httpClient: _httpClient,
    );
    return _send('load the profile', () async {
      final response = await user_api.IdentityApi(api).me();
      if (response == null) {
        throw const AppFailure.unknown(
          'The Weave backend returned an invalid profile payload.',
        );
      }
      return response.toDomain();
    });
  }

  Future<UserProfile> updateProfile({
    required Uri baseUrl,
    required String accessToken,
    required UserProfileUpdate update,
  }) async {
    final api = weaveUserApiClient(
      apiBaseUrl: baseUrl,
      accessToken: accessToken,
      httpClient: _httpClient,
    );
    return _send('save the profile', () async {
      final response = await user_api.ProfileApi(
        api,
      ).updateProductProfile(userProfileUpdateToOpenApi(update));
      if (response == null) {
        throw const AppFailure.unknown(
          'The Weave backend returned an invalid profile update payload.',
        );
      }
      return response.toDomain();
    });
  }

  Future<UserProfile> _send(
    String operation,
    Future<UserProfile> Function() request,
  ) async {
    try {
      return await request().timeout(const Duration(seconds: 8));
    } on AppFailure {
      rethrow;
    } on user_api.ApiException catch (error) {
      if (error.code == 401 || error.code == 403) {
        throw AppFailure.unknown(
          'The Weave backend rejected the current profile session.',
          cause: error.code,
        );
      }
      throw AppFailure.unknown(
        'The Weave backend could not $operation.',
        cause: error.code,
      );
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to reach or decode the Weave profile backend right now.',
        cause: error,
      );
    }
  }
}
