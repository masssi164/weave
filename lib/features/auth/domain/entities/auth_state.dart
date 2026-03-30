import 'package:weave/features/auth/domain/entities/auth_session.dart';

enum AuthStatus { signedOut, authenticated }

class AuthState {
  const AuthState._({required this.status, this.session});

  const AuthState.signedOut() : this._(status: AuthStatus.signedOut);

  const AuthState.authenticated(AuthSession session)
    : this._(status: AuthStatus.authenticated, session: session);

  final AuthStatus status;
  final AuthSession? session;

  bool get isAuthenticated => status == AuthStatus.authenticated;
}
