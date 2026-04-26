import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/profile/data/repositories/backend_user_profile_repository.dart';
import 'package:weave/features/profile/data/services/backend_profile_client.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/domain/repositories/user_profile_repository.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

final backendProfileClientProvider = Provider<BackendProfileClient>((ref) {
  return BackendProfileClient(
    httpClient: ref.watch(weaveApiHttpClientProvider),
  );
});

final userProfileRepositoryProvider = Provider<UserProfileRepository>((ref) {
  return BackendUserProfileRepository(
    client: ref.watch(backendProfileClientProvider),
    sessionResolver: () => ref.read(weaveAuthenticatedSessionProvider.future),
  );
});

final userProfileProvider = FutureProvider<UserProfile?>((ref) async {
  ref.watch(weaveAuthenticatedSessionProvider);
  return ref.watch(userProfileRepositoryProvider).loadProfile();
});
