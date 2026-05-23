import 'package:weave/features/onboarding/data/services/backend_onboarding_status_client.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';
import 'package:weave/features/onboarding/domain/repositories/first_run_status_repository.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

class BackendFirstRunStatusRepository implements FirstRunStatusRepository {
  const BackendFirstRunStatusRepository({
    required BackendOnboardingStatusClient client,
    required Future<WeaveAuthenticatedSession?> Function() sessionResolver,
  }) : _client = client,
       _sessionResolver = sessionResolver;

  final BackendOnboardingStatusClient _client;
  final Future<WeaveAuthenticatedSession?> Function() _sessionResolver;

  @override
  Future<FirstRunStatus?> loadStatus() async {
    final session = await _sessionResolver();
    if (session == null) {
      return null;
    }

    return _client.fetchStatus(
      baseUrl: session.apiBaseUrl,
      accessToken: session.accessToken,
    );
  }
}
