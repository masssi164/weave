import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/onboarding/data/repositories/backend_first_run_status_repository.dart';
import 'package:weave/features/onboarding/data/services/backend_onboarding_status_client.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';
import 'package:weave/features/onboarding/domain/repositories/first_run_status_repository.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
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

Duration? _doNotRetryFirstRunStatus(int retryCount, Object error) => null;

final firstRunStatusProvider = FutureProvider<FirstRunLoadResult>((ref) async {
  ref.watch(weaveAuthenticatedSessionProvider);
  final result = await ref.watch(firstRunStatusRepositoryProvider).loadStatus();
  if (result is FirstRunUnauthorized) {
    await ref.read(authSessionRepositoryProvider).clearLocalSession();
    ref.invalidate(weaveAuthenticatedSessionProvider);
  }
  return result;
}, retry: _doNotRetryFirstRunStatus);

final chatProvisioningStatusProvider =
    Provider<AsyncValue<FirstRunModuleStatus?>>((ref) {
      return ref.watch(firstRunStatusProvider).whenData((result) {
        return switch (result) {
          FirstRunAuthenticated(:final status) =>
            status.moduleProvisioning.chat,
          _ => null,
        };
      });
    });

void refreshChatProvisioningStatus(WidgetRef ref) {
  ref.invalidate(firstRunStatusProvider);
}
