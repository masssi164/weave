import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  group('OpenAPI facade drift prevention', () {
    test(
      'OpenAPI-covered responses do not add hand-written ResponseDto types',
      () {
        final findings = <String>[];

        for (final file in _dartFilesUnder('lib')) {
          if (_isGenerated(file)) continue;
          final source = File(file).readAsStringSync();
          for (final match in _responseDtoPattern.allMatches(source)) {
            final className = match.group(1)!;
            if (!_isOpenApiCoveredResponseDto(className)) continue;
            if (_isResponseDtoAllowed(file, className)) continue;
            findings.add('$file defines $className');
          }
        }

        expect(
          findings,
          isEmpty,
          reason:
              'OpenAPI-covered response families must use generated models plus '
              'extensions/mappers. Any legacy class must be listed in '
              '_legacyResponseDtoAllowlist with an expiry issue.',
        );
      },
    );

    test('member facade endpoints do not parse raw JSON payload fields', () {
      final findings = <String>[];

      for (final file in _dartFilesUnder('lib')) {
        if (_isGenerated(file)) continue;
        if (!_isOpenApiCoveredMemberFacadeFile(file)) continue;
        if (_isAllowed(file, _rawJsonMemberFacadeAllowlist)) continue;

        final lines = File(file).readAsLinesSync();
        for (var index = 0; index < lines.length; index += 1) {
          final line = _withoutComment(lines[index]);
          if (_rawJsonLookupPattern.hasMatch(line)) {
            findings.add('$file:${index + 1}: ${line.trim()}');
          }
        }
      }

      expect(
        findings,
        isEmpty,
        reason:
            'OpenAPI-covered member facade clients must decode generated '
            'models first. Calendar/Boards and other transitional exceptions '
            'must stay explicitly allowlisted with expiry issues.',
      );
    });

    test('normal member provider graph cannot reach raw provider seams', () {
      final findings = <String>[];

      for (final file in _normalMemberProviderGraphFiles()) {
        if (_isAllowed(file, _memberProviderGraphAllowlist)) continue;
        final source = File(file).readAsStringSync();
        for (final pattern in _rawProviderReachabilityPatterns) {
          if (pattern.hasMatch(source)) {
            findings.add('$file reaches ${pattern.pattern}');
          }
        }
      }

      expect(
        findings,
        isEmpty,
        reason:
            'Normal member providers/screens must depend on Weave facades, not '
            'Nextcloud, Matrix, provider status, or platform diagnostic seams. '
            'Diagnostic/admin exceptions require an allowlist expiry issue.',
      );
    });

    test('user-facing hardcoded copy stays fenced until localized', () {
      final findings = <String>[];

      for (final file in _dartFilesUnder('lib')) {
        if (_isGenerated(file)) continue;
        if (_isLocalizationApprovedPath(file)) continue;
        if (_isAllowed(file, _hardcodedCopyAllowlist)) continue;
        if (!_isUserFacingLayer(file)) continue;

        final lines = File(file).readAsLinesSync();
        for (var index = 0; index < lines.length; index += 1) {
          final line = _withoutComment(lines[index]);
          if (_looksLikeUserFacingString(line)) {
            findings.add('$file:${index + 1}: ${line.trim()}');
          }
        }
      }

      expect(
        findings,
        isEmpty,
        reason:
            'Member-visible copy must flow through l10n or typed error/state '
            'codes. Current hardcoded-copy debt is fenced by _hardcodedCopyAllowlist '
            'and expires through #908.',
      );
    });
  });
}

final _responseDtoPattern = RegExp(r'\bclass\s+([A-Za-z0-9]+ResponseDto)\b');
final _rawJsonLookupPattern = RegExp(r'''\b(?:payload|json)\s*\[\s*['"]''');

const _openApiCoveredResponsePrefixes = <String>{
  'AuthenticatedUser',
  'Boards',
  'Calendar',
  'Chat',
  'Devops',
  'File',
  'Office',
  'OnboardingStatus',
  'Organization',
  'Platform',
  'ProductProfile',
  'Provider',
  'Workspace',
};

