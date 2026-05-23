import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';

final authSessionRepositoryProvider = Provider<AuthSessionRepository>((ref) {
  final secureStore = ref.watch(secureStoreProvider);
  final oidcClient = ref.watch(oidcClientProvider);
  return OidcAuthSessionRepository(
    secureStore: secureStore,
    oidcClient: oidcClient,
  );
});
