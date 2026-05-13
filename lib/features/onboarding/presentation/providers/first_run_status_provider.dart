import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/onboarding/data/repositories/backend_first_run_status_repository.dart';
import 'package:weave/features/onboarding/data/services/backend_onboarding_status_client.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';
import 'package:weave/features/onboarding/domain/repositories/first_run_status_repository.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

final backendOnboardingStatusClientProvider =
    Provider<BackendOnboardingStatusClient>((ref) {
      return BackendOnboardingStatusClient(
        httpClient: ref.watch(weaveApiHttpClientProvider),
      );
    });

final firstRunStatusRepositoryProvider = Provider<FirstRunStatusRepository>((
  ref,
) {
  return BackendFirstRunStatusRepository(
    client: ref.watch(backendOnboardingStatusClientProvider),
    sessionResolver: () => ref.read(weaveAuthenticatedSessionProvider.future),
  );
});

final firstRunStatusProvider = FutureProvider<FirstRunStatus?>((ref) async {
  ref.watch(weaveAuthenticatedSessionProvider);
  return ref.watch(firstRunStatusRepositoryProvider).loadStatus();
});