const _legacyResponseDtoAllowlist = <LegacyFence>[
  LegacyFence(
    path:
        'lib/integrations/weave_api/data/dtos/platform_status_response_dto.dart',
    issue: '#904',
    classNames: ['PlatformStatusResponseDto'],
    reason:
        'Diagnostic/admin platform status remains hand-written until provider '
        'stack DTO collapse and diagnostic seams are split from normal member '
        'state.',
  ),
  LegacyFence(
    path:
        'lib/integrations/weave_api/data/dtos/provider_stack_response_dto.dart',
    issue: '#904',
    classNames: [
      'ProviderRegistryResponseDto',
      'ProviderCategoryStatusResponseDto',
      'ProviderAdapterReadinessEvidenceResponseDto',
      'ProviderCategoryContractResponseDto',
      'ProviderChoiceModelResponseDto',
      'ProviderStatusResponseDto',
      'DevopsSummaryResponseDto',
      'LinkedSourceProjectResponseDto',
      'SourceRepositoryResponseDto',
      'DevopsIssueSummaryResponseDto',
      'DevopsMergeRequestSummaryResponseDto',
      'DevopsPipelineSummaryResponseDto',
      'DevopsJobSummaryResponseDto',
      'DevopsReleaseSummaryResponseDto',
      'OfficeCapabilitiesResponseDto',
      'OfficeProviderCandidateResponseDto',
      'OfficeCapabilityFlagsResponseDto',
      'OfficePermissionModelResponseDto',
      'OfficeLockSessionReadinessResponseDto',
      'OfficeLaunchResponseDto',
      'OfficeLaunchErrorResponseDto',
    ],
    reason:
        'The monolithic provider stack DTO family is deferred to the provider '
        'stack collapse slice; normal member reachability remains fenced here.',
  ),
];

const _rawJsonMemberFacadeAllowlist = <LegacyFence>[
  LegacyFence(
    path: 'lib/features/calendar/data/services/calendar_facade_client.dart',
    issue: '#903',
    reason:
        'Calendar still parses raw facade JSON until the Calendar OpenAPI adapter '
        'slice replaces it with generated Calendar*Response models.',
  ),
  LegacyFence(
    path:
        'lib/features/boards/data/repositories/backend_boards_workspace_repository.dart',
    issue: '#903',
    reason:
        'Boards still parses raw facade JSON until the Boards OpenAPI adapter '
        'slice replaces it with generated Boards*Response models.',
  ),
  LegacyFence(
    path: 'lib/features/files/data/repositories/backend_files_repository.dart',
    issue: '#908',
    reason:
        'Files still reads raw error payload message text until member-visible '
        'failure copy is localized through typed codes.',
  ),
  LegacyFence(
    path:
        'lib/integrations/weave_api/data/dtos/platform_status_response_dto.dart',
    issue: '#904',
    reason:
        'Platform status is diagnostic/admin-only until provider reachability is '
        'split from normal member paths.',
  ),
  LegacyFence(
    path:
        'lib/integrations/weave_api/data/dtos/provider_stack_response_dto.dart',
    issue: '#904',
    reason:
        'Provider registry raw parsing is fenced until the provider stack DTO '
        'collapse uses generated OpenAPI models.',
  ),
];

