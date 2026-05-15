enum GuestPreviewStatus { pending, active, disabled, expired }

enum GuestAccessCapability { chat, files, calendar, memberDirectory, admin }

class GuestPreviewProfile {
  const GuestPreviewProfile({
    required this.displayName,
    required this.email,
    required this.status,
    required this.allowedCapabilities,
    required this.missingAccessMessages,
  });

  final String displayName;
  final String email;
  final GuestPreviewStatus status;
  final Set<GuestAccessCapability> allowedCapabilities;
  final List<String> missingAccessMessages;

  bool get canSeeMemberOnlyAffordances =>
      allowedCapabilities.contains(GuestAccessCapability.admin);
}
