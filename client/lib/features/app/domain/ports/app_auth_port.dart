import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';

abstract interface class AppAuthPort {
  Future<AuthState> restoreSession(AuthConfiguration configuration);

  Future<AuthState> signIn(AuthConfiguration configuration);

  Future<void> signOut(AuthConfiguration configuration);

  Future<void> clearLocalSession();
}
