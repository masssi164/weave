abstract interface class ChatSessionPort {
  Future<void> ensureSession();

  Future<void> signOut();

  Future<void> clearSession();
}
