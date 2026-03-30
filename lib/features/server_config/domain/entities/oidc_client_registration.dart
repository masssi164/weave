enum OidcClientRegistrationMode { manual }

class OidcClientRegistration {
  const OidcClientRegistration({required this.mode, required this.clientId});

  const OidcClientRegistration.manual({required this.clientId})
    : mode = OidcClientRegistrationMode.manual;

  final OidcClientRegistrationMode mode;
  final String clientId;

  OidcClientRegistration copyWith({
    OidcClientRegistrationMode? mode,
    String? clientId,
  }) {
    return OidcClientRegistration(
      mode: mode ?? this.mode,
      clientId: clientId ?? this.clientId,
    );
  }

  bool get isComplete => clientId.trim().isNotEmpty;
}