const _memberProviderGraphAllowlist = <LegacyFence>[
  LegacyFence(
    path:
        'lib/features/app/presentation/providers/workspace_connection_provider.dart',
    issue: '#906',
    reason:
        'Workspace connection still carries provider vocabulary until normal '
        'member reachability is reduced to backend capability states.',
  ),
  LegacyFence(
    path: 'lib/features/chat/presentation/providers/chat_provider.dart',
    issue: '#906',
    reason:
        'Chat provider still contains transitional provider invalidation names; '
        'primary repository wiring remains covered by backend facade tests.',
  ),
  LegacyFence(
    path:
        'lib/features/chat/presentation/providers/chat_security_provider.dart',
    issue: '#895',
    reason:
        'Chat security is an explicit diagnostic-only Matrix seam and must not '
        'be reached from normal member settings or primary Chat flows.',
  ),
  LegacyFence(
    path:
        'lib/features/chat/presentation/providers/chat_security_repository_provider.dart',
    issue: '#895',
    reason:
        'Diagnostic-only Matrix repository seam remains until providergraph '
        'reachability is removed from member surfaces.',
  ),
  LegacyFence(
    path:
        'lib/features/chat/presentation/widgets/chat_security_settings_section.dart',
    issue: '#895',
    reason:
        'Diagnostic widget is allowed only while it is not mounted from normal '
        'member Settings or primary Chat routes.',
  ),
  LegacyFence(
    path: 'lib/features/boards/presentation/boards_workspace_screen.dart',
    issue: '#903',
    reason:
        'Boards presentation still maps OpenProject adapter vocabulary until '
        'the Boards OpenAPI adapter exposes provider-neutral domain state.',
  ),
  LegacyFence(
    path:
        'lib/features/server_config/presentation/providers/server_configuration_form_controller.dart',
    issue: '#908',
    reason:
        'Member setup still carries Matrix/Nextcloud legacy fields until the '
        'handoff and setup copy are provider-neutralized.',
  ),
  LegacyFence(
    path:
        'lib/features/server_config/presentation/widgets/server_configuration_form.dart',
    issue: '#908',
    reason:
        'Member setup UI still carries Matrix/Nextcloud legacy fields until the '
        'handoff and setup copy are provider-neutralized.',
  ),
  LegacyFence(
    path: 'lib/features/settings/presentation/settings_screen.dart',
    issue: '#906',
    reason:
        'Settings still renders provider-stack status while diagnostic/admin '
        'reachability is being split from normal member paths.',
  ),
  LegacyFence(
    path: 'lib/features/shell/presentation/shell_workspace_status.dart',
    issue: '#906',
    reason:
        'Shell workspace status still references provider-derived legacy state '
        'until member capability state is fully facade-owned.',
  ),
];

