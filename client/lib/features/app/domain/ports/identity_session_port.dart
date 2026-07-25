enum IdentitySessionReconciliation { unchanged, accessUpdated }

abstract interface class IdentitySessionPort {
  Future<IdentitySessionReconciliation> reconcile({
    required Uri baseUrl,
    required String accessToken,
  });
}
