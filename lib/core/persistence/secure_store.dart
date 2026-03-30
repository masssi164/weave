/// Boundary for future sensitive persistence such as tokens or secrets.
abstract interface class SecureStore {
  Future<String?> read(String key);

  Future<void> write(String key, String value);

  Future<void> delete(String key);
}