const _hardcodedCopyAllowlist = <LegacyFence>[
  LegacyFence(
    path: 'lib/core/failures/app_failure.dart',
    issue: '#908',
    reason:
        'Failure messages remain developer/support diagnostics until typed '
        'member-facing recovery codes replace displayable strings.',
  ),
  LegacyFence(
    path: 'lib/features/app/domain/entities/workspace_capability_snapshot.dart',
    issue: '#908',
    reason:
        'Capability state fallback text is transitional until localized member '
        'recovery view models own all visible copy.',
  ),
  LegacyFence(
    path: 'lib/features/app/domain/use_cases/',
    issue: '#908',
    reason:
        'App bootstrap/sign-in use cases still emit displayable failure text '
        'until typed recovery codes feed localized UI copy.',
  ),
  LegacyFence(
    path: 'lib/features/app/domain/entities/provider_stack_snapshot.dart',
    issue: '#906',
    reason:
        'Provider stack snapshot fallback text remains fenced until provider '
        'readiness is split into admin diagnostics and member capability state.',
  ),
  LegacyFence(
    path:
        'lib/features/app/presentation/providers/workspace_connection_provider.dart',
    issue: '#908',
    reason:
        'Workspace connection still maps capability fallback copy directly '
        'until localized recovery view models own visible member text.',
  ),
  LegacyFence(
    path:
        'lib/features/boards/data/repositories/backend_boards_workspace_repository.dart',
    issue: '#903',
    reason:
        'Boards data-layer messages are fenced until the Boards OpenAPI adapter '
        'and localized failure mapping land.',
  ),
  LegacyFence(
    path:
        'lib/features/boards/data/repositories/static_boards_workspace_repository.dart',
    issue: '#903',
    reason:
        'Static Boards demo data remains fenced until Boards uses generated '
        'OpenAPI models and localized member copy.',
  ),
  LegacyFence(
    path: 'lib/features/boards/data/services/',
    issue: '#903',
    reason:
        'Boards normalizer/fallback text remains fenced until Boards uses '
        'generated OpenAPI models and localized member copy.',
  ),
  LegacyFence(
    path: 'lib/features/boards/domain/entities/',
    issue: '#903',
    reason:
        'Boards domain fallback labels remain fenced until Boards uses '
        'generated OpenAPI models and localized member copy.',
  ),
  LegacyFence(
    path: 'lib/features/auth/data/',
    issue: '#908',
    reason:
        'Auth persistence/OIDC services still emit displayable failure text '
        'until typed failure codes feed localized sign-in recovery copy.',
  ),
  LegacyFence(
    path: 'lib/features/auth/domain/entities/auth_failure.dart',
    issue: '#908',
    reason:
        'AuthFailure still stores displayable text until typed failure codes '
        'feed localized sign-in recovery copy.',
  ),
  LegacyFence(
    path: 'lib/features/calendar/domain/entities/calendar_event.dart',
    issue: '#903',
    reason:
        'Calendar domain fallback labels are transitional until the Calendar '
        'OpenAPI adapter and localized member-state mapper land.',
  ),
  LegacyFence(
    path: 'lib/features/calendar/data/services/calendar_facade_client.dart',
    issue: '#903',
    reason:
        'Calendar data-layer messages are fenced until the Calendar OpenAPI '
        'adapter returns typed states/codes.',
  ),
  LegacyFence(
    path:
        'lib/features/connectors/presentation/providers/connector_preview_provider.dart',
    issue: '#908',
    reason:
        'Connector preview copy is transitional and must move behind localized '
        'copy or typed readiness states before the hardcoded-copy fence closes.',
  ),
  LegacyFence(
    path: 'lib/features/chat/data/repositories/backend_chat_repository.dart',
    issue: '#908',
    reason:
        'Chat repository failure text is fenced until member-visible recovery '
        'copy is localized from typed failure codes.',
  ),
  LegacyFence(
    path: 'lib/features/chat/data/repositories/matrix_',
    issue: '#906',
    reason:
        'Legacy Matrix repositories are diagnostic/provider seams and remain '
        'fenced until normal member reachability is removed.',
  ),
  LegacyFence(
    path: 'lib/features/chat/data/services/matrix_',
    issue: '#906',
    reason:
        'Legacy Matrix services are diagnostic/provider seams and remain fenced '
        'until normal member reachability is removed.',
  ),
  LegacyFence(
    path: 'lib/features/chat/data/services/archived_message_store.dart',
    issue: '#908',
    reason:
        'Archived-message storage diagnostics remain hardcoded until typed '
        'storage error codes feed localized recovery copy.',
  ),
  LegacyFence(
    path: 'lib/features/chat/domain/entities/',
    issue: '#908',
    reason:
        'Chat domain/demo/evidence labels are transitional hardcoded copy until '
        'member-visible evidence and recovery text is localized.',
  ),
  LegacyFence(
    path: 'lib/features/files/data/repositories/backend_files_repository.dart',
    issue: '#908',
    reason:
        'Files repository failure text is fenced until member-visible recovery '
        'copy is localized from typed failure codes.',
  ),
  LegacyFence(
    path: 'lib/features/files/data/services/',
    issue: '#908',
    reason:
        'File picker/import/export services still emit displayable failure text '
        'until typed file operation codes feed localized UI copy.',
  ),
  LegacyFence(
    path: 'lib/features/files/domain/entities/files_failure.dart',
    issue: '#908',
    reason:
        'FilesFailure still stores displayable text until typed failure codes '
        'feed localized Files recovery copy.',
  ),
  LegacyFence(
    path: 'lib/features/onboarding/data/',
    issue: '#908',
    reason:
        'Onboarding backend clients still emit displayable failure text until '
        'typed first-run recovery codes feed localized UI copy.',
  ),
  LegacyFence(
    path: 'lib/features/onboarding/domain/',
    issue: '#908',
    reason:
        'Onboarding handoff/domain failures still carry displayable text until '
        'typed first-run recovery codes feed localized UI copy.',
  ),
  LegacyFence(
    path: 'lib/features/profile/data/',
    issue: '#908',
    reason:
        'Profile data clients still emit displayable backend failure text until '
        'typed profile recovery codes feed localized UI copy.',
  ),
  LegacyFence(
    path: 'lib/features/settings/presentation/settings_screen.dart',
    issue: '#908',
    reason:
        'Settings still contains interpolated and provider-status copy until '
        'localized recovery/policy view models replace it.',
  ),
  LegacyFence(
    path: 'lib/features/server_config/data/',
    issue: '#908',
    reason:
        'Server configuration persistence/derivation still emits provider and '
        'validation text until member setup is provider-neutral and localized.',
  ),
  LegacyFence(
    path:
        'lib/integrations/weave_api/data/dtos/organization_manifest_response_dto.dart',
    issue: '#908',
    reason:
        'Manifest validation messages are support diagnostics until typed '
        'localized recovery states replace displayable strings.',
  ),
  LegacyFence(
    path:
        'lib/integrations/weave_api/data/dtos/platform_status_response_dto.dart',
    issue: '#904',
    reason:
        'Platform diagnostic validation messages remain fenced with the '
        'diagnostic provider seam.',
  ),
  LegacyFence(
    path:
        'lib/integrations/weave_api/data/dtos/provider_stack_response_dto.dart',
    issue: '#904',
    reason:
        'Provider registry validation messages remain fenced with provider '
        'stack DTO collapse and diagnostic separation.',
  ),
  LegacyFence(
    path:
        'lib/integrations/weave_api/data/dtos/workspace_capabilities_response_dto.dart',
    issue: '#908',
    reason:
        'Workspace capability fallback text is fenced until localized member '
        'recovery view models own visible copy.',
  ),
  LegacyFence(
    path:
        'lib/integrations/weave_api/data/dtos/workspace_home_response_dto.dart',
    issue: '#908',
    reason:
        'Workspace Home validation messages are support diagnostics until typed '
        'localized recovery states replace displayable strings.',
  ),
  LegacyFence(
    path: 'lib/integrations/weave_api/data/services/weave_api_client.dart',
    issue: '#908',
    reason:
        'Backend API transport messages are fenced until UI displays typed, '
        'localized recovery copy instead of raw transport text.',
  ),
  LegacyFence(
    path: 'lib/integrations/weave_api/domain/',
    issue: '#908',
    reason:
        'OpenAPI feature-adapter fallback text remains fenced until typed '
        'member readiness codes feed localized UI copy.',
  ),
  LegacyFence(
    path: 'lib/integrations/nextcloud/',
    issue: '#906',
    reason:
        'Nextcloud remains a legacy provider seam until normal member provider '
        'reachability is removed or restricted to diagnostic/admin paths.',
  ),
];

