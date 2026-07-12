abstract interface class ClientUpgradePort {
  Future<void> removeObsoleteAuthenticatedState();
}
