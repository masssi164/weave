import 'package:weave/features/app/domain/ports/identity_session_port.dart';

class FakeIdentitySessionPort implements IdentitySessionPort {
  FakeIdentitySessionPort({
    this.result = IdentitySessionReconciliation.unchanged,
  });

  IdentitySessionReconciliation result;
  Object? error;
  int calls = 0;
  Uri? lastBaseUrl;
  String? lastAccessToken;

  @override
  Future<IdentitySessionReconciliation> reconcile({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    calls += 1;
    lastBaseUrl = baseUrl;
    lastAccessToken = accessToken;
    final failure = error;
    if (failure != null) {
      throw failure;
    }
    return result;
  }
}
