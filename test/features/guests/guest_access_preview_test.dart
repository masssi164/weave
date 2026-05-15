import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/guests/domain/entities/guest_preview.dart';
import 'package:weave/features/guests/presentation/widgets/guest_access_preview_card.dart';

void main() {
  testWidgets('renders guest states with restricted-access explanations', (
    tester,
  ) async {
    const guests = <GuestPreviewProfile>[
      GuestPreviewProfile(
        displayName: 'Pending Partner',
        email: 'pending@example.test',
        status: GuestPreviewStatus.pending,
        allowedCapabilities: <GuestAccessCapability>{},
        missingAccessMessages: <String>['Invite acceptance is required.'],
      ),
      GuestPreviewProfile(
        displayName: 'Active Contractor',
        email: 'active@example.test',
        status: GuestPreviewStatus.active,
        allowedCapabilities: <GuestAccessCapability>{
          GuestAccessCapability.chat,
        },
        missingAccessMessages: <String>[
          'Files access requires an explicit guest policy.',
        ],
      ),
      GuestPreviewProfile(
        displayName: 'Disabled Vendor',
        email: 'disabled@example.test',
        status: GuestPreviewStatus.disabled,
        allowedCapabilities: <GuestAccessCapability>{},
        missingAccessMessages: <String>['This guest is disabled.'],
      ),
      GuestPreviewProfile(
        displayName: 'Expired Reviewer',
        email: 'expired@example.test',
        status: GuestPreviewStatus.expired,
        allowedCapabilities: <GuestAccessCapability>{},
        missingAccessMessages: <String>['The invitation expired.'],
      ),
    ];

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: GuestAccessPreviewCard(guests: guests),
          ),
        ),
      ),
    );

    expect(find.text('Pending invitation'), findsOneWidget);
    expect(find.text('Active guest'), findsOneWidget);
    expect(find.text('Disabled guest'), findsOneWidget);
    expect(find.text('Expired invitation'), findsOneWidget);
    expect(find.text('Guest identity · active@example.test'), findsOneWidget);
    expect(find.text('Allowed access: chat.'), findsOneWidget);
    expect(
      find.text(
        'Owner, admin, and member-only affordances are hidden for this guest.',
      ),
      findsNWidgets(4),
    );
    expect(find.textContaining('internal policy'), findsOneWidget);
  });

  testWidgets('exposes a screen-reader summary for guest identity state', (
    tester,
  ) async {
    const guest = GuestPreviewProfile(
      displayName: 'Sam Contractor',
      email: 'sam@example.test',
      status: GuestPreviewStatus.active,
      allowedCapabilities: <GuestAccessCapability>{GuestAccessCapability.chat},
      missingAccessMessages: <String>['Calendar access is not shared.'],
    );

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: GuestAccessPreviewCard(guests: <GuestPreviewProfile>[guest]),
        ),
      ),
    );

    final semantics = tester.getSemantics(find.text('Sam Contractor'));
    expect(semantics.label, contains('Guest identity Sam Contractor'));
    expect(semantics.label, contains('Active guest'));
    expect(semantics.label, contains('Allowed access: chat.'));
  });
}
