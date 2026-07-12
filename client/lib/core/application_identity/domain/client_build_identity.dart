/// Support-safe immutable identity for the installed Weave application.
///
/// Package metadata comes from the installed artifact. Candidate metadata is
/// embedded at compile time so support can distinguish otherwise identical
/// version/build pairs without relying on mutable runtime state.
class ClientBuildIdentity {
  const ClientBuildIdentity({
    required this.candidateCommit,
    required this.version,
    required this.buildNumber,
    required this.bundleIdentifier,
    required this.evidenceReference,
  });

  static const unavailableValue = 'not embedded';

  final String candidateCommit;
  final String version;
  final String buildNumber;
  final String bundleIdentifier;
  final String evidenceReference;

  bool get isCandidateTraceable =>
      candidateCommit != unavailableValue &&
      version != unavailableValue &&
      buildNumber != unavailableValue &&
      bundleIdentifier != unavailableValue &&
      evidenceReference != unavailableValue;

  factory ClientBuildIdentity.supportSafe({
    required String candidateCommit,
    required String version,
    required String buildNumber,
    required String bundleIdentifier,
    required String evidenceReference,
  }) {
    return ClientBuildIdentity(
      candidateCommit: _validated(
        candidateCommit,
        RegExp(r'^[0-9a-fA-F]{7,64}$'),
      ),
      version: _validated(version, RegExp(r'^[0-9A-Za-z.+-]{1,64}$')),
      buildNumber: _validated(buildNumber, RegExp(r'^[0-9A-Za-z.+-]{1,64}$')),
      bundleIdentifier: _validated(
        bundleIdentifier,
        RegExp(r'^[0-9A-Za-z._-]{1,160}$'),
      ),
      evidenceReference: _validated(
        evidenceReference,
        RegExp(r'^[0-9A-Za-z._:/#-]{1,256}$'),
      ),
    );
  }

  static String _validated(String value, RegExp allowed) {
    final trimmed = value.trim();
    return allowed.hasMatch(trimmed) ? trimmed : unavailableValue;
  }
}
