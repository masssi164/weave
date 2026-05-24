import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';

enum MemberCapabilityState { ready, disabled, degraded, policyBlocked }

class OrganizationManifestSnapshot {
  const OrganizationManifestSnapshot({
    required this.manifestVersion,
    required this.organizationId,
    required this.displayName,
    required this.organizationAuthUrl,
    required this.supportSafe,
    required this.providerConfigurationExposed,
    required this.diagnosticsExposed,
    required this.whitelistingOwner,
    required this.clientResponsibilities,
    required this.adminConsoleResponsibilities,
    required this.memberCapabilityStates,
    required this.capabilities,
    this.generatedAt,
  });

  final String manifestVersion;
  final String organizationId;
  final String displayName;
  final Uri organizationAuthUrl;
  final DateTime? generatedAt;
  final bool supportSafe;
  final bool providerConfigurationExposed;
  final bool diagnosticsExposed;
  final String whitelistingOwner;
  final List<String> clientResponsibilities;
  final List<String> adminConsoleResponsibilities;
  final Map<String, MemberCapabilityState> memberCapabilityStates;
  final WorkspaceCapabilitySnapshot capabilities;

  bool get safeForMemberClient =>
      supportSafe && !providerConfigurationExposed && !diagnosticsExposed;

  bool get whitelistingOwnedByAdminConsole =>
      whitelistingOwner == 'organization-admin-console';
}
