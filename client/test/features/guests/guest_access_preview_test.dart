import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/guests/domain/entities/guest_preview.dart';
import 'package:weave/features/guests/presentation/widgets/guest_access_preview_card.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
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
    expect(find.textContaining('Demo access note:'), findsNWidgets(4));
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
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

  testWidgets('renders localized German guest preview copy and semantics', (
    tester,
  ) async {
    const guest = GuestPreviewProfile(
      displayName: 'Sam Auftragnehmer',
      email: 'sam@example.test',
      status: GuestPreviewStatus.active,
      allowedCapabilities: <GuestAccessCapability>{GuestAccessCapability.chat},
      missingAccessMessages: <String>['Calendar access is not shared.'],
    );

    await tester.pumpWidget(
      const MaterialApp(
        locale: Locale('de'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: GuestAccessPreviewCard(guests: <GuestPreviewProfile>[guest]),
        ),
      ),
    );

    expect(find.text('Aktiver Gast'), findsOneWidget);
    expect(find.text('Erlaubter Zugriff: Chat.'), findsOneWidget);
    expect(find.textContaining('Demo-Zugriffshinweis:'), findsOneWidget);

    final semantics = tester.getSemantics(find.text('Sam Auftragnehmer'));
    expect(semantics.label, contains('Gastidentität Sam Auftragnehmer'));
    expect(semantics.label, contains('Aktiver Gast'));
  });
}
