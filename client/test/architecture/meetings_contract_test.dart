import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Matrix-native meeting architecture is internally consistent', () async {
    final contract = await File(
      '../docs/meeting-architecture-decision.md',
    ).readAsString();
    final profile = await File(
      '../docs/architecture/matrixrtc-profile-0.yaml',
    ).readAsString();
    final spec = await File(
      '../specs/0003-contextual-meetings/spec.md',
    ).readAsString();
    final traceability = await File(
      '../specs/0003-contextual-meetings/traceability.yaml',
    ).readAsString();
    final domain = await File(
      'lib/features/chat/domain/entities/channel_workspace.dart',
    ).readAsString();

    for (final required in <String>[
      'Matrix v1.19',
      '/.well-known/matrix/client',
      '/_matrix/client/v1/auth_metadata',
      'Authorization Code + PKCE',
      'Matrix Authentication Service',
      'Keycloak',
      '/_matrix/client/versions',
      'm.rtc.slot',
      'm.rtc.member',
      'matrixrtc_wire',
      'Matrix OpenID',
      'room/call policy',
      'NativeCallCoordinator',
      'MatrixRTC media E2EE',
      'DTLS-SRTP alone',
      'Files/WebDAV',
      'Experimental/Guarded',
      'no member `/api/weave/calls`',
      'no `com.weave.call.*`',
    ]) {
      expect(contract, contains(required));
    }

    for (final required in <String>[
      'weave.matrixrtc/profile-0',
      'specification: v1.19',
      'org.matrix.msc4143.stable',
      'dual_read_single_write: true',
      'known_draft_conflicts:',
      'authoritative_write_model: msc4143-slot-sticky-membership',
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

    for (final required in <String>[
      'Matrix v1.19 plus pinned MatrixRTC Profile 0 is the member northbound contract',
      'LiveKit is a replaceable RTC transport and SFU, not a Weave Calls API',
      'Matrix OpenID identity proof never substitutes for current room and call authorization',
      'legacy Calls APIs and fixtures remain guarded migration evidence with deletion criteria',
    ]) {
      expect(traceability, contains(required));
    }

    for (final required in <String>[
      'enum ChannelMeetingAttachPointKind',
      'enum ChannelMeetingEncryptionBoundaryKind',
      'enum ChannelMeetingUxRequirementKind',
      'canLinkFromChannelOrCalendar',
      'hasDocumentedEncryptionBoundaries',
      'hasAccessibleJoinContract',
      'preventsVagueSecurityClaims',
      'recordingEnabled: false',
      'transcriptionEnabled: false',
    ]) {
      expect(domain, contains(required));
    }

    expect(
      contract,
      isNot(contains('MatrixRTC / Element Call as a future comparison option')),
    );
    expect(
      spec,
      isNot(contains('LiveKit as the current active meetings provider contract')),
    );
    expect(
      traceability,
      isNot(contains('MatrixRTC/Element Call is future comparison only')),
    );
  });
}
