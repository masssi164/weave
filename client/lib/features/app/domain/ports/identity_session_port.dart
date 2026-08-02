enum IdentitySessionReconciliation { unchanged, reauthorizationRequired }

abstract interface class IdentitySessionPort {
  Future<IdentitySessionReconciliation> reconcile({
    required Uri baseUrl,
    required String accessToken,
  });
}
