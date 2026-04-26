import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/profile/data/services/backend_profile_client.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/domain/repositories/user_profile_repository.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

class BackendUserProfileRepository implements UserProfileRepository {
  const BackendUserProfileRepository({
    required BackendProfileClient client,
    required Future<WeaveAuthenticatedSession?> Function() sessionResolver,
    this.profileEditingSupported = false,
  }) : _client = client,
       _sessionResolver = sessionResolver;

  final BackendProfileClient _client;
  final Future<WeaveAuthenticatedSession?> Function() _sessionResolver;
  final bool profileEditingSupported;

  @override
  Future<UserProfile?> loadProfile() async {
    final session = await _sessionResolver();
    if (session == null) {
      return null;
    }
    return _client.fetchProfile(
      baseUrl: session.apiBaseUrl,
      accessToken: session.accessToken,
    );
  }

  @override
  Future<UserProfile> updateProfile(UserProfileUpdate update) async {
    final session = await _sessionResolver();
    if (session == null) {
      throw const AppFailure.unknown(
        'Sign in before editing the Weave profile.',
      );
    }
    if (!profileEditingSupported) {
      throw const AppFailure.unknown(
        'Profile editing is waiting for backend PATCH /api/profile support.',
      );
    }
    return _client.updateProfile(
      baseUrl: session.apiBaseUrl,
      accessToken: session.accessToken,
      update: update,
    );
  }
}
