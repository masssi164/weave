import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';

class StubAuthSessionRepository implements AuthSessionRepository {
  const StubAuthSessionRepository({required OidcClient client})
    : _client = client;

  final OidcClient _client;

  @override
  Future<bool> hasActiveSession() async {
    final _ = _client;
    return false;
  }
}
