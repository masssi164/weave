import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

// V01_ADMIN_CONSOLE_MVP / issue #350: normal members consume Weave product state only.
void main() {
  test(
    'member client does not call admin/provider control-plane APIs directly',
    () {
      final libFiles = Directory('lib')
          .listSync(recursive: true)
          .whereType<File>()
          .where((file) => file.path.endsWith('.dart'));

      final forbiddenFragments = <String>[
        '/admin/control-plane',
        'deploy_new',
        'attach_existing',
        'hybrid setup',
        '/admin/identity/readiness',
        '/identity/readiness',
        '/admin/providers',
        '/admin/policies',
        '/admin/audit',
        'secretref://',
        'SecretRef inventory',
        'provider replacement dry-run',
        'CI/CD pipeline',
        'OIDC client setup',
      ];

      for (final file in libFiles) {
        final source = file.readAsStringSync();
        for (final fragment in forbiddenFragments) {
          expect(
            source,
            isNot(contains(fragment)),
            reason:
                '${file.path} must not expose admin/provider control-plane fragment `$fragment` to normal members.',
          );
        }
      }
    },
  );

  test('member client does not add optional provider SDK imports', () {
    final libFiles = Directory('lib')
        .listSync(recursive: true)
        .whereType<File>()
        .where((file) => file.path.endsWith('.dart'));

    final forbiddenImportFragments = <String>[
      'package:slack_',
      'package:microsoft_graph',
      'package:msgraph',
      'package:nextcloud',
      'package:livekit',
      'package:caldav',
      'package:matrix',
      'package:flutter_vodozemac',
    ];

    for (final file in libFiles) {
      final source = file.readAsStringSync();
      for (final fragment in forbiddenImportFragments) {
        expect(
          source,
          isNot(contains(fragment)),
          reason:
              '${file.path} must use Weave backend facades instead of optional provider SDK imports.',
        );
      }
    }
  });

  test('member feature providers do not import raw provider SDKs', () {
    final libFiles = Directory('lib')
        .listSync(recursive: true)
        .whereType<File>()
        .where((file) => file.path.endsWith('.dart'));

    for (final file in libFiles) {
      final source = file.readAsStringSync();
      final normalizedPath = file.path.replaceAll(r'\', '/');
      expect(
        source,
        isNot(contains("package:matrix/")),
        reason:
            '$normalizedPath must use the Weave Matrix facade and Rust bridge, not the Dart Matrix SDK.',
      );
      expect(
        source,
        isNot(contains("package:flutter_vodozemac/")),
        reason:
            '$normalizedPath must not reintroduce the old Matrix SDK crypto dependency.',
      );
    }

    final filesFeatureSources = Directory('lib/features/files')
        .listSync(recursive: true)
        .whereType<File>()
        .where((file) => file.path.endsWith('.dart'));
    final forbiddenFilesFragments = <String>[
      'integrations/nextcloud',
      'nextcloudDavClientProvider',
      'NextcloudDavClient',
      'WebDAV',
      'webdav',
    ];
    for (final file in filesFeatureSources) {
      final imports = file
          .readAsLinesSync()
          .where((line) => line.trimLeft().startsWith('import '))
          .join('\n');
      for (final fragment in forbiddenFilesFragments) {
        expect(
          imports,
          isNot(contains(fragment)),
          reason:
              '${file.path} must use the backend Files facade instead of direct provider seams.',
        );
      }
    }
  });

  test('normal member settings cannot reach Matrix security diagnostics', () {
    final settings = File(
      'lib/features/settings/presentation/settings_screen.dart',
    ).readAsStringSync();

    expect(settings, isNot(contains('ChatSecuritySettingsSection')));
    expect(settings, isNot(contains('chat_security_settings_section.dart')));
    expect(settings, isNot(contains('chatSecurityProvider')));
    expect(settings, isNot(contains('chatSecurityRepositoryProvider')));
    expect(settings, isNot(contains('MatrixChatSecurityRepository')));
    expect(settings, isNot(contains('RustMatrixCoreChatSecurityRepository')));
  });

  test('normal member routes cannot mount diagnostic Matrix providers', () {
    final normalMemberRouteFiles = <String>[
      'lib/core/router/app_router.dart',
      'lib/features/home/presentation/home_screen.dart',
      'lib/features/chat/presentation/chat_screen.dart',
      'lib/features/chat/presentation/chat_room_screen.dart',
      'lib/features/files/presentation/files_screen.dart',
      'lib/features/calendar/presentation/calendar_screen.dart',
      'lib/features/profile/presentation/profile_screen.dart',
      'lib/features/help/presentation/help_screen.dart',
      'lib/features/settings/presentation/settings_screen.dart',
      'lib/features/shell/presentation/app_shell.dart',
      'lib/features/shell/presentation/shell_workspace_status.dart',
    ];
    final forbiddenFragments = <String>[
      'ChatSecuritySettingsSection',
      'chat_security_settings_section.dart',
      'chatSecurityProvider',
      'chatSecurityRepositoryProvider',
      'MatrixChatSecurityRepository',
      'RustMatrixCoreChatSecurityRepository',
      'MatrixE2eeDiagnostic',
      'weaveApiMatrixE2eeDiagnosticProvider',
      'fetchMatrixE2eeDiagnostic',
      'PlatformStatusResponseDto',
    ];

    for (final path in normalMemberRouteFiles) {
      final source = File(path).readAsStringSync();
      for (final fragment in forbiddenFragments) {
        expect(
          source,
          isNot(contains(fragment)),
          reason:
              '$path must stay on Weave domain/capability facades and must not mount diagnostic Matrix provider fragment `$fragment`.',
        );
      }
    }
  });
}
