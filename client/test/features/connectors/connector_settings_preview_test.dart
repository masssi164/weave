import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/connectors/domain/entities/connector_preview.dart';
import 'package:weave/features/connectors/presentation/widgets/connector_settings_preview_card.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

void main() {
  testWidgets('renders governed connector states without secret entry fields', (
    tester,
  ) async {
    const connectors = <ConnectorPreviewCapability>[
      ConnectorPreviewCapability(
        name: 'Slack',
        status: ConnectorPreviewStatus.disabled,
        summary:
            'No OAuth, webhook, access token, or refresh token is collected.',
        providerActionsEnabled: false,
        auditSummary: 'Install and disconnect events will be audited.',
      ),
      ConnectorPreviewCapability(
        name: 'Teams',
        status: ConnectorPreviewStatus.unavailable,
        summary: 'Unavailable until Slack hardening.',
        providerActionsEnabled: false,
        auditSummary: 'Consent changes will be visible.',
      ),
      ConnectorPreviewCapability(
        name: 'Migration',
        status: ConnectorPreviewStatus.degraded,
        summary: 'Rate limits are visible before import.',
        providerActionsEnabled: false,
        auditSummary: 'Dry runs are replay-safe.',
      ),
      ConnectorPreviewCapability(
        name: 'Manifest',
        status: ConnectorPreviewStatus.actionRequired,
        summary: 'Requires reviewed scoped capabilities.',
        providerActionsEnabled: false,
        auditSummary: 'Secret values are rejected.',
      ),
      ConnectorPreviewCapability(
        name: 'Configured reference',
        status: ConnectorPreviewStatus.configured,
        summary: 'The backend exposes only a safe reference.',
        providerActionsEnabled: true,
        auditSummary:
            'Provider actions require enabled and configured metadata.',
      ),
    ];

    await tester.pumpWidget(
      const MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: SingleChildScrollView(
            child: ConnectorSettingsPreviewCard(connectors: connectors),
          ),
        ),
      ),
    );

    expect(find.text('Disabled'), findsOneWidget);
    expect(find.text('Unavailable'), findsOneWidget);
    expect(find.text('Degraded'), findsOneWidget);
    expect(find.text('Action required'), findsOneWidget);
    expect(find.text('Configured reference'), findsWidgets);
    expect(find.byType(TextField), findsNothing);
    expect(find.textContaining('access token'), findsOneWidget);
    expect(find.textContaining('Demo metadata:'), findsNWidgets(5));
    expect(find.text('Provider action unavailable'), findsNWidgets(4));
    expect(find.text('Backend-configured action preview only'), findsOneWidget);
  });

  testWidgets('exposes non-color status in connector semantics', (
    tester,
  ) async {
    const connector = ConnectorPreviewCapability(
      name: 'Slack',
      status: ConnectorPreviewStatus.degraded,
      summary: 'Rate-limited by provider.',
      providerActionsEnabled: false,
      auditSummary: 'Failure events are audited.',
    );

    await tester.pumpWidget(
      const MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: ConnectorSettingsPreviewCard(
            connectors: <ConnectorPreviewCapability>[connector],
          ),
        ),
      ),
    );

    final semantics = tester.getSemantics(find.text('Slack'));
    expect(semantics.label, contains('Status Degraded'));
    expect(semantics.label, contains('Provider actions are unavailable'));
    expect(semantics.label, contains('no provider secret is handled'));
  });

  testWidgets('renders localized German connector preview labels', (
    tester,
  ) async {
    const connector = ConnectorPreviewCapability(
      name: 'Slack',
      status: ConnectorPreviewStatus.degraded,
      summary: 'Rate-limited by provider.',
      providerActionsEnabled: false,
      auditSummary: 'Failure events are audited.',
    );

    await tester.pumpWidget(
      const MaterialApp(
        locale: Locale('de'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: ConnectorSettingsPreviewCard(
            connectors: <ConnectorPreviewCapability>[connector],
          ),
        ),
      ),
    );

    expect(find.text('Eingeschränkt'), findsOneWidget);
    expect(find.textContaining('Demo-Metadaten:'), findsOneWidget);
    expect(find.text('Provider-Aktion nicht verfügbar'), findsOneWidget);

    final semantics = tester.getSemantics(find.text('Slack'));
    expect(semantics.label, contains('Status Eingeschränkt'));
    expect(
      semantics.label,
      contains('die App verarbeitet kein Provider-Geheimnis'),
    );
  });
}
