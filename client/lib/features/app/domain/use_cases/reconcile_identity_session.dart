import 'package:weave/features/app/domain/ports/identity_session_port.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';

class ReconcileIdentitySession {
  const ReconcileIdentitySession({
    required IdentitySessionPort identitySessionPort,
  }) : _identitySessionPort = identitySessionPort;

  final IdentitySessionPort _identitySessionPort;

  Future<IdentitySessionReconciliation> call({
    required Uri backendApiBaseUrl,
    required AuthState authenticated,
  }) async {
    final session = authenticated.session;
    if (!authenticated.isAuthenticated || session == null) {
      throw const AuthFailure.protocol(
        'The identity provider did not establish an authenticated session.',
      );
    }

    return _reconcile(
      backendApiBaseUrl: backendApiBaseUrl,
      accessToken: session.accessToken,
    );
  }

  Future<IdentitySessionReconciliation> _reconcile({
    required Uri backendApiBaseUrl,
    required String accessToken,
  }) async {
    try {
      return await _identitySessionPort.reconcile(
        baseUrl: backendApiBaseUrl,
        accessToken: accessToken,
      );
    } on AuthFailure {
      rethrow;
    } catch (error) {
      throw AuthFailure.protocol(
        'Unable to reconcile organization access after authentication.',
        cause: error,
      );
    }
  }
}
