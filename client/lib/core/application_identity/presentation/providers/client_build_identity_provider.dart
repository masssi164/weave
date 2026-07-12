import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:weave/core/application_identity/domain/client_build_identity.dart';

typedef PackageInfoLoader = Future<PackageInfo> Function();

final packageInfoLoaderProvider = Provider<PackageInfoLoader>((ref) {
  return PackageInfo.fromPlatform;
});

final clientBuildIdentityProvider = FutureProvider<ClientBuildIdentity>((
  ref,
) async {
  const candidateCommit = String.fromEnvironment('WEAVE_CANDIDATE_COMMIT');
  const evidenceReference = String.fromEnvironment(
    'WEAVE_CANDIDATE_EVIDENCE_REF',
  );

  try {
    final packageInfo = await ref.watch(packageInfoLoaderProvider)();
    return ClientBuildIdentity.supportSafe(
      candidateCommit: candidateCommit,
      version: packageInfo.version,
      buildNumber: packageInfo.buildNumber,
      bundleIdentifier: packageInfo.packageName,
      evidenceReference: evidenceReference,
    );
  } catch (_) {
    // Diagnostics remain available and explicit when package metadata cannot
    // be read. Candidate builds fail their traceability assertion rather than
    // displaying guessed artifact values.
    return ClientBuildIdentity.supportSafe(
      candidateCommit: candidateCommit,
      version: '',
      buildNumber: '',
      bundleIdentifier: '',
      evidenceReference: evidenceReference,
    );
  }
});
