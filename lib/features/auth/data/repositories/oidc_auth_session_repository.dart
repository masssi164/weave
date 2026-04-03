import 'package:weave/core/persistence/secure_store.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/domain/entities/auth_session.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';

const authSessionStorageKey = 'auth_session_v1';

class OidcAuthSessionRepository implements AuthSessionRepository {
  const OidcAuthSessionRepository({
    required SecureStore secureStore,
    required OidcClient oidcClient,
  }) : _secureStore = secureStore,
       _oidcClient = oidcClient;

  final SecureStore _secureStore;
  final OidcClient _oidcClient;

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async {
    final session = await _readSession();
    if (session == null) {
      return const AuthState.signedOut();
    }

    if (!session.matches(configuration)) {
      await clearLocalSession();
      return const AuthState.signedOut();
    }

    if (!session.requiresRefresh) {
      return AuthState.authenticated(session);
    }

    if (!session.hasRefreshToken) {
      await clearLocalSession();
      return const AuthState.signedOut();
    }

    return refreshSession(configuration);
  }

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async {
    final tokens = await _oidcClient.authorizeAndExchangeCode(configuration);
    final session = _sessionFromTokens(configuration, tokens);
    await _writeSession(session);
    return AuthState.authenticated(session);
  }

  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async {
    final currentSession = await _readSession();
    if (currentSession == null || !currentSession.matches(configuration)) {
      await clearLocalSession();
      return const AuthState.signedOut();
    }

    final refreshToken = currentSession.refreshToken;
    if (refreshToken == null || refreshToken.isEmpty) {
      await clearLocalSession();
      return const AuthState.signedOut();
    }

    try {
      final tokens = await _oidcClient.refresh(
        configuration,
        refreshToken: refreshToken,
      );
      final refreshedSession = _sessionFromTokens(
        configuration,
        tokens,
        fallbackRefreshToken: currentSession.refreshToken,
        fallbackIdToken: currentSession.idToken,
        fallbackScopes: currentSession.scopes,
      );
      await _writeSession(refreshedSession);
      return AuthState.authenticated(refreshedSession);
    } on AuthFailure catch (failure) {
      if (failure.type == AuthFailureType.storage) {
        rethrow;
      }

      await clearLocalSession();
      return const AuthState.signedOut();
    }
  }

  @override
  Future<void> signOut(AuthConfiguration configuration) async {
    final session = await _readSession();
    await clearLocalSession();

    final idToken = session?.idToken;
    if (idToken == null || idToken.isEmpty) {
      return;
    }

    try {
      await _oidcClient.endSession(configuration, idTokenHint: idToken);
    } on AuthFailure {
      // Local logout always wins, even if the OP logout endpoint fails.
    }
  }

  @override
  Future<void> clearLocalSession() async {
    try {
      await _secureStore.delete(authSessionStorageKey);
    } catch (error) {
      throw AuthFailure.storage(
        'Unable to clear the saved session.',
        cause: error,
      );
    }
  }

  AuthSession _sessionFromTokens(
    AuthConfiguration configuration,
    OidcTokenBundle tokens, {
    String? fallbackRefreshToken,
    String? fallbackIdToken,
    List<String>? fallbackScopes,
  }) {
    return AuthSession(
      issuer: configuration.issuer,
      clientId: configuration.clientId,
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken ?? fallbackRefreshToken,
      idToken: tokens.idToken ?? fallbackIdToken,
      expiresAt: tokens.expiresAt,
      tokenType: tokens.tokenType,
      scopes: tokens.scopes.isEmpty
          ? (fallbackScopes ?? const <String>[])
          : tokens.scopes,
    );
  }

  Future<AuthSession?> _readSession() async {
    try {
      final raw = await _secureStore.read(authSessionStorageKey);
      if (raw == null || raw.isEmpty) {
        return null;
      }

      return AuthSessionDto.decode(raw).toSession();
    } catch (error) {
      throw AuthFailure.storage(
        'Unable to read the saved session.',
        cause: error,
      );
    }
  }

  Future<void> _writeSession(AuthSession session) async {
    try {
      await _secureStore.write(
        authSessionStorageKey,
        AuthSessionDto.fromSession(session).encode(),
      );
    } catch (error) {
      throw AuthFailure.storage('Unable to save the session.', cause: error);
    }
  }
}
