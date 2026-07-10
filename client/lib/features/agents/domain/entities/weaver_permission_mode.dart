enum WeaverPermissionMode {
  deny,
  allowlist,
  ask,
  auto,
  full;

  static WeaverPermissionMode fromWire(String? value) {
    return WeaverPermissionMode.values.firstWhere(
      (mode) => mode.name == value,
      orElse: () => WeaverPermissionMode.ask,
    );
  }

  bool get isDangerous => this == WeaverPermissionMode.full;
}

class WeaverPermissionModeUpdate {
  const WeaverPermissionModeUpdate({
    required this.accepted,
    required this.mode,
    required this.dangerous,
    required this.policyReason,
    required this.runtimeProfileHash,
  });

  final bool accepted;
  final WeaverPermissionMode mode;
  final bool dangerous;
  final String policyReason;
  final String runtimeProfileHash;
}
