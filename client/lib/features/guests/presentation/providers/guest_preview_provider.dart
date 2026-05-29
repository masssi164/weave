import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/guests/domain/entities/guest_preview.dart';

// Demo/backend-style fixture data. Widgets wrap missing-access messages with
// localized context before rendering them.
final guestPreviewProvider = Provider<List<GuestPreviewProfile>>((ref) {
  return const <GuestPreviewProfile>[
    GuestPreviewProfile(
      displayName: 'Mina Partner',
      email: 'mina.partner@example.test',
      status: GuestPreviewStatus.pending,
      allowedCapabilities: <GuestAccessCapability>{},
      missingAccessMessages: <String>[
        'Chat, files, and calendar stay hidden until the invite is accepted and a policy grants access.',
      ],
    ),
    GuestPreviewProfile(
      displayName: 'Sam Contractor',
      email: 'sam.contractor@example.test',
      status: GuestPreviewStatus.active,
      allowedCapabilities: <GuestAccessCapability>{GuestAccessCapability.chat},
      missingAccessMessages: <String>[
        'Files access requires an explicit guest policy from a workspace admin.',
        'Calendar access is not shared with this guest.',
      ],
    ),
    GuestPreviewProfile(
      displayName: 'Rae Alumni',
      email: 'rae.alumni@example.test',
      status: GuestPreviewStatus.disabled,
      allowedCapabilities: <GuestAccessCapability>{},
      missingAccessMessages: <String>[
        'This guest is disabled and cannot open workspace modules.',
      ],
    ),
    GuestPreviewProfile(
      displayName: 'Noor Vendor',
      email: 'noor.vendor@example.test',
      status: GuestPreviewStatus.expired,
      allowedCapabilities: <GuestAccessCapability>{},
      missingAccessMessages: <String>[
        'The invitation expired. Send a new invite before granting access.',
      ],
    ),
  ];
});
