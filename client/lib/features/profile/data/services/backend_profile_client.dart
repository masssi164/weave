import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/profile/data/dtos/user_profile_dto.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/integrations/weave_api/data/services/weave_api_uri_builder.dart';

class BackendProfileClient {
  BackendProfileClient({required http.Client httpClient})
    : _httpClient = httpClient;

  final http.Client _httpClient;

  Future<UserProfile> fetchProfile({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final response = await _send(
      () => _httpClient.get(
        weaveApiUri(baseUrl, const ['api', 'me']),
        headers: _headers(accessToken),
      ),
    );

    if (response.statusCode != 200) {
      throw _failureForStatus(response, 'load the profile');
    }

    try {
      final decoded = jsonDecode(response.body);
      if (decoded is! Map<String, dynamic>) {
        throw const AppFailure.unknown(
          'The Weave backend returned an invalid profile payload.',
        );
      }
      return UserProfileDto.fromJson(decoded).toDomain();
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to decode the profile from the Weave backend.',
        cause: error,
      );
    }
  }

  Future<UserProfile> updateProfile({
    required Uri baseUrl,
    required String accessToken,
    required UserProfileUpdate update,
  }) async {
    final response = await _send(
      () => _httpClient.patch(
        weaveApiUri(baseUrl, const ['api', 'profile']),
        headers: _headers(accessToken),
        body: jsonEncode(userProfileUpdateToJson(update)),
      ),
    );

    if (response.statusCode != 200) {
      throw _failureForStatus(response, 'save the profile');
    }

    try {
      final decoded = jsonDecode(response.body);
      if (decoded is! Map<String, dynamic>) {
        throw const AppFailure.unknown(
          'The Weave backend returned an invalid profile update payload.',
        );
      }
      return UserProfileDto.fromJson(decoded).toDomain();
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to decode the updated profile from the Weave backend.',
        cause: error,
      );
    }
  }

  Map<String, String> _headers(String accessToken) => {
    'Accept': 'application/json',
    'Content-Type': 'application/json',
    'Authorization': 'Bearer $accessToken',
  };

  Future<http.Response> _send(Future<http.Response> Function() request) async {
    try {
      return await request().timeout(const Duration(seconds: 8));
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to reach the Weave profile backend right now.',
        cause: error,
      );
    }
  }

  AppFailure _failureForStatus(http.Response response, String operation) {
    if (response.statusCode == 401 || response.statusCode == 403) {
      return AppFailure.unknown(
        'The Weave backend rejected the current profile session.',
        cause: response.statusCode,
      );
    }

    return AppFailure.unknown(
      'The Weave backend could not $operation.',
      cause: response.statusCode,
    );
  }
}