final _rawProviderReachabilityPatterns = <RegExp>[
  RegExp(r'integrations/nextcloud'),
  RegExp(r'nextcloud[A-Z_a-z]'),
  RegExp(r'Matrix[A-Z_a-z]'),
  RegExp(r'matrix[A-Z_a-z]'),
  RegExp(r'\bCalDAV\b|\bcaldav[A-Z_a-z]|\bcalDav[A-Z_a-z]'),
  RegExp(r'\bOpenProject\b|\bopenproject\b|\bopenProject[A-Z_a-z]'),
  RegExp(r'\bLiveKit\b|\blivekit[A-Z_a-z]|\bliveKit[A-Z_a-z]'),
  RegExp(
    r'import .*integrations/(?:nextcloud|matrix|caldav|openproject|livekit)',
  ),
  RegExp(r'\b[a-zA-Z0-9_]*(?:ProviderService|ProviderRepository)\b'),
  RegExp(
    r'\b(?:watch|read)\([a-zA-Z0-9_]*(?:ProviderService|ProviderRepository)Provider\b',
  ),
  RegExp(r'platformStatus'),
  RegExp(r'providerStack'),
  RegExp(r'/api/providers'),
  RegExp(r'/api/platform'),
];

bool _isOpenApiCoveredResponseDto(String className) {
  final base = className.substring(0, className.length - 'Dto'.length);
  return _openApiCoveredResponsePrefixes.any(base.startsWith);
}

bool _isOpenApiCoveredMemberFacadeFile(String path) {
  final normalized = _normalize(path);
  return normalized.startsWith('lib/features/calendar/data/') ||
      normalized.startsWith('lib/features/boards/data/') ||
      normalized.startsWith('lib/features/chat/data/repositories/backend_') ||
      normalized.startsWith('lib/features/files/data/repositories/backend_') ||
      normalized.startsWith('lib/features/onboarding/data/') ||
      normalized.startsWith('lib/features/profile/data/') ||
      normalized.startsWith('lib/integrations/weave_api/data/dtos/') ||
      normalized.endsWith(
        'lib/integrations/weave_api/data/services/weave_api_client.dart',
      );
}

Iterable<String> _normalMemberProviderGraphFiles() sync* {
  const roots = <String>[
    'lib/features/app/presentation',
    'lib/features/auth/presentation',
    'lib/features/boards/presentation',
    'lib/features/calendar/presentation',
    'lib/features/chat/presentation',
    'lib/features/connectors/presentation',
    'lib/features/files/presentation',
    'lib/features/guests/presentation',
    'lib/features/help/presentation',
    'lib/features/onboarding/presentation',
    'lib/features/profile/presentation',
    'lib/features/server_config/presentation',
    'lib/features/settings/presentation',
    'lib/features/shell/presentation',
    'lib/features/workflows/presentation',
  ];
  for (final root in roots) {
    final directory = Directory(root);
    if (!directory.existsSync()) continue;
    yield* _dartFilesUnder(root);
  }
}

