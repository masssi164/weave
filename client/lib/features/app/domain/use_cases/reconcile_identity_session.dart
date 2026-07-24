import 'package:weave/features/app/domain/ports/app_auth_port.dart';
import 'package:weave/features/app/domain/ports/identity_session_port.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';

class ReconcileIdentitySession {
  const ReconcileIdentitySession({
    required AppAuthPort authPort,
    required IdentitySessionPort identitySessionPort,
  }) : _authPort = authPort,
       _identitySessionPort = identitySessionPort;

  final AppAuthPort _authPort;
  final IdentitySessionPort _identitySessionPort;

  Future<AuthState> call({
    required AuthConfiguration authConfiguration,
    required Uri backendApiBaseUrl,
    required AuthState authenticated,
  }) async {
    final session = authenticated.session;
    if (!authenticated.isAuthenticated || session == null) {
      throw const AuthFailure.protocol(
        'The identity provider did not establish an authenticated session.',
      );
    }

    final reconciliation = await _reconcile(
      backendApiBaseUrl: backendApiBaseUrl,
      accessToken: session.accessToken,
    );
    if (reconciliation == IdentitySessionReconciliation.unchanged) {
      return authenticated;
    }
    if (!session.hasRefreshToken) {
      await _authPort.clearLocalSession();
      throw const AuthFailure.sessionRejected(
        'Identity access changed, but the session cannot be refreshed. Sign in again.',
      );
    }

    final refreshed = await _authPort.refreshSession(authConfiguration);
    if (!refreshed.isAuthenticated || refreshed.session == null) {
      throw const AuthFailure.sessionRejected(
        'Identity access changed, but the refreshed session was rejected. Sign in again.',
      );
    }
    return refreshed;
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
