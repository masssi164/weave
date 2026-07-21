import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('meeting architecture contract maps issue 216 acceptance', () async {
    final contract = await File(
      '../docs/meeting-architecture-decision.md',
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
      'Matrix Client-Server API v1.19',
      'Matrix Authentication Service (MAS)',
      'internal RTC Authorizer',
      'LiveKit is the first replaceable southbound SFU adapter',
      'obsolete member `/api/calls` routes',
      'Authorization is reevaluated for join and refresh',
      'Call signaling and membership',
      'Media transport',
      'Captions',
      'transcripts',
      'Recordings',
      'metadata',
      'device selection',
      'join preview',
      'mute, camera, leave, and end controls',
      'participant list',
      'physical-device proof',
      'does not claim',
    ]) {
      expect(contract, contains(required));
    }

    for (final required in <String>[
      'FR-001',
      'FR-002',
      'FR-003',
      'FR-004',
      'FR-005',
      'FR-006',
      'FR-007',
      'FR-008',
      'FR-009',
      'FR-010',
    ]) {
      expect(spec, contains(required));
    }

    for (final required in <String>[
      'ChannelMeetingAttachPoint',
      'ChannelMeetingEncryptionBoundary',
      'ChannelMeetingUxRequirement',
      'meeting join and start controls fail closed',
      'encryption claims name signaling, media, captions, transcripts, recordings, and metadata boundaries',
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

    expect(contract, contains('Do not claim `secure meetings`'));
    expect(contract, contains('without\nnaming and evidencing'));
    expect(
      contract,
      isNot(contains('Preferred first implementation candidate')),
    );
    expect(spec, contains('Matrix v1.19 plus pinned MatrixRTC Profile 0'));
    expect(
      traceability,
      contains('LiveKit is only a replaceable southbound SFU adapter'),
    );
  });
}
