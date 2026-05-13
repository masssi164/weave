import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/profile/data/repositories/backend_user_profile_repository.dart';
import 'package:weave/features/profile/data/services/backend_profile_client.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

void main() {
  group('BackendUserProfileRepository', () {
    test(
      'keeps profile editing blocked until the backend exposes PATCH /api/profile',
      () async {
        final repository = BackendUserProfileRepository(
          client: BackendProfileClient(httpClient: http.Client()),
          sessionResolver: () async => WeaveAuthenticatedSession(
            apiBaseUrl: Uri.parse('https://api.weave.local/api'),
            accessToken: 'token-123',
          ),
        );

        await expectLater(
          () => repository.updateProfile(
            const UserProfileUpdate(displayName: 'Alice Example'),
          ),
          throwsA(
            isA<AppFailure>().having(
              (failure) => failure.message,
              'message',
              contains('PATCH /api/profile'),
            ),
          ),
        );
      },
    );
  });
}
