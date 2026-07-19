import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Matrix-native Calls contract is strict and internally consistent', () async {
    final decision = await File(
      '../docs/meeting-architecture-decision.md',
    ).readAsString();
    final profile = await File(
      '../docs/architecture/matrixrtc-profile-0.yaml',
    ).readAsString();
    final spec = await File(
      '../specs/0003-contextual-meetings/spec.md',
    ).readAsString();

    for (final required in <String>[
      'Matrix Client-Server v1.19',
      '/.well-known/matrix/client',
      '/_matrix/client/v1/auth_metadata',
      'Authorization Code + PKCE S256',
      'Matrix Authentication Service',
      'Keycloak',
      'm.rtc.slot',
      'm.rtc.member',
      'matrixrtc_wire',
      'Matrix OpenID',
      'NativeCallCoordinator',
      'MatrixRTC media E2EE',
      'DTLS-SRTP alone',
      'Files/WebDAV',
      'Experimental/Guarded',
      'no compatibility reader',
    ]) {
      expect(decision, contains(required));
    }

    for (final required in <String>[
      'weave.matrixrtc/profile-0',
      'specification: v1.19',
      'compatibility_policy: strict-cutover',
      'read_policy: strict-profile-0-only',
      'write_policy: strict-profile-0-only',
      'reject_unknown_or_legacy_shapes: true',
      'identity_input: matrix-openid-credential',
      'local_gap_module: matrixrtc_wire',
      'dtls_srtp_only_is_e2ee: false',
      'status: experimental-guarded',
    ]) {
      expect(profile, contains(required));
    }

    for (var requirement = 1; requirement <= 20; requirement++) {
      expect(spec, contains('FR-${requirement.toString().padLeft(3, '0')}'));
    }

    for (final forbidden in <String>[
      'dual_read_single_write: true',
      'compatibility_reads:',
      'unstable_fallback_endpoint:',
      'legacy Calls APIs and fixtures remain guarded migration evidence',
      'LiveKit as the current active meetings provider contract',
    ]) {
      expect(profile + decision + spec, isNot(contains(forbidden)));
    }
  });
}
