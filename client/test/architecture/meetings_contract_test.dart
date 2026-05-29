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
      'LiveKit as the active meetings/video-call provider key',
      'LiveKit-style SFU',
      'MatrixRTC / Element Call',
      'future comparison option',
      'Generic hosted meeting links',
      'Matrix signaling',
      'Media streams',
      'Captions',
      'Transcripts',
      'Recordings',
      'Metadata',
      'channel context',
      'calendar event context',
      'thread context',
      'device selection',
      'join preview',
      'mute and camera state',
      'participant list',
      'errors with retry',
      'Recording, transcription, and captions are off by default',
      'Vague-claim guard',
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
    expect(contract, contains('without naming the boundary and evidence'));
    expect(
      contract,
      isNot(contains('Preferred first implementation candidate')),
    );
    expect(
      spec,
      contains('LiveKit as the current active meetings provider contract'),
    );
    expect(
      traceability,
      contains('LiveKit remains the active meetings provider contract'),
    );
  });
}
