abstract interface class AuthSessionRepository {
  Future<bool> hasActiveSession();
}
