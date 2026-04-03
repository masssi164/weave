abstract interface class ChatSessionPort {
  Future<void> signOut();

  Future<void> clearSession();

  void invalidateActiveSession();
}