bool _isUserFacingLayer(String path) {
  final normalized = _normalize(path);
  if (normalized.contains('/presentation/')) return false;
  return normalized.contains('/domain/entities/') ||
      normalized.contains('/domain/use_cases/') ||
      normalized.contains('/data/repositories/') ||
      normalized.contains('/data/services/') ||
      normalized.contains('/integrations/weave_api/data/');
}

bool _isLocalizationApprovedPath(String path) {
  final normalized = _normalize(path);
  return normalized.startsWith('lib/l10n/') ||
      normalized.startsWith('lib/generated/');
}

bool _looksLikeUserFacingString(String line) {
  if (!line.contains("'") && !line.contains('"')) return false;
  if (line.contains('l10n.')) return false;
  if (line.contains('AppLocalizations')) return false;
  if (line.trimLeft().startsWith('import ')) return false;
  if (line.trimLeft().startsWith('part ')) return false;
  if (line.contains('Provider<')) return false;
  if (line.contains('ProviderFor(')) return false;
  if (line.contains('Uri.parse(')) return false;

  final literals = _stringLiteralPattern
      .allMatches(line)
      .map((match) => match.group(2) ?? match.group(3) ?? '')
      .where((value) => value.trim().length >= 4);
  for (final literal in literals) {
    if (_isLikelyIdentifierOrRoute(literal)) continue;
    if (_userFacingWords.hasMatch(literal) || literal.contains(' ')) {
      return true;
    }
  }
  return false;
}

final _stringLiteralPattern = RegExp(r'''(r)?'([^']*)'|"(.*?)"''');
final _userFacingWords = RegExp(
  r'\b(the|this|that|your|you|weave|workspace|backend|provider|calendar|boards|chat|files|sign in|unable|invalid|failed|error|ready|disabled|policy|admin|support|diagnostic|secret|token)\b',
  caseSensitive: false,
);

bool _isLikelyIdentifierOrRoute(String literal) {
  if (literal.startsWith('package:')) return true;
  if (literal.startsWith('lib/')) return true;
  if (literal.startsWith('/')) return true;
  if (literal.startsWith('weave://')) return true;
  if (literal.startsWith('http://') || literal.startsWith('https://')) {
    return true;
  }
  if (RegExp(r'^[a-zA-Z0-9_.:-]+$').hasMatch(literal)) return true;
  return false;
}

String _withoutComment(String line) {
  final commentStart = line.indexOf('//');
  if (commentStart == -1) return line;
  return line.substring(0, commentStart);
}

Iterable<String> _dartFilesUnder(String directoryPath) {
  return Directory(directoryPath)
      .listSync(recursive: true)
      .whereType<File>()
      .where((file) => file.path.endsWith('.dart'))
      .map((file) => _normalize(file.path));
}

bool _isGenerated(String path) {
  final normalized = _normalize(path);
  return normalized.contains('/generated/') || normalized.endsWith('.g.dart');
}

bool _isAllowed(String path, List<LegacyFence> allowlist) {
  final normalized = _normalize(path);
  return allowlist.any((fence) {
    if (fence.path.endsWith('/')) {
      return normalized.startsWith(fence.path);
    }
    return fence.path == normalized || normalized.startsWith(fence.path);
  });
}

bool _isResponseDtoAllowed(String path, String className) {
  final normalized = _normalize(path);
  return _legacyResponseDtoAllowlist.any((fence) {
    final pathMatches =
        fence.path == normalized || normalized.startsWith(fence.path);
    if (!pathMatches) return false;
    return fence.classNames != null &&
        fence.classNames!.isNotEmpty &&
        fence.classNames!.contains(className);
  });
}

String _normalize(String path) {
  final normalized = path.replaceAll(r'\', '/');
  return normalized.startsWith('./') ? normalized.substring(2) : normalized;
}

class LegacyFence {
  const LegacyFence({
    required this.path,
    required this.issue,
    required this.reason,
    this.classNames,
  });

  final String path;
  final String issue;
  final String reason;
  final List<String>? classNames;
}
