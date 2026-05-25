abstract final class FeatureFlags {
  static const guestPortal = bool.fromEnvironment('WEAVE_GUEST_PORTAL');
  static const interopAdmin = bool.fromEnvironment('WEAVE_INTEROP_ADMIN');
  static const migrationDryRun = bool.fromEnvironment(
    'WEAVE_MIGRATION_DRY_RUN',
  );
  static const legacyDirectMatrixChat = bool.fromEnvironment(
    'WEAVE_LEGACY_DIRECT_MATRIX_CHAT',
  );

  static const hasFeatureGatedSurfaces =
      guestPortal || interopAdmin || migrationDryRun || legacyDirectMatrixChat;
}
