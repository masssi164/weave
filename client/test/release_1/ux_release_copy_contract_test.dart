import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  group('ISO 9241-110 dogfood UX release gate', () {
    final gate = File('../docs/iso-9241-110-dogfood-ux-gate.md');

    test(
      'documents precise planning states and first-use acceptance criteria',
      () {
        // V01_USER_READY_ORG_FLOW
        expect(gate.existsSync(), isTrue);
        final markdown = gate.readAsStringSync();

        for (final required in <String>[
          'Preview is not a release-scope state',
          'Ready for users',
          'Admin setup required',
          'Disabled by policy',
          'Broken/degraded',
          'Not in this release',
          'First-use acceptance criteria',
          'Prompting checklist for future agent work',
          'Provider diagnostics | Hidden from members',
        ]) {
          expect(markdown, contains(required));
        }

        for (final marker in <String>['V01_USER_READY_ORG_FLOW']) {
          // ignore: avoid_print
          print(marker);
        }
      },
    );

    test(
      'release-scope localized copy does not leak preview/scaffold language',
      () {
        final banned = <Pattern>[
          RegExp(r'\bpreview\b', caseSensitive: false),
          RegExp(r'\bscaffold\b', caseSensitive: false),
          RegExp(r'\bcoming soon\b', caseSensitive: false),
          RegExp(r'\broadmap\b', caseSensitive: false),
          RegExp(r'\bfuture\b', caseSensitive: false),
          RegExp(r'\bvorschau\b', caseSensitive: false),
          RegExp(r'\bgerüst\b', caseSensitive: false),
          RegExp(r'\bdemnächst\b', caseSensitive: false),
          RegExp(r'\bkünft\w*\b', caseSensitive: false),
        ];

        final findings = <String>[];
        for (final path in <String>[
          'lib/l10n/app_en.arb',
          'lib/l10n/app_de.arb',
        ]) {
          final data =
              jsonDecode(File(path).readAsStringSync()) as Map<String, dynamic>;
          for (final entry in data.entries) {
            final key = entry.key;
            final value = entry.value;
            if (key.startsWith('@') || value is! String) {
              continue;
            }
            if (!_isReleaseScopeUserCopy(key)) {
              continue;
            }
            final normalized = value
                // Message snippets use the word "preview" as a variable name,
                // not as product maturity copy.
                .replaceAll('{preview}', '{latestActivity}')
                .replaceAll('{Preview}', '{latestActivity}');
            for (final pattern in banned) {
              if (pattern.allMatches(normalized).isNotEmpty) {
                findings.add('$path:$key -> $value');
              }
            }
          }
        }

        expect(
          findings,
          isEmpty,
          reason:
              'Normal member/release-scope copy must use ready/admin setup/policy/degraded/hidden states, not preview or roadmap language.',
        );
      },
    );

    test(
      'release-scope models do not classify visible surfaces as preview',
      () {
        final channelWorkspace = File(
          'lib/features/chat/domain/entities/channel_workspace.dart',
        ).readAsStringSync();
        final agentPolicy = File(
          'lib/features/agents/domain/entities/agent_capability_policy.dart',
        ).readAsStringSync();

        expect(
          channelWorkspace,
          isNot(contains('ChannelWorkspaceSurfaceAvailability.preview')),
        );
        expect(channelWorkspace, contains('notConfigured'));
        expect(channelWorkspace, contains('disabledByPolicy'));
        expect(agentPolicy, isNot(contains('previewOnly')));
        expect(agentPolicy, contains('disabledByPolicy'));
      },
    );
  });
}

bool _isReleaseScopeUserCopy(String key) {
  if (_featureGatedCopyPrefixes.any(key.startsWith)) {
    return false;
  }

  return _releaseScopePrefixes.any(key.startsWith);
}

const _releaseScopePrefixes = <String>[
  'welcome',
  'setup',
  'nav',
  'shell',
  'chatOverview',
  'chatFavorites',
  'chatPersonal',
  'chatChannels',
  'chatAi',
  'chatAgent',
  'chatProvisioning',
  'chatScreen',
  'chatRoom',
  'channelWorkspace',
  'files',
  'calendar',
  'deck',
  'settingsBrand',
  'settingsTheme',
  'settingsWorkspace',
  'settingsAdmin',
  'settingsHelp',
  'settingsShell',
  'agentCapability',
  'helpWhatIsWeave',
];

const _featureGatedCopyPrefixes = <String>[
  'settingsPreviewSurfaces',
  'settingsGuestPortalPreview',
  'settingsInteropAdminPreview',
  'settingsMigrationDryRunPreview',
];
