import 'dart:async';
import 'dart:convert';
import 'dart:ffi' as ffi;
import 'dart:io';
import 'dart:ui' as ui;

import 'package:crypto/crypto.dart';
import 'package:ffi/ffi.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:path_provider/path_provider.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/bootstrap_gate.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/shell/presentation/app_shell.dart';
import 'package:weave/features/chat/presentation/chat_admission_gate.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/core/persistence/secure_store.dart';
import 'package:weave/core/widgets/success_state.dart';
import 'package:weave/features/onboarding/presentation/member_handoff_screen.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart'
    show authSessionStorageKey;
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/chat/data/repositories/matrix_device_identity_repository.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/chat/presentation/chat_screen.dart';
import 'package:weave/features/chat/presentation/providers/chat_provider.dart';
import 'package:weave/features/calendar/presentation/calendar_screen.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/entities/calendar_failure.dart';
import 'package:weave/features/calendar/domain/entities/calendar_write_result.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/features/files/presentation/providers/files_provider.dart';
import 'package:weave/features/files/presentation/files_screen.dart';
import 'package:weave/features/files/presentation/files_locations_screen.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/features/onboarding/presentation/organization_access_screen.dart';
import 'package:weave/features/server_config/presentation/providers/saved_server_configuration_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';
import 'package:weave/main.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart';
import 'package:weave/integrations/rust_matrix_core/presentation/providers/matrix_crypto_session_provider.dart';

import 'system_browser_auth_e2e_test.dart'
    show
        emitNativeProductDiagnostic,
        emitNativeAuthFailureDiagnostic,
        requireActualNativeBearerExpiry,
        requireNativeExpiredBearerDenied;
import 'helpers/test_config.dart';
import 'package:weave/features/auth/presentation/providers/auth_flow_controller.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';

const _simulator = bool.fromEnvironment('WEAVE_NATIVE_EXPECT_SIMULATOR');
const _disposable = bool.fromEnvironment('WEAVE_DEVICE_DISPOSABLE_STACK');
const _actorTestBudget = Duration(minutes: 30);

/// Run identity belongs to the validated private input, allowing one qualified
/// binary to execute independent instances of the same versioned scenario.
String requireNativeDualRuntimeFixture(Map<String, dynamic> fixture) {
  final runId = fixture['runId'];
  final scenario = fixture['memberScenario'];
  if (runId is! String ||
      !RegExp(r'^[A-Za-z0-9][A-Za-z0-9-]{0,39}$').hasMatch(runId) ||
      !['A', 'B'].contains(fixture['role']) ||
      fixture['organizationRole'] != 'member' ||
      scenario is! Map ||
      scenario['schemaVersion'] != 'weave.member-client-scenario-resolved/v1' ||
      scenario['scenarioId'] != 'member-client-baseline' ||
      scenario['scenarioVersion'] != 1 ||
      scenario['runId'] != runId ||
      scenario['scenarioSha256'] is! String ||
      !RegExp(
        r'^[0-9a-f]{64}$',
      ).hasMatch(scenario['scenarioSha256'] as String) ||
      scenario['sharedSpaceRef'] != 'workspace-default' ||
      fixture['sharedSpaceRef'] != scenario['sharedSpaceRef']) {
    throw TestFailure('Owned ordinary-member scenario binding is unavailable.');
  }
  final actors = scenario['actors'];
  final domains = scenario['requiredDomains'];
  final content = scenario['content'];
  if (actors is! List ||
      actors.length != 2 ||
      domains is! List ||
      domains.length != 3 ||
      !domains.toSet().containsAll(['chat', 'files', 'calendar']) ||
      content is! Map) {
    throw TestFailure('Owned member scenario contract is incomplete.');
  }
  for (var index = 0; index < actors.length; index++) {
    final actor = actors[index];
    final permissions = actor is Map ? actor['spacePermissions'] : null;
    if (actor is! Map ||
        actor['slot'] != ['A', 'B'][index] ||
        actor['organizationRole'] != 'member' ||
        permissions is! List ||
        permissions.length != 2 ||
        !permissions.toSet().containsAll(['VIEW', 'EDIT'])) {
      throw TestFailure('Two ordinary editor members are required.');
    }
  }
  for (final field in [
    'conversation',
    'messageA',
    'messageB',
    'event',
    'updatedEvent',
    'initialFileName',
    'replacementFileName',
    'initialContent',
    'replacementContent',
  ]) {
    if (content[field] is! String || (content[field] as String).isEmpty) {
      throw TestFailure('Shared member content is unavailable.');
    }
  }
  for (final entry in {
    'initialFileName': 'initialFileName',
    'replacementFileName': 'replacementFileName',
    'initialContent': 'initialContent',
    'replacementContent': 'replacementContent',
  }.entries) {
    if (fixture[entry.key] != content[entry.value]) {
      throw TestFailure('Native content differs from the shared scenario.');
    }
  }
  return runId;
}

const _nativeProxyKeys = [
  'http_proxy',
  'https_proxy',
  'HTTP_PROXY',
  'HTTPS_PROXY',
  'no_proxy',
  'NO_PROXY',
];

/// iOS exposes these launch settings to native libraries but not Dart's
/// Platform.environment. Read only the existing owned relay settings.
Map<String, String> nativeDualProcessProxyEnvironment() {
  final getenv = ffi.DynamicLibrary.process()
      .lookupFunction<
        ffi.Pointer<Utf8> Function(ffi.Pointer<Utf8>),
        ffi.Pointer<Utf8> Function(ffi.Pointer<Utf8>)
      >('getenv');
  final environment = <String, String>{};
  for (final name in _nativeProxyKeys) {
    final key = name.toNativeUtf8();
    try {
      final value = getenv(key);
      if (value != ffi.nullptr) environment[name] = value.toDartString();
    } finally {
      malloc.free(key);
    }
  }
  return environment;
}

String nativeDualProxyDirective(Map<String, String> environment) {
  final proxy = environment['http_proxy'];
  final match = proxy == null
      ? null
      : RegExp(r'^http://127\.0\.0\.1:([1-9][0-9]{0,4})$').firstMatch(proxy);
  if (match == null ||
      int.parse(match.group(1)!) > 65535 ||
      _nativeProxyKeys.take(4).any((key) => environment[key] != proxy) ||
      environment['no_proxy'] != '' ||
      environment['NO_PROXY'] != '') {
    throw StateError('Owned native HTTP relay configuration is unavailable.');
  }
  return 'PROXY 127.0.0.1:${match.group(1)}';
}

/// Keep the actual HTTP client and TLS context; only select the same owned
/// fault relay already used by the native Matrix client.
class NativeDualHttpOverrides extends HttpOverrides {
  NativeDualHttpOverrides(Map<String, String> environment)
    : _directive = nativeDualProxyDirective(environment);

  final String _directive;

  @override
  HttpClient createHttpClient(SecurityContext? context) {
    return super.createHttpClient(context)..findProxy = (_) => _directive;
  }
}

void _installNativeDualProxy(String runId) {
  if (!(Platform.isIOS && _simulator && _disposable && runId.isNotEmpty)) {
    throw StateError('Owned iOS simulator HTTP relay is required.');
  }
  HttpOverrides.global = NativeDualHttpOverrides(
    nativeDualProcessProxyEnvironment(),
  );
}

/// Snapshot only the owned export directory, without following links.
Future<Set<String>> nativeDualExportSnapshot(Directory documents) async {
  final root = await documents.resolveSymbolicLinks();
  final files = <String>{};
  var examined = 0;
  await for (final entry in Directory(
    root,
  ).list(recursive: true, followLinks: false)) {
    if (++examined > 4096) {
      throw TestFailure(
        'Owned export directory exceeds the observation bound.',
      );
    }
    if (entry is File) {
      files.add(entry.absolute.uri.normalizePath().toFilePath());
    }
  }
  return files;
}

Future<File> createNativeDualExportSentinel(
  Directory documents,
  String runId,
) async {
  if (!RegExp(r'^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$').hasMatch(runId)) {
    throw TestFailure('Owned export sentinel run binding is unavailable.');
  }
  final root = await documents.resolveSymbolicLinks();
  final sentinel = File('$root/weave-dual-preserve-$runId.txt');
  await sentinel.create(exclusive: true);
  await sentinel.writeAsString(
    'Unrelated owned export sentinel $runId\n',
    flush: true,
  );
  return sentinel;
}

Future<void> requireNativeDualExportSentinelUnchanged(
  File sentinel,
  String runId,
) async {
  if (await FileSystemEntity.type(sentinel.path, followLinks: false) !=
          FileSystemEntityType.file ||
      await sentinel.readAsString() !=
          'Unrelated owned export sentinel $runId\n') {
    throw TestFailure('Native export changed an unrelated owned document.');
  }
}

/// Verify the actual selected destination, rather than the picker staging file.
Future<File> requireNativeDualExportDestination(
  Directory documents,
  Set<String> before,
  String? selected,
) async {
  if (selected == null || !File(selected).isAbsolute) {
    throw TestFailure('Native export did not return an absolute destination.');
  }
  final root = await documents.resolveSymbolicLinks();
  final destination = File(selected).absolute.uri.normalizePath().toFilePath();
  if (!destination.startsWith('$root${Platform.pathSeparator}') ||
      before.contains(destination)) {
    throw TestFailure('Native export destination is not a new owned file.');
  }
  var current = destination;
  while (current != root) {
    final type = await FileSystemEntity.type(current, followLinks: false);
    if (type == FileSystemEntityType.link ||
        type == FileSystemEntityType.notFound) {
      throw TestFailure('Native export destination ownership is unavailable.');
    }
    current = File(current).parent.path;
  }
  if (await FileSystemEntity.type(destination, followLinks: false) !=
      FileSystemEntityType.file) {
    throw TestFailure('Native export destination is not a regular file.');
  }
  return File(destination);
}

Map<String, String> nativeDualSessionIdentityDigests(
  Map<String, dynamic> claims, {
  required String expectedSubjectDigest,
  required String expectedIssuer,
  required String expectedNativeOrganizationId,
  String? expectedOrganizationRole,
}) {
  final subject = claims['sub'];
  final issuer = claims['iss'];
  final organizations = claims['organization'];
  final selected = organizations is Map && organizations.length == 1
      ? organizations.values.single
      : null;
  final organization = selected is Map ? selected['id'] : null;
  if (subject is! String ||
      subject.isEmpty ||
      issuer is! String ||
      issuer.isEmpty ||
      organization is! String ||
      organization.isEmpty) {
    throw TestFailure('Actual native session identity is unavailable.');
  }
  final digests = {
    'identityDigest': sha256.convert(utf8.encode(subject)).toString(),
    'identityIssuerDigest': sha256.convert(utf8.encode(issuer)).toString(),
    'nativeOrganizationDigest': sha256
        .convert(utf8.encode(organization))
        .toString(),
  };
  if (digests['identityDigest'] != expectedSubjectDigest) {
    throw TestFailure(
      'Actual native session subject does not match its assigned actor.',
    );
  }
  if (issuer != expectedIssuer) {
    throw TestFailure(
      'Actual native session issuer does not match its owned fixture.',
    );
  }
  if (organization != expectedNativeOrganizationId) {
    throw TestFailure(
      'Actual native session native organization does not match its owned fixture.',
    );
  }
  if (expectedOrganizationRole != null) {
    final access = selected is Map ? selected['resource_access'] : null;
    final app = access is Map ? access['weave-app'] : null;
    final roles = app is Map ? app['roles'] : null;
    final groups = selected is Map ? selected['groups'] : null;
    final productRoles = roles is List
        ? roles
              .whereType<String>()
              .where(const {'owner', 'admin', 'member', 'guest'}.contains)
              .toSet()
        : <String>{};
    if (productRoles.length != 1 ||
        !productRoles.contains(expectedOrganizationRole) ||
        expectedOrganizationRole == 'member' &&
            ((roles is List && roles.contains('agent-runtime.entitled')) ||
                (groups is List && groups.contains('/capabilities/weaver')))) {
      throw TestFailure(
        'Actual native actor is not the prepared ordinary member.',
      );
    }
  }
  return digests;
}

Future<void> writeNativeDualPrivateJson(File destination, Object value) async {
  final staging = File('${destination.path}.tmp');
  if (await FileSystemEntity.isLink(destination.path) ||
      await FileSystemEntity.isLink(staging.path)) {
    throw TestFailure('Unsafe private actor proof path.');
  }
  final stagingType = await FileSystemEntity.type(
    staging.path,
    followLinks: false,
  );
  if (stagingType == FileSystemEntityType.file) {
    await staging.delete();
  } else if (stagingType != FileSystemEntityType.notFound) {
    throw TestFailure('Unsafe private actor proof path.');
  }
  if (await destination.exists()) {
    await destination.copy(staging.path);
  }
  await staging.writeAsString(jsonEncode(value), flush: true);
  await staging.rename(destination.path);
}

Future<Map<String, Object>> _observeRoomPolicy(
  WidgetTester tester,
  ProviderContainer container,
) async {
  final screen = tester.widget<ChatRoomScreen>(find.byType(ChatRoomScreen));
  final session = await container
      .read(matrixCryptoSessionCoordinatorProvider)
      .open(synchronize: false);
  // This reads the actual SDK room state after bidirectional UI messages. It
  // does not create a room or supply an encryption policy to the application.
  final rooms = await const RustMatrixCoreBridge().loadRooms(
    profileKey: session.profileKey,
  );
  final room = rooms.where((room) => room.roomId == screen.conversation.id);
  expect(room, hasLength(1));
  if (room.single.encrypted) {
    throw TestFailure(
      'Required encrypted-room device/recovery proof is incomplete.',
    );
  }
  return {
    'policyRequired': room.single.encrypted,
    'status': 'not-required',
    'plaintextFallback': false,
  };
}

void _stage(String stage) => emitNativeProductDiagnostic(
  'NATIVE_PRODUCT_STAGE phase=$stage',
  simulatorDriver: true,
);

Finder nativeDualHandoffSignIn(String label) => find.descendant(
  of: find.descendant(
    of: find.byType(MemberHandoffScreen),
    matching: find.byType(SuccessState),
  ),
  matching: find.byWidgetPredicate(
    (widget) =>
        widget is AccessibleButton &&
        widget.onPressed != null &&
        widget.semanticLabel == label,
  ),
);

String nativeDualOnboardingDiagnostic({
  required String role,
  required Map<String, int> visibleWidgetCounts,
}) {
  return 'NATIVE_PRODUCT_STAGE phase=onboarding-failed '
      "actor=${role == 'A' || role == 'B' ? role : 'unavailable'} "
      '${['organization', 'handoff', 'ready', 'signIn', 'workspace'].map((name) => '${name}Count=${(visibleWidgetCounts[name] ?? 0).clamp(0, 999)}').join(' ')}';
}

Future<void> safelyReportDualOnboardingFailure({
  required Future<void> Function() report,
  required void Function() unavailable,
}) async {
  try {
    await report();
  } catch (_) {
    try {
      unavailable();
    } catch (_) {}
  }
}

Future<void> _reportOnboardingFailure(
  WidgetTester tester,
  String role,
  String signInLabel,
) async {
  final container = ProviderScope.containerOf(
    tester.element(find.byType(WeaveApp)),
  );
  final failure = container.read(authFlowControllerProvider).failure;
  if (failure != null) {
    emitNativeAuthFailureDiagnostic(failure, simulatorDriver: true);
  }
  emitNativeProductDiagnostic(
    nativeDualOnboardingDiagnostic(
      role: role,
      visibleWidgetCounts: {
        'organization': find.byType(OrganizationAccessScreen).evaluate().length,
        'handoff': find.byType(MemberHandoffScreen).evaluate().length,
        'ready': find
            .descendant(
              of: find.byType(MemberHandoffScreen),
              matching: find.byType(SuccessState),
            )
            .evaluate()
            .length,
        'signIn': nativeDualHandoffSignIn(signInLabel).evaluate().length,
        'workspace': find
            .byKey(const ValueKey('weave.workspace.home'))
            .evaluate()
            .length,
      },
    ),
    simulatorDriver: true,
  );
}

String nativeDualFilesUploadDiagnostic(AsyncValue<FilesViewState>? state) {
  final value = state?.asData?.value;
  return 'NATIVE_PRODUCT_STAGE phase=files-upload-wait-failed '
      'initialized=${state != null} '
      'loading=${state?.isLoading ?? false} '
      'error=${state?.hasError ?? false} '
      'data=${value != null} '
      'uploadPhase=${value?.uploadStatus.phase.name ?? 'unavailable'} '
      'uploadFailure=${value?.uploadStatus.failure?.type.name ?? 'unavailable'} '
      'directoryFailure=${value?.directoryFailure?.type.name ?? 'unavailable'} '
      'busy=${value?.isBusy ?? false}';
}

Future<void> nativeDualFilesUploadProbe(
  ProviderContainer container,
  Future<void> Function() operation, {
  void Function(String)? diagnostic,
}) async {
  void emit(String message) => diagnostic != null
      ? diagnostic(message)
      : emitNativeProductDiagnostic(message, simulatorDriver: true);
  try {
    await operation();
  } catch (_) {
    await safelyReportDualOnboardingFailure(
      report: () async => emit(
        nativeDualFilesUploadDiagnostic(
          container.exists(filesProvider)
              ? container.read(filesProvider)
              : null,
        ),
      ),
      unavailable: () => emit(
        'NATIVE_PRODUCT_STAGE phase=files-upload-wait-failed state=unavailable',
      ),
    );
    rethrow;
  }
}

Future<void> _wait(
  WidgetTester tester,
  bool Function() condition,
  Duration limit,
) async {
  final deadline = DateTime.now().add(limit);
  while (!condition()) {
    if (!DateTime.now().isBefore(deadline)) {
      throw TestFailure(
        'A required dual-client UI condition did not complete.',
      );
    }
    await tester.pump(const Duration(milliseconds: 250));
  }
}

Future<void> _tap(WidgetTester tester, Finder finder) async {
  expect(finder, findsOneWidget);
  // Aligning an already hittable target can move ancestor workspace tabs.
  if (finder.hitTestable().evaluate().length != 1) {
    await tester.ensureVisible(finder);
    await tester.pump();
  }
  expect(
    finder.hitTestable(),
    findsOneWidget,
    reason: 'The actual UI target must be hittable after scroll preparation.',
  );
  await tester.tap(finder);
  await tester.pump(const Duration(milliseconds: 250));
}

Future<void> tapNativeDualChatConversation(WidgetTester tester, String title) =>
    _tap(tester, find.text(title));

Future<Finder> openNativeDualChatCreationDialog(
  WidgetTester tester, {
  required DateTime deadline,
}) async {
  final create = find.byKey(const Key('chat-create-conversation-button'));
  await _wait(tester, () {
    return create.evaluate().length == 1 &&
        tester.widget<IconButton>(create).onPressed != null &&
        create.hitTestable().evaluate().length == 1;
  }, deadline.difference(DateTime.now()));
  if (!DateTime.now().isBefore(deadline)) {
    throw TestFailure(
      'The real Chat action deadline expired before activation.',
    );
  }
  await _tap(tester, create);
  final name = find.byKey(const Key('chat-create-conversation-name-field'));
  await _wait(tester, () {
    return name.evaluate().length == 1 &&
        tester.widget<TextField>(name).enabled != false;
  }, deadline.difference(DateTime.now()));
  if (!DateTime.now().isBefore(deadline)) {
    throw TestFailure('The real Chat dialog missed its action deadline.');
  }
  expect(name, findsOneWidget);
  return name;
}

/// Uses the same current named Space choice as an ordinary member.
Future<void> selectNativeDualChatSpace(
  WidgetTester tester,
  String spaceRef, {
  required DateTime deadline,
}) async {
  final selector = find.byKey(const Key('chat-create-space-selector'));
  await _wait(tester, () {
    if (selector.evaluate().length != 1) return false;
    final dropdown = tester.widget<DropdownButton<String>>(selector);
    return dropdown.onChanged != null && dropdown.items?.isNotEmpty == true;
  }, deadline.difference(DateTime.now()));
  final dropdown = tester.widget<DropdownButton<String>>(selector);
  if (dropdown.items!.where((item) => item.value == spaceRef).length != 1) {
    throw TestFailure('The intended current Chat Space is unavailable.');
  }
  if (!DateTime.now().isBefore(deadline)) {
    throw TestFailure('The Chat Space selection missed its action deadline.');
  }
  if (dropdown.value == spaceRef) return;
  await _tap(tester, selector);
  final choice = find.byElementPredicate((element) {
    final widget = element.widget;
    if (widget is! Semantics ||
        widget.properties.identifier != 'chat-space-option-$spaceRef') {
      return false;
    }
    var inSelector = false;
    element.visitAncestorElements((ancestor) {
      if (ancestor.widget is DropdownButton<String>) {
        inSelector = true;
        return false;
      }
      return true;
    });
    return !inSelector;
  });
  final popupScroll = find.byType(Scrollable).hitTestable().last;
  for (
    var scrolls = 0;
    choice.evaluate().isEmpty &&
        scrolls < dropdown.items!.length &&
        DateTime.now().isBefore(deadline);
    scrolls++
  ) {
    await tester.drag(popupScroll, const Offset(0, -240));
    await tester.pump(const Duration(milliseconds: 100));
  }
  if (!DateTime.now().isBefore(deadline)) {
    throw TestFailure('The Chat Space selection missed its action deadline.');
  }
  await _tap(tester, choice);
  await _wait(tester, () {
    if (selector.evaluate().length != 1) return false;
    final selected = tester.widget<DropdownButton<String>>(selector);
    return selected.onChanged != null && selected.value == spaceRef;
  }, deadline.difference(DateTime.now()));
  if (!DateTime.now().isBefore(deadline)) {
    throw TestFailure('The Chat Space selection missed its action deadline.');
  }
}

({bool home, bool entry}) nativeLocalInspectionSurface(Element? root) {
  var home = false;
  var entry = false;
  void visit(Element element) {
    final widget = element.widget;
    if ((widget is Offstage && widget.offstage) ||
        (widget is TickerMode && !widget.enabled)) {
      return;
    }
    if (widget is OrganizationAccessScreen) entry = true;
    if (widget.key == const ValueKey('weave.workspace.home')) home = true;
    element.visitChildElements(visit);
  }

  if (root != null) visit(root);
  return (home: home, entry: entry);
}

Future<Map<String, String>?> nativeLocalInspectionSessionSnapshot({
  required Future<String?> Function() readSession,
  required Future<String?> Function() readDevice,
  required bool Function() workspaceCurrent,
  required AuthConfiguration configuration,
  required Map<String, dynamic> fixture,
}) async {
  final raw = await readSession();
  if (raw == null || !workspaceCurrent()) return null;
  final session = AuthSessionDto.decode(raw).toSession();
  if (!session.matches(configuration)) return null;
  final parts = session.accessToken.split('.');
  if (parts.length != 3) return null;
  final claims =
      jsonDecode(utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))))
          as Map<String, dynamic>;
  final identity = nativeDualSessionIdentityDigests(
    claims,
    expectedSubjectDigest: fixture['identityDigestExpected'] as String,
    expectedIssuer: fixture['identityIssuer'] as String,
    expectedNativeOrganizationId: fixture['nativeOrganizationId'] as String,
    expectedOrganizationRole: fixture['organizationRole'] as String?,
  );
  final device = await readDevice();
  if (await readSession() != raw || !workspaceCurrent()) return null;
  return {
    ...identity,
    if (device != null && device.isNotEmpty)
      'matrixDeviceDigest': sha256.convert(utf8.encode(device)).toString(),
  };
}

const nativeLocalInspectionEntryDiagnostic =
    'NATIVE_PRODUCT_STAGE phase=local-inspection-entry-ready qualificationPassed=false supportSafe=true';
const nativeLocalInspectionUnavailableDiagnostic =
    'NATIVE_PRODUCT_STAGE phase=local-inspection-unavailable qualificationPassed=false supportSafe=true';

Future<void> nativeLocalInspectionMain() async {
  WidgetsFlutterBinding.ensureInitialized();
  ui.PlatformDispatcher.instance.setSemanticsTreeEnabled(true);
  if (!(Platform.isIOS && _simulator && _disposable)) {
    throw StateError(
      'Local inspection requires its owned iOS simulator session.',
    );
  }
  final support = await getApplicationSupportDirectory();
  final fixture =
      jsonDecode(
            await File(
              '${support.path}/weave-dual-ios-input.json',
            ).readAsString(),
          )
          as Map<String, dynamic>;
  final runId = requireNativeDualRuntimeFixture(fixture);
  // The inspection owner terminates this isolate with its owned app process.
  _installNativeDualProxy(runId);
  final container = ProviderContainer();
  runApp(
    UncontrolledProviderScope(container: container, child: const WeaveApp()),
  );
  var checking = false;
  var ready = false;
  var entryObserved = false;
  var unavailableReported = false;
  final stateFile = File('${support.path}/weave-local-inspection-state.json');
  // Passive local instrumentation never restores, refreshes or clears auth.
  Timer.periodic(const Duration(seconds: 1), (_) async {
    if (checking || !container.exists(appBootstrapProvider)) return;
    checking = true;
    try {
      final surface = nativeLocalInspectionSurface(
        WidgetsBinding.instance.rootElement,
      );
      if (!entryObserved && surface.entry) {
        emitNativeProductDiagnostic(
          nativeLocalInspectionEntryDiagnostic,
          simulatorDriver: true,
        );
        entryObserved = true;
      }
      if (container.read(appBootstrapProvider).asData?.value.phase !=
          BootstrapPhase.ready) {
        if (ready) {
          ready = false;
          await writeNativeDualPrivateJson(stateFile, {
            'mode': 'local-inspection',
            'qualificationPassed': false,
            'role': fixture['role'],
            'runId': runId,
            'workspaceObserved': false,
          });
        }
        return;
      }
      if (!container.exists(savedServerConfigurationProvider) ||
          !container.exists(secureStoreProvider)) {
        return;
      }
      final configuration = container
          .read(savedServerConfigurationProvider)
          .asData
          ?.value;
      if (configuration == null) return;
      final store = container.read(secureStoreProvider);
      bool current() =>
          container.exists(appBootstrapProvider) &&
          container.read(appBootstrapProvider).asData?.value.phase ==
              BootstrapPhase.ready &&
          identical(
            container.read(savedServerConfigurationProvider).asData?.value,
            configuration,
          ) &&
          nativeLocalInspectionSurface(
            WidgetsBinding.instance.rootElement,
          ).home;
      if (ready) return;
      if (!surface.home) return;
      final identity = await nativeLocalInspectionSessionSnapshot(
        readSession: () => store.read(authSessionStorageKey),
        readDevice: () => store.read(matrixDeviceIdentityStorageKey),
        workspaceCurrent: current,
        configuration: AuthConfiguration(
          issuer: configuration.oidcIssuerUrl,
          clientId: configuration.oidcClientRegistration.clientId,
        ),
        fixture: fixture,
      );
      if (identity == null || !current()) return;
      final observedProof = {
        'mode': 'local-inspection',
        'qualificationPassed': false,
        'role': fixture['role'],
        'runId': runId,
        'workspaceObserved': true,
        'observedAt': DateTime.now().toUtc().toIso8601String(),
        ...identity,
      };
      await writeNativeDualPrivateJson(stateFile, observedProof);
      if (!current()) {
        await writeNativeDualPrivateJson(stateFile, {
          'mode': 'local-inspection',
          'qualificationPassed': false,
          'role': fixture['role'],
          'runId': runId,
          'workspaceObserved': false,
        });
        return;
      }
      _stage('workspace-ready');
      ready = true;
      unavailableReported = false;
    } catch (_) {
      if (!unavailableReported) {
        emitNativeProductDiagnostic(
          nativeLocalInspectionUnavailableDiagnostic,
          simulatorDriver: true,
        );
        unavailableReported = true;
      }
    } finally {
      checking = false;
    }
  });
}

// Expiry includes the real bearer lifetime and validator clock skew before
// the required fresh-session product readback. Match the coordinator window.
DateTime nativeDualActionDeadline(String action, DateTime startedAt) =>
    startedAt.add(Duration(seconds: action == 'expiry-refresh' ? 420 : 180));

void main() {
  if (const bool.fromEnvironment('WEAVE_NATIVE_LOCAL_INSPECTION')) {
    unawaited(nativeLocalInspectionMain());
    return;
  }
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  binding.platformDispatcher.semanticsEnabledTestValue = true;
  ui.PlatformDispatcher.instance.setSemanticsTreeEnabled(true);
  binding.shouldPropagateDevicePointerEvents = true;

  testWidgets(
    'independent public onboarding and concurrent collaboration',
    (tester) async {
      expect(Platform.isIOS && _simulator && _disposable, isTrue);
      final support = await getApplicationSupportDirectory();
      final fixtureFile = File('${support.path}/weave-dual-ios-input.json');
      final fixture =
          jsonDecode(await fixtureFile.readAsString()) as Map<String, dynamic>;
      final runId = requireNativeDualRuntimeFixture(fixture);
      final previousHttpOverrides = HttpOverrides.current;
      _installNativeDualProxy(runId);
      addTearDown(() => HttpOverrides.global = previousHttpOverrides);
      final role = fixture['role'] as String;
      final phase = fixture['phase'] ?? 'initial';
      expect(phase, isIn(['initial', 'restore']));
      final restoring = phase == 'restore';
      final resultFile = File('${support.path}/weave-dual-ios-result.json');
      final prior = restoring
          ? jsonDecode(await resultFile.readAsString()) as Map<String, dynamic>
          : null;
      final entry = fixture['entryUri'] as String;
      expect(entry, isNotEmpty);
      // No configuration, repositories, authentication services or native picker
      // overrides: the same product bootstrap handles public host and invite input.
      await tester.pumpWidget(const ProviderScope(child: WeaveApp()));
      if (!restoring) {
        await _wait(
          tester,
          () => find.byType(OrganizationAccessScreen).evaluate().isNotEmpty,
          const Duration(seconds: 90),
        );
        final l10n = AppLocalizations.of(
          tester.element(find.byType(OrganizationAccessScreen)),
        );
        final field = find.descendant(
          of: find.byType(OrganizationAccessScreen),
          matching: find.byType(TextField),
        );
        expect(field, findsOneWidget);
        await tester.enterText(field, entry);
        await _tap(
          tester,
          find.widgetWithText(
            AccessibleButton,
            l10n.setupOrganizationContinueButton,
          ),
        );
        final signIn = nativeDualHandoffSignIn(l10n.signInButton);
        try {
          await _wait(
            tester,
            () => signIn.evaluate().length == 1,
            const Duration(seconds: 90),
          );
          expect(signIn, findsOneWidget);
          final onboardingContainer = ProviderScope.containerOf(
            tester.element(find.byType(WeaveApp)),
          );
          final selected = await onboardingContainer.read(
            savedServerConfigurationProvider.future,
          );
          expect(selected, isNotNull);
          expect(selected!.validatedManifest, isNotNull);
          expect(selected.manifestValidatedAt, isNotNull);
          final expectedOrigin = Uri.parse(
            fixture['organizationOrigin'] as String,
          );
          expect(selected.organizationOrigin, isNotNull);
          expect(selected.organizationOrigin!.path, anyOf('', '/'));
          expect(expectedOrigin.path, anyOf('', '/'));
          expect(
            selected.organizationOrigin!.replace(path: '').toString(),
            expectedOrigin.replace(path: '').toString(),
          );
        } catch (_) {
          await safelyReportDualOnboardingFailure(
            report: () => _reportOnboardingFailure(
              tester,
              role,
              l10n.signInButton,
            ).timeout(const Duration(seconds: 5)),
            unavailable: () => _stage('onboarding-diagnostic-unavailable'),
          );
          rethrow;
        }
        expect(
          find.byKey(const ValueKey('weave.workspace.home')),
          findsNothing,
        );
        _stage('shell-ready');
        final barrier = File(
          '${support.path}/weave-native-driver-$runId.ready',
        );
        await _wait(
          tester,
          () => barrier.existsSync(),
          const Duration(minutes: 2),
        );
        await barrier.delete();
        _stage('driver-attached');
        // XCTest has verified the unique hittable native control before releasing
        // this barrier. Flutter now performs one real product UI action; XCTest
        // exclusively operates the actual system browser and issuer form.
        expect(signIn, findsOneWidget);
        await _tap(tester, signIn);
        _stage('flutter-sign-in-tapped');
        try {
          await _wait(
            tester,
            () => find
                .byKey(const ValueKey('weave.workspace.home'))
                .evaluate()
                .isNotEmpty,
            const Duration(minutes: 5),
          );
        } catch (_) {
          await safelyReportDualOnboardingFailure(
            report: () => _reportOnboardingFailure(
              tester,
              role,
              l10n.signInButton,
            ).timeout(const Duration(seconds: 5)),
            unavailable: () => _stage('onboarding-diagnostic-unavailable'),
          );
          rethrow;
        }
      } else {
        await _wait(
          tester,
          () => find
              .byKey(const ValueKey('weave.workspace.home'))
              .evaluate()
              .isNotEmpty,
          const Duration(seconds: 90),
        );
        expect(find.byType(OrganizationAccessScreen), findsNothing);
        expect(find.byKey(const ValueKey('weave.auth.sign-in')), findsNothing);
        _stage('process-restart-restored');
        _stage('shell-ready');
      }
      _stage('workspace-ready');
      final container = ProviderScope.containerOf(
        tester.element(find.byType(WeaveApp)),
      );
      ProviderContainer filesContainer() => _nativeFilesContainer(tester);
      final configuration = await container.read(
        savedServerConfigurationProvider.future,
      );
      expect(configuration, isNotNull);
      final session = await container
          .read(authSessionRepositoryProvider)
          .restoreSession(
            AuthConfiguration(
              issuer: configuration!.oidcIssuerUrl,
              clientId: configuration.oidcClientRegistration.clientId,
            ),
          );
      expect(session.isAuthenticated, isTrue);
      final token = session.session!.accessToken.split('.');
      expect(token, hasLength(3));
      final claims =
          jsonDecode(
                utf8.decode(base64Url.decode(base64Url.normalize(token[1]))),
              )
              as Map<String, dynamic>;
      final subject = claims['sub'] as String;
      expect(subject, isNotEmpty);
      final sessionIdentity = nativeDualSessionIdentityDigests(
        claims,
        expectedSubjectDigest: fixture['identityDigestExpected'] as String,
        expectedIssuer: fixture['identityIssuer'] as String,
        expectedNativeOrganizationId: fixture['nativeOrganizationId'] as String,
        expectedOrganizationRole: 'member',
      );
      final scenarios = <String, Map<String, dynamic>>{
        if (prior != null)
          for (final entry
              in (prior['scenarios'] as Map<String, dynamic>).entries)
            entry.key: Map<String, dynamic>.from(entry.value as Map),
        'authentication': {'status': 'passed', 'evidence': 'flutter-ui'},
      };
      await expectLater(tester, meetsGuideline(iOSTapTargetGuideline));
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
      final accessibilityScreens = <String>{
        'home',
        if (prior != null &&
            scenarios['accessibility']?['status'] == 'passed') ...[
          'chat',
          'calendar',
          'files',
        ],
      };
      await navigateNativeDualDestination(tester, Icons.chat_bubble_outline);
      final store = container.read(secureStoreProvider);
      String? device;
      await _wait(tester, () {
        // The persisted identity is read below after UI admission; no device is
        // created here merely to manufacture independent-device evidence.
        return find
            .byKey(const Key('chat-create-conversation-button'))
            .evaluate()
            .isNotEmpty;
      }, const Duration(seconds: 90));
      device = await store.read(matrixDeviceIdentityStorageKey);
      expect(device, isNotNull);
      expect(device, isNotEmpty);
      final resources = <String, Object>{
        if (prior != null)
          ...Map<String, Object>.from(prior['resources'] as Map),
      };
      final recovery = <String, dynamic>{
        if (prior != null)
          ...Map<String, dynamic>.from(prior['recovery'] as Map),
        'processRestored': restoring,
      };
      if (prior != null) {
        expect(
          prior['identityDigest'],
          sha256.convert(utf8.encode(subject)).toString(),
        );
        expect(
          prior['matrixDeviceDigest'],
          sha256.convert(utf8.encode(device!)).toString(),
        );
        expect(prior['storageDigest'], fixture['storageDigest']);
        expect(
          recovery['organizationOrigin'],
          configuration.organizationOrigin.toString(),
        );
      }
      final result = {
        'role': role,
        'organizationRole': 'member',
        'memberScenarioSha256':
            (fixture['memberScenario'] as Map)['scenarioSha256'],
        ...sessionIdentity,
        'matrixDeviceDigest': sha256.convert(utf8.encode(device!)).toString(),
        'storageDigest': fixture['storageDigest'],
        'scenarios': scenarios,
        'resources': resources,
        'recovery': recovery,
        if (prior != null) 'encryption': prior['encryption'],
      };
      await writeNativeDualPrivateJson(resultFile, result);
      final commandFile = File('${support.path}/weave-dual-ios-command.json');
      var sequence = restoring ? fixture['lastSequence'] as int : 0;
      String? eventId;
      final memberScenario = fixture['memberScenario'] as Map;
      final content = memberScenario['content'] as Map;
      final conversation = content['conversation'] as String;
      final messageA = content['messageA'] as String;
      final messageB = content['messageB'] as String;
      final eventTitle = content['event'] as String;
      final updatedTitle = content['updatedEvent'] as String;
      final fileName = fixture['initialFileName'] as String;
      final replacementName = fixture['replacementFileName'] as String;
      final initialContent = fixture['initialContent'] as String;
      final replacementContent = fixture['replacementContent'] as String;
      String? fileId = prior?['resources']?['fileId'] as String?;
      String? fileRevision = recovery['fileRevision'] as String?;
      NativeDualFilesContext? fileContext = restoring
          ? NativeDualFilesContext.restore(recovery['filesContext'])
          : null;
      File? exportSentinel;
      while (true) {
        await _wait(
          tester,
          () {
            if (!commandFile.existsSync()) return false;
            final value =
                jsonDecode(commandFile.readAsStringSync())
                    as Map<String, dynamic>;
            return value['sequence'] is int &&
                (value['sequence'] as int) > sequence;
          },
          // Idle actors must survive the peer's legitimate readiness and action
          // work. Coordinator readiness/actions keep their existing deadlines;
          // this wait stays bounded by the unchanged whole-actor test budget.
          _actorTestBudget,
        );
        final command =
            jsonDecode(await commandFile.readAsString())
                as Map<String, dynamic>;
        final action = command['action'] as String;
        final next = command['sequence'] as int;
        expect(next, greaterThan(sequence));
        final actionDeadline = nativeDualActionDeadline(action, DateTime.now());
        switch (action) {
          case 'chat-create-send':
            expect(role, 'A');
            late Finder name;
            try {
              name = await openNativeDualChatCreationDialog(
                tester,
                deadline: actionDeadline,
              );
              await selectNativeDualChatSpace(
                tester,
                fixture['sharedSpaceRef'] as String,
                deadline: actionDeadline,
              );
            } catch (_) {
              await safelyReportDualOnboardingFailure(
                report: () async {
                  final state = container.read(chatProvider);
                  final create = find.byKey(
                    const Key('chat-create-conversation-button'),
                  );
                  final count = create.evaluate().length;
                  _stage(
                    'chat-readiness-failed chatPhase=${state.phase.name} '
                    'failureType=${(state.failure ?? state.staleFailure)?.type.name ?? 'none'} '
                    'createCount=${count.clamp(0, 999)} '
                    'enabled=${count == 1 && tester.widget<IconButton>(create).onPressed != null} '
                    'hittable=${create.hitTestable().evaluate().length == 1} '
                    'dialogCount=${find.byKey(const Key('chat-create-conversation-name-field')).evaluate().length.clamp(0, 999)}',
                  );
                },
                unavailable: () {},
              );
              rethrow;
            }
            await tester.enterText(name, conversation);
            expect(
              tester
                  .widget<SwitchListTile>(
                    find.byKey(const Key('chat-create-direct-switch')),
                  )
                  .value,
              isTrue,
            );
            final participant = find.byKey(
              const Key('chat-create-conversation-participant-field'),
            );
            expect(participant, findsOneWidget);
            await tester.enterText(
              participant,
              fixture['peerDisplayName'] as String,
            );
            final peer = find.widgetWithText(
              CheckboxListTile,
              fixture['peerDisplayName'] as String,
            );
            await _wait(
              tester,
              () => peer.evaluate().length == 1,
              const Duration(seconds: 60),
            );
            await tapNativeDualPeer(tester, peer, deadline: actionDeadline);
            await expectLater(tester, meetsGuideline(iOSTapTargetGuideline));
            await expectLater(
              tester,
              meetsGuideline(labeledTapTargetGuideline),
            );
            await _tap(
              tester,
              find.byKey(const Key('chat-create-conversation-submit-button')),
            );
            await _wait(
              tester,
              () => find.byType(ChatRoomScreen).evaluate().length == 1,
              const Duration(seconds: 90),
            );
            await sendNativeDualChatMessage(
              tester,
              messageA,
              deadline: actionDeadline,
            );
            expect(
              tester
                  .widget<ChatRoomScreen>(find.byType(ChatRoomScreen))
                  .conversation
                  .title,
              conversation,
            );
            resources.addAll({
              'roomId': tester
                  .widget<ChatRoomScreen>(find.byType(ChatRoomScreen))
                  .conversation
                  .id,
              'roomTitle': conversation,
            });
            await writeNativeDualPrivateJson(resultFile, result);
          case 'chat-open-reply':
            expect(role, 'B');
            await navigateNativeDualDestination(tester, Icons.home_outlined);
            await navigateNativeDualDestination(
              tester,
              Icons.chat_bubble_outline,
            );
            await waitNativeDualChatConversation(
              tester,
              container,
              conversation,
              deadline: actionDeadline,
            );
            await tapNativeDualChatConversation(tester, conversation);
            await _wait(
              tester,
              () => find.byType(ChatRoomScreen).evaluate().length == 1,
              const Duration(seconds: 60),
            );
            final direct = tester
                .widget<ChatRoomScreen>(find.byType(ChatRoomScreen))
                .conversation;
            expect(direct.isDirectMessage, isTrue);
            expect(direct.isInvite, isFalse);
            expect(
              find.byKey(const Key('chat-accept-invitation-button')),
              findsNothing,
            );
            await _wait(
              tester,
              () => _message(messageA).evaluate().length == 1,
              const Duration(seconds: 90),
            );
            await sendNativeDualChatMessage(
              tester,
              messageB,
              deadline: actionDeadline,
            );
            _orderedMessages(tester, messageA, messageB);
            result['encryption'] = await _observeRoomPolicy(tester, container);
            resources.addAll({
              'roomId': tester
                  .widget<ChatRoomScreen>(find.byType(ChatRoomScreen))
                  .conversation
                  .id,
              'roomTitle': conversation,
            });
            scenarios['chat'] = {'status': 'passed', 'evidence': 'flutter-ui'};
          case 'chat-observe':
            expect(role, 'A');
            await _wait(
              tester,
              () => _message(messageB).evaluate().length == 1,
              const Duration(seconds: 90),
            );
            _orderedMessages(tester, messageA, messageB);
            result['encryption'] = await _observeRoomPolicy(tester, container);
            resources.addAll({
              'roomId': tester
                  .widget<ChatRoomScreen>(find.byType(ChatRoomScreen))
                  .conversation
                  .id,
              'roomTitle': conversation,
            });
            scenarios['chat'] = {'status': 'passed', 'evidence': 'flutter-ui'};
          case 'calendar-create':
            expect(role, 'A');
            await tapNativeDualBack(tester, deadline: actionDeadline);
            await navigateNativeDualDestination(
              tester,
              Icons.calendar_month_outlined,
            );
            await editNativeDualCalendarEvent(
              tester,
              tooltip: _copy(tester).calendarCreateButton,
              title: eventTitle,
              deadline: actionDeadline,
            );
            eventId = (await _calendarReadback(tester, eventTitle, true))!.id;
          case 'calendar-observe':
            expect(role, 'B');
            await tapNativeDualBack(tester, deadline: actionDeadline);
            await navigateNativeDualDestination(
              tester,
              Icons.calendar_month_outlined,
            );
            eventId = (await _calendarReadback(tester, eventTitle, true))!.id;
          case 'calendar-update':
            expect(role, 'A');
            await editNativeDualCalendarEvent(
              tester,
              tooltip: _copy(tester).calendarEditEventTooltip(eventTitle),
              title: updatedTitle,
              deadline: actionDeadline,
            );
            expect(
              (await _calendarReadback(tester, updatedTitle, true))!.id,
              eventId,
            );
          case 'calendar-observe-updated':
            expect(role, 'B');
            expect(
              (await _calendarReadback(tester, updatedTitle, true))!.id,
              eventId,
            );
            expect(find.text(eventTitle), findsNothing);
          case 'calendar-delete':
            expect(role, 'A');
            await navigateNativeDualDestination(
              tester,
              Icons.calendar_month_outlined,
            );
            final beforeCancellation = (await _calendarReadback(
              tester,
              updatedTitle,
              true,
            ))!;
            expect(beforeCancellation.id, eventId);
            await _tap(
              tester,
              find.byTooltip(
                _copy(tester).calendarDeleteEventTooltip(updatedTitle),
              ),
            );
            await _tap(
              tester,
              find.widgetWithText(
                TextButton,
                _copy(tester).calendarCancelButton,
              ),
            );
            expect(
              (await _calendarReadback(tester, updatedTitle, true))!.etag,
              beforeCancellation.etag,
              reason:
                  'Cancelling deletion must preserve the shared event version.',
            );
            await _tap(
              tester,
              find.byTooltip(
                _copy(tester).calendarDeleteEventTooltip(updatedTitle),
              ),
            );
            await _tap(
              tester,
              find.descendant(
                of: find.byType(AlertDialog),
                matching: find.byType(FilledButton),
              ),
            );
            await _calendarReadback(tester, updatedTitle, false);
            scenarios['calendar'] = {
              'status': 'passed',
              'evidence': 'flutter-ui',
            };
          case 'calendar-observe-deleted':
            expect(role, 'B');
            await navigateNativeDualDestination(
              tester,
              Icons.calendar_month_outlined,
            );
            await _calendarReadback(tester, updatedTitle, false);
            expect(
              container
                  .read(calendarProvider)
                  .requireValue
                  .events
                  .where((event) => event.id == eventId),
              isEmpty,
            );
            scenarios['calendar'] = {
              'status': 'passed',
              'evidence': 'flutter-ui',
            };
          case 'files-upload':
            expect(role, 'A');
            await _selectSharedFiles(
              tester,
              fixture['sharedSpaceRef'] as String,
            );
            await writeNativeDualPrivateJson(
              File('${support.path}/weave-dual-ios-native-request.json'),
              {'sequence': next, 'mode': 'import', 'fileName': fileName},
            );
            await _tap(
              tester,
              find.widgetWithText(
                AccessibleButton,
                _copy(tester).filesUploadButton,
              ),
            );
            await nativeDualFilesUploadProbe(
              filesContainer(),
              () => _wait(
                tester,
                () =>
                    filesContainer()
                        .read(filesProvider)
                        .asData
                        ?.value
                        .uploadStatus
                        .phase ==
                    FilesUploadPhase.completed,
                const Duration(seconds: 90),
              ),
            );
            final uploaded = filesContainer()
                .read(filesProvider)
                .requireValue
                .directoryListing!
                .entries
                .where((entry) => entry.name == fileName)
                .single;
            fileId = uploaded.id;
            fileContext = NativeDualFilesContext.capture(
              filesContainer(),
              fileId,
              spaceRef: fixture['sharedSpaceRef'] as String,
            );
            fileRevision = uploaded.revision;
            expect(fileId, isNotEmpty);
            expect(fileRevision, isNotNull);
            await _filePreview(
              tester,
              fileId,
              initialContent,
              fileContext,
              actionDeadline,
            );
          case 'files-open':
            expect(role, 'B');
            await _selectSharedFiles(
              tester,
              fixture['sharedSpaceRef'] as String,
            );
            await _wait(
              tester,
              () =>
                  filesContainer()
                      .read(filesProvider)
                      .asData
                      ?.value
                      .directoryListing
                      ?.entries
                      .where((entry) => entry.name == fileName)
                      .length ==
                  1,
              const Duration(seconds: 90),
            );
            final shared = filesContainer()
                .read(filesProvider)
                .requireValue
                .directoryListing!
                .entries
                .where((entry) => entry.name == fileName)
                .single;
            fileId = shared.id;
            fileContext = NativeDualFilesContext.capture(
              filesContainer(),
              fileId,
              spaceRef: fixture['sharedSpaceRef'] as String,
            );
            fileRevision = shared.revision;
            await _filePreview(
              tester,
              fileId,
              initialContent,
              fileContext,
              actionDeadline,
            );
          case 'files-replace':
            expect(role, 'A');
            expect(fileId, isNotNull);
            await tapNativeDualFilesAction(
              tester,
              filesContainer(),
              fileContext!,
              NativeDualFilesAction.replace,
              deadline: actionDeadline,
              publishPickerRequest: () => writeNativeDualPrivateJson(
                File('${support.path}/weave-dual-ios-native-request.json'),
                {
                  'sequence': next,
                  'mode': 'import',
                  'fileName': replacementName,
                },
              ),
            );
            await _wait(tester, () {
              final state = filesContainer().read(filesProvider).asData?.value;
              final entry = state?.directoryListing?.entries.where(
                (entry) => entry.id == fileId,
              );
              return state?.entryActionStatus.phase ==
                      FilesEntryActionPhase.replacedContent &&
                  entry?.length == 1 &&
                  entry!.single.revision != fileRevision;
            }, const Duration(seconds: 90));
            final replaced = filesContainer()
                .read(filesProvider)
                .requireValue
                .directoryListing!
                .entries
                .where((entry) => entry.id == fileId)
                .single;
            expect(replaced.name, fileName);
            expect(replaced.revision, isNot(fileRevision));
            fileRevision = replaced.revision;
            await _filePreview(
              tester,
              fileId!,
              replacementContent,
              fileContext,
              actionDeadline,
            );
          case 'files-open-updated':
            expect(role, 'B');
            await navigateNativeDualDestination(tester, Icons.home_outlined);
            await _selectSharedFiles(
              tester,
              fixture['sharedSpaceRef'] as String,
            );
            await _wait(
              tester,
              () =>
                  filesContainer()
                      .read(filesProvider)
                      .asData
                      ?.value
                      .directoryListing
                      ?.entries
                      .where(
                        (entry) =>
                            entry.id == fileId &&
                            entry.revision != fileRevision,
                      )
                      .length ==
                  1,
              const Duration(seconds: 90),
            );
            fileRevision = filesContainer()
                .read(filesProvider)
                .requireValue
                .directoryListing!
                .entries
                .where((entry) => entry.id == fileId)
                .single
                .revision;
            await _filePreview(
              tester,
              fileId!,
              replacementContent,
              fileContext!,
              actionDeadline,
            );
          case 'files-export-cancel':
          case 'files-export':
          case 'files-export-updated':
            expect(fileId, isNotNull);
            final documents = await getApplicationDocumentsDirectory();
            exportSentinel ??= await createNativeDualExportSentinel(
              documents,
              runId,
            );
            await requireNativeDualExportSentinelUnchanged(
              exportSentinel,
              runId,
            );
            final beforeExport = await nativeDualExportSnapshot(documents);
            final cancelling = action == 'files-export-cancel';
            var exportingObserved = false;
            final exportSubscription = filesContainer().listen(filesProvider, (
              _,
              next,
            ) {
              final state = next.asData?.value;
              if (state?.entryActionStatus.phase ==
                      FilesEntryActionPhase.exportingEntry &&
                  state?.isBusy == true) {
                exportingObserved = true;
              }
            });
            try {
              await tapNativeDualFilesAction(
                tester,
                filesContainer(),
                fileContext!,
                NativeDualFilesAction.export,
                deadline: actionDeadline,
                publishPickerRequest: () => writeNativeDualPrivateJson(
                  File('${support.path}/weave-dual-ios-native-request.json'),
                  {
                    'sequence': next,
                    'mode': cancelling ? 'export-cancel' : 'export',
                    'fileName': fileName,
                  },
                ),
              );
              await _wait(
                tester,
                () =>
                    exportingObserved &&
                    filesContainer()
                            .read(filesProvider)
                            .asData
                            ?.value
                            .entryActionStatus
                            .phase ==
                        (cancelling
                            ? FilesEntryActionPhase.idle
                            : FilesEntryActionPhase.exportedEntry) &&
                    filesContainer().read(filesProvider).asData?.value.isBusy ==
                        false,
                const Duration(seconds: 90),
              );
              await requireNativeDualExportSentinelUnchanged(
                exportSentinel,
                runId,
              );
              if (cancelling) {
                final state = filesContainer().read(filesProvider).requireValue;
                expect(state.entryActionStatus.destination, isNull);
                expect(state.directoryFailure, isNull);
                expect(await nativeDualExportSnapshot(documents), beforeExport);
                break;
              }
              final output = await requireNativeDualExportDestination(
                documents,
                beforeExport,
                filesContainer()
                    .read(filesProvider)
                    .requireValue
                    .entryActionStatus
                    .destination,
              );
              expect(
                await output.readAsString(),
                action == 'files-export' ? initialContent : replacementContent,
              );
              if (action == 'files-export-updated') {
                scenarios['files'] = {
                  'status': 'passed',
                  'evidence': 'flutter-ui',
                };
              }
            } finally {
              exportSubscription.close();
            }
          case 'authorization':
            final proofFile = File(
              '${support.path}/weave-dual-ios-denial.json',
            );
            await _wait(
              tester,
              () => proofFile.existsSync(),
              const Duration(seconds: 90),
            );
            final proof =
                jsonDecode(await proofFile.readAsString())
                    as Map<String, dynamic>;
            expect(proof['status'], 'passed');
            expect(proof['evidence'], 'server-denial');
            expect(
              proof['resourceDigest'],
              sha256
                  .convert(utf8.encode(resources['fileId'] as String))
                  .toString(),
            );
            expect(proof['denials'], {
              'chat': 'passed',
              'files': 'passed',
              'calendar': 'passed',
            });
            expect(
              proof['deniedIdentityDigest'],
              isNot(result['identityDigest']),
            );
            scenarios['authorization'] = proof;
          case 'network-ready':
            await openNativeDualChatConversation(
              tester,
              container,
              conversation,
              roomId: resources['roomId'] as String,
              deadline: actionDeadline,
            );
          case 'network-disconnected':
            expect(role, 'A');
            await tester.enterText(
              find.byType(TextField).last,
              'A recoverable $runId',
            );
            await _tap(
              tester,
              find.widgetWithText(
                FilledButton,
                _copy(tester).chatRoomSendButton,
              ),
            );
            await _wait(
              tester,
              () => find.byType(MaterialBanner).evaluate().isNotEmpty,
              const Duration(seconds: 90),
            );
            expect(
              find.descendant(
                of: find.byType(MaterialBanner),
                matching: find.widgetWithText(
                  TextButton,
                  _copy(tester).chatRoomRetrySendAction,
                ),
              ),
              findsOneWidget,
            );
            // This independent transport probe is a real generated User request,
            // not the UI outcome; the native failed-send banner is the oracle.
            await expectLater(
              container.read(filesRepositoryProvider).listDirectory('/'),
              throwsA(anything),
            );
          case 'network-peer-online':
            expect(role, 'B');
            await sendNativeDualChatMessage(
              tester,
              'B remains online $runId',
              deadline: actionDeadline,
            );
          case 'network-resume':
            expect(role, 'A');
            await _tap(
              tester,
              find.descendant(
                of: find.byType(MaterialBanner),
                matching: find.widgetWithText(
                  TextButton,
                  _copy(tester).chatRoomRetrySendAction,
                ),
              ),
            );
            await _wait(
              tester,
              () =>
                  find.byType(MaterialBanner).evaluate().isEmpty &&
                  _message('A recoverable $runId').evaluate().length == 1 &&
                  _message('B remains online $runId').evaluate().length == 1,
              const Duration(seconds: 90),
            );
            recovery['network'] = true;
          case 'network-peer-readback':
            expect(role, 'B');
            await _wait(
              tester,
              () =>
                  _message('A recoverable $runId').evaluate().length == 1 &&
                  _message('B remains online $runId').evaluate().length == 1,
              const Duration(seconds: 90),
            );
            recovery['network'] = true;
          case 'background-peer-send':
            expect(role, 'B');
            await sendNativeDualChatMessage(
              tester,
              'B background catchup $runId',
              deadline: actionDeadline,
            );
            recovery['background'] = true;
          case 'background-catchup':
            expect(role, 'A');
            await _wait(
              tester,
              () =>
                  _message('B background catchup $runId').evaluate().length ==
                  1,
              const Duration(seconds: 90),
            );
            recovery['background'] = true;
          case 'restart-checkpoint':
            expect(restoring, isFalse);
            expect(
              scenarios.keys,
              containsAll(['chat', 'calendar', 'files', 'authorization']),
            );
            recovery['organizationOrigin'] = configuration.organizationOrigin
                .toString();
            recovery['fileRevision'] = fileRevision;
            recovery['filesContext'] = fileContext!.toPrivateJson();
            await store.write(
              'dual_native_expiry_$runId',
              session.session!.accessToken,
            );
            _stage('restart-checkpoint-ready');
          case 'restore-readback':
            expect(restoring, isTrue);
            await openNativeDualChatConversation(
              tester,
              container,
              conversation,
              roomId: resources['roomId'] as String,
              deadline: actionDeadline,
            );
            await readNativeDualChatHistory(
              tester,
              [
                messageA,
                messageB,
                'B remains online $runId',
                'A recoverable $runId',
                'B background catchup $runId',
              ],
              roomId: resources['roomId'] as String,
              deadline: actionDeadline,
            );
            await tapNativeDualBack(tester, deadline: actionDeadline);
            await _selectSharedFiles(
              tester,
              fixture['sharedSpaceRef'] as String,
            );
            final entry = filesContainer()
                .read(filesProvider)
                .requireValue
                .directoryListing!
                .entries
                .where((entry) => entry.name == fileName)
                .single;
            expect(entry.id, fileId);
            expect(entry.revision, fileRevision);
            await _filePreview(
              tester,
              entry.id,
              replacementContent,
              fileContext!,
              actionDeadline,
            );
            recovery['persistentReadback'] = true;
            _stage('process-restart-product-readback');
          case 'grant-revocation-readback':
            expect(role, 'B');
            final proofFile = File(
              '${support.path}/weave-dual-ios-grant-proof.json',
            );
            final grant =
                jsonDecode(await proofFile.readAsString())
                    as Map<String, dynamic>;
            expect(grant['status'], 'passed');
            expect(grant['deniedIdentityDigest'], result['identityDigest']);
            expect(
              grant['resourceDigest'],
              sha256.convert(utf8.encode(fileId!)).toString(),
            );
            expect(grant['denied'], isTrue);
            expect(grant['regrantRestored'], isTrue);
            await _selectSharedFiles(
              tester,
              fixture['sharedSpaceRef'] as String,
            );
            final entry = filesContainer()
                .read(filesProvider)
                .requireValue
                .directoryListing!
                .entries
                .where((entry) => entry.name == fileName)
                .single;
            expect(entry.id, fileId);
            await _filePreview(
              tester,
              entry.id,
              replacementContent,
              fileContext!,
              actionDeadline,
            );
            recovery['grantRegrantReadback'] = true;
          case 'expiry-refresh':
            expect(restoring, isTrue);
            final expired = await store.read('dual_native_expiry_$runId');
            expect(expired, isNotEmpty);
            await requireActualNativeBearerExpiry(expired!);
            final authConfiguration = AuthConfiguration(
              issuer: configuration.oidcIssuerUrl,
              clientId: configuration.oidcClientRegistration.clientId,
            );
            final before = await container
                .read(authSessionRepositoryProvider)
                .restoreSession(authConfiguration);
            final refreshed = await container
                .read(authSessionRepositoryProvider)
                .refreshSession(authConfiguration);
            expect(refreshed.isAuthenticated, isTrue);
            expect(
              refreshed.session!.accessToken,
              isNot(before.session!.accessToken),
            );
            final matrix = await container
                .read(matrixCryptoSessionCoordinatorProvider)
                .open(allowInteractiveSignIn: false);
            final proof = await store.read(
              'matrix_member_device_proof_v1_${matrix.profileKey}',
            );
            expect(proof, isNotEmpty);
            await requireNativeExpiredBearerDenied(
              container,
              TestConfig(
                issuerUrl: configuration.oidcIssuerUrl,
                clientId: configuration.oidcClientRegistration.clientId,
                matrixHomeserverUrl:
                    configuration.serviceEndpoints.matrixHomeserverUrl,
                backendApiBaseUrl:
                    configuration.serviceEndpoints.backendApiBaseUrl,
                offlineContractOnly: false,
              ),
              expired,
              refreshed.session!.accessToken,
              matrix.deviceId,
              proof!,
            );
            await store.delete('dual_native_expiry_$runId');
            // The normal User UI must still read the actual shared file after
            // refresh; successful token exchange alone is not product evidence.
            await _selectSharedFiles(
              tester,
              fixture['sharedSpaceRef'] as String,
            );
            await _filePreview(
              tester,
              fileId!,
              replacementContent,
              fileContext!,
              actionDeadline,
            );
            recovery['expiryRefresh'] = true;
            _stage('expired-bearer-denied');
          case 'logout':
            expect(role, 'A');
            final authConfiguration = AuthConfiguration(
              issuer: configuration.oidcIssuerUrl,
              clientId: configuration.oidcClientRegistration.clientId,
            );
            final before = await container
                .read(authSessionRepositoryProvider)
                .restoreSession(authConfiguration);
            expect(before.isAuthenticated, isTrue);
            final retained = before.session!.accessToken;
            final matrix = await container
                .read(matrixCryptoSessionCoordinatorProvider)
                .open(allowInteractiveSignIn: false);
            final deviceProof = await store.read(
              'matrix_member_device_proof_v1_${matrix.profileKey}',
            );
            expect(deviceProof, isNotEmpty);
            await navigateNativeDualDestination(
              tester,
              Icons.settings_outlined,
            );
            await _tap(
              tester,
              find.widgetWithText(
                AccessibleButton,
                _copy(tester).settingsSignOutButton,
              ),
            );
            await _wait(
              tester,
              () => find
                  .byKey(const ValueKey('weave.auth.sign-in'))
                  .evaluate()
                  .isNotEmpty,
              const Duration(seconds: 60),
            );
            expect(
              (await container
                      .read(authSessionRepositoryProvider)
                      .restoreSession(authConfiguration))
                  .isAuthenticated,
              isFalse,
            );
            await expectLater(
              container.read(filesRepositoryProvider).listDirectory('/'),
              throwsA(anything),
            );
            final denied = await container
                .read(weaveApiHttpClientProvider)
                .get(
                  configuration.serviceEndpoints.matrixHomeserverUrl.resolve(
                    '/_matrix/client/v3/account/whoami',
                  ),
                  headers: {
                    'Authorization': 'Bearer $retained',
                    'x-weave-matrix-device-id': matrix.deviceId,
                    'x-weave-matrix-device-proof': deviceProof!,
                  },
                );
            expect(denied.statusCode, 401);
            expect(jsonDecode(denied.body)['errcode'], 'M_UNKNOWN_TOKEN');
            expect(container.read(authFlowControllerProvider).isBusy, isFalse);
            recovery['logoutIndependent'] = true;
            _stage('logout-denial-passed');
          case 'peer-logout-independent':
            expect(role, 'B');
            expect(
              (await container
                      .read(authSessionRepositoryProvider)
                      .restoreSession(
                        AuthConfiguration(
                          issuer: configuration.oidcIssuerUrl,
                          clientId:
                              configuration.oidcClientRegistration.clientId,
                        ),
                      ))
                  .isAuthenticated,
              isTrue,
            );
            await _selectSharedFiles(
              tester,
              fixture['sharedSpaceRef'] as String,
            );
            await _filePreview(
              tester,
              fileId!,
              replacementContent,
              fileContext!,
              actionDeadline,
            );
            recovery['logoutIndependent'] = true;
          case 'recovery':
            for (final key in [
              'network',
              'background',
              'processRestored',
              'persistentReadback',
              'expiryRefresh',
              'logoutIndependent',
            ]) {
              expect(
                recovery[key],
                isTrue,
                reason: 'Actual independent recovery proof is incomplete.',
              );
            }
            scenarios['recovery'] = {
              'status': 'passed',
              'evidence': 'flutter-ui',
            };
          case 'finish':
            expect(
              accessibilityScreens,
              containsAll(['home', 'chat', 'calendar', 'files']),
            );
            scenarios['accessibility'] = {
              'status': 'passed',
              'evidence': 'flutter-ui',
            };
            expect(
              scenarios.keys.toSet(),
              containsAll([
                'authentication',
                'chat',
                'calendar',
                'files',
                'authorization',
                'recovery',
                'accessibility',
              ]),
            );
          default:
            throw TestFailure('Unsupported private collaboration command.');
        }
        for (final screen in ['chat', 'calendar', 'files']) {
          if (action.startsWith('$screen-')) {
            await expectLater(tester, meetsGuideline(iOSTapTargetGuideline));
            await expectLater(
              tester,
              meetsGuideline(labeledTapTargetGuideline),
            );
            accessibilityScreens.add(screen);
          }
        }
        if (accessibilityScreens.containsAll([
          'home',
          'chat',
          'calendar',
          'files',
        ])) {
          scenarios['accessibility'] = {
            'status': 'passed',
            'evidence': 'flutter-ui',
          };
        }
        sequence = next;
        if (fileId != null) {
          resources.addAll({'fileId': fileId, 'fileName': fileName});
        }
        if (eventId != null) {
          final event = container
              .read(calendarProvider)
              .asData
              ?.value
              .events
              .where((event) => event.id == eventId);
          if (event?.length == 1) {
            resources.addAll({
              'calendarId': event!.single.scope.id,
              'eventId': eventId,
              'eventTitle': event.single.title,
            });
          }
        }
        await writeNativeDualPrivateJson(resultFile, result);
        final ack = File('${support.path}/weave-dual-ios-ack.json');
        await writeNativeDualPrivateJson(ack, {
          'sequence': sequence,
          'action': action,
          'status': 'passed',
        });
        if (action == 'finish' || action == 'restart-checkpoint') break;
      }
    },
    timeout: const Timeout(_actorTestBudget),
  );
}

ProviderContainer _nativeFilesContainer(WidgetTester tester) {
  final screen = find.byType(FilesScreen);
  expect(screen, findsOneWidget);
  return ProviderScope.containerOf(tester.element(screen));
}

Future<void> _selectSharedFiles(WidgetTester tester, String spaceRef) async {
  await navigateNativeDualDestination(tester, Icons.folder_outlined);
  await selectNativeDualFilesScope(tester, spaceRef);
  await _wait(tester, () {
    final state = _nativeFilesContainer(
      tester,
    ).read(filesProvider).asData?.value;
    return state?.rootSpaceRef == spaceRef &&
        state?.directoryListing != null &&
        !state!.isBusy &&
        state.directoryFailure == null;
  }, const Duration(seconds: 90));
}

/// Opens a real folder location through native Back and ordinary folder rows.
/// This helper observes the destination controller; it never changes its scope.
Future<void> selectNativeDualFilesScope(
  WidgetTester tester,
  String? spaceRef,
) async {
  final overview = find.byType(FilesLocationsScreen);
  final screen = find.byType(FilesScreen);
  await _wait(
    tester,
    () => overview.evaluate().length == 1 || screen.evaluate().length == 1,
    const Duration(seconds: 90),
  );
  var backActions = 0;
  while (overview.evaluate().isEmpty) {
    if (backActions++ >= 8 || screen.evaluate().length != 1) {
      throw TestFailure(
        'The Files overview was not reached through navigation.',
      );
    }
    final previous = tester.element(screen);
    await _tap(tester, find.byType(BackButton));
    await _wait(
      tester,
      () =>
          overview.evaluate().length == 1 ||
          (screen.evaluate().length == 1 && tester.element(screen) != previous),
      const Duration(seconds: 90),
    );
  }
  expect(overview, findsOneWidget);
  // An iOS pop can reveal the previous page before its transition finishes.
  // Wait for its actual interactive route rather than tapping through a modal.
  await _wait(tester, () {
    final route = ModalRoute.of(tester.element(overview));
    return route?.isCurrent == true &&
        route?.animation?.isCompleted != false &&
        route?.secondaryAnimation?.isDismissed != false;
  }, const Duration(seconds: 15));
  final owner = ProviderScope.containerOf(tester.element(overview));
  await _wait(tester, () {
    final capability = owner.read(workspaceCapabilitySnapshotProvider);
    final spaces = owner.read(weaveApiMemberSpacesProvider);
    return !capability.isLoading &&
        !capability.hasError &&
        capability.asData?.value.files.isReady == true &&
        !spaces.isLoading &&
        !spaces.hasError &&
        spaces.asData?.value != null &&
        (spaceRef == null ||
            spaces.asData!.value!.visibleSpaceRefs.contains(spaceRef));
  }, const Duration(seconds: 90));
  final choice = find.byWidgetPredicate(
    (widget) =>
        widget is Semantics &&
        widget.properties.identifier ==
            (spaceRef == null
                ? 'files-location-private'
                : 'files-location-space-$spaceRef'),
  );
  if (choice.evaluate().isEmpty) {
    final vertical = find.descendant(
      of: overview,
      matching: find.byWidgetPredicate(
        (widget) =>
            widget is Scrollable && widget.axisDirection == AxisDirection.down,
      ),
    );
    expect(vertical, findsOneWidget);
    await tester.drag(vertical, const Offset(0, 10000));
    await tester.pump(const Duration(milliseconds: 250));
    await tester.scrollUntilVisible(
      choice,
      300,
      maxScrolls: 30,
      scrollable: vertical,
    );
  }
  expect(choice, findsOneWidget);
  expect(tester.widget<Semantics>(choice).properties.enabled, isTrue);
  // Do not realign an already hittable row to the sliver's leading edge.
  // If scrolling is needed, center the row and still require a real hit.
  if (choice.hitTestable().evaluate().length != 1) {
    await Scrollable.ensureVisible(tester.element(choice), alignment: 0.5);
    await tester.pump(const Duration(milliseconds: 250));
  }
  expect(choice.hitTestable(), findsOneWidget);
  await tester.tap(choice.hitTestable());
  await tester.pump(const Duration(milliseconds: 250));
  await _wait(tester, () {
    if (screen.evaluate().length != 1) return false;
    final value = _nativeFilesContainer(tester).read(filesProvider);
    final state = value.asData?.value;
    return !value.isLoading &&
        !value.hasError &&
        state?.rootSpaceRef == spaceRef &&
        state?.isBusy == false &&
        state?.hasSuccessfulCurrentRead == true;
  }, const Duration(seconds: 90));
}

enum NativeDualFilesAction { open, replace, export }

/// Actor-private identity of an already observed file and its original directory.
/// Serialized only inside the existing owner-only restart exchange, never logs.
class NativeDualFilesContext {
  NativeDualFilesContext._(
    this._file,
    this._owner,
    this._scope,
    this._parent,
    this._path,
  );
  final String _file;
  final String _owner;
  final String? _scope;
  final String _parent;
  final String _path;

  factory NativeDualFilesContext.capture(
    ProviderContainer container,
    String file, {
    required String? spaceRef,
  }) {
    final value = container.read(filesProvider);
    final state = value.asData?.value;
    final listing = state?.directoryListing;
    if (value.isLoading ||
        value.hasError ||
        state?.connectionState.isConnected != true ||
        state?.rootSpaceRef != spaceRef ||
        listing?.ownerKey == null ||
        listing?.parentFileId == null ||
        listing?.entries.where((entry) => entry.id == file).length != 1) {
      throw TestFailure('Owned Files context could not be captured.');
    }
    return NativeDualFilesContext._(
      file,
      listing!.ownerKey!,
      spaceRef,
      listing.parentFileId!,
      listing.path,
    );
  }

  factory NativeDualFilesContext.restore(Object? raw) {
    if (raw is! Map ||
        [
          'file',
          'owner',
          'parent',
          'path',
        ].any((key) => raw[key] is! String || (raw[key] as String).isEmpty) ||
        (raw['scope'] != null && raw['scope'] is! String)) {
      throw TestFailure('Owned Files restart context is unavailable.');
    }
    return NativeDualFilesContext._(
      raw['file'] as String,
      raw['owner'] as String,
      raw['scope'] as String?,
      raw['parent'] as String,
      raw['path'] as String,
    );
  }

  Map<String, Object?> toPrivateJson() => {
    'file': _file,
    'owner': _owner,
    'scope': _scope,
    'parent': _parent,
    'path': _path,
  };
}

Never _failNativeFiles(
  String reason,
  ProviderContainer container,
  NativeDualFilesContext expected,
  NativeDualFilesAction action, {
  void Function(String)? emit,
  int controls = 0,
  int scrolls = 0,
}) {
  final value = container.read(filesProvider);
  final state = value.asData?.value;
  final entries = state?.directoryListing?.entries
      .where((entry) => entry.id == expected._file)
      .toList();
  final entry = entries?.length == 1 ? entries!.single : null;
  (emit ?? _stage)(
    'files-action-failed reason=$reason action=${action.name} '
    'loading=${value.isLoading} error=${value.hasError} connected=${state?.connectionState.isConnected == true} '
    'busy=${state?.isBusy ?? false} listing=${state?.directoryListing != null} '
    'directoryError=${state?.directoryFailure != null} matches=${entries?.length.clamp(0, 99) ?? 0} '
    'directory=${entry?.isDirectory ?? false} download=${entry?.allows('download') ?? false} '
    'updateContent=${entry?.allows('updateContent') ?? false} '
    'controls=${controls.clamp(0, 99)} scrolls=${scrolls.clamp(0, 99)}',
  );
  throw TestFailure('Owned Files action failed: $reason.');
}

FilesViewState? _nativeFilesActionState(
  ProviderContainer container,
  NativeDualFilesContext expected,
  NativeDualFilesAction action,
  void Function(String)? emit,
) {
  Never fail(String reason) =>
      _failNativeFiles(reason, container, expected, action, emit: emit);
  final capability = container.read(workspaceCapabilitySnapshotProvider);
  final value = container.read(filesProvider);
  if (capability.isLoading) return null;
  if (capability.hasError) fail('capability-error');
  final files = capability.asData?.value.files;
  if (files?.isReady != true ||
      files?.policyState != WorkspaceCapabilityPolicyState.allowed ||
      files?.memberSpaceAccessUnavailable == true) {
    fail('not-admitted');
  }
  if (value.isLoading) return null;
  if (value.hasError) fail('state-error');
  final state = value.asData?.value;
  if (state == null || !state.connectionState.isConnected) {
    fail('not-connected');
  }
  if (state.rootSpaceRef != expected._scope ||
      state.currentPath != expected._path ||
      state.directoryListing?.parentFileId != expected._parent ||
      state.directoryListing?.ownerKey != expected._owner) {
    fail('context-changed');
  }
  if (expected._scope != null) {
    final spaces = container.read(weaveApiMemberSpacesProvider);
    if (spaces.isLoading) return null;
    if (spaces.hasError ||
        spaces.asData?.value?.visibleSpaceRefs.contains(expected._scope) !=
            true) {
      fail('space-not-admitted');
    }
  }
  if (state.directoryFailure != null ||
      state.entryActionStatus.failure != null) {
    fail('listing-error');
  }
  if (state.isBusy) return null;
  final entries = state.directoryListing!.entries
      .where((entry) => entry.id == expected._file)
      .toList();
  if (entries.length != 1) fail('entry-not-unique');
  if (entries.single.isDirectory) fail('entry-is-directory');
  if (!entries.single.allows(
    action == NativeDualFilesAction.replace ? 'updateContent' : 'download',
  )) {
    fail('action-not-allowed');
  }
  return state;
}

/// Prepares only an already authorized owned control. The native picker request
/// is published after visibility preparation; every precondition is rechecked
/// after that asynchronous publication and immediately before the actual tap.
Future<void> tapNativeDualFilesAction(
  WidgetTester tester,
  ProviderContainer container,
  NativeDualFilesContext expected,
  NativeDualFilesAction action, {
  required DateTime deadline,
  Future<void> Function()? publishPickerRequest,
  DateTime Function()? now,
  void Function(String)? emit,
}) async {
  final clock = now ?? DateTime.now;
  final screen = find.byType(FilesScreen);
  final control = find.descendant(
    of: screen,
    matching: find.byKey(
      Key(
        'files-${switch (action) {
          NativeDualFilesAction.open => 'open',
          NativeDualFilesAction.replace => 'update',
          NativeDualFilesAction.export => 'export',
        }}-${expected._file}',
      ),
    ),
  );
  final initialControlCount = control.evaluate().length;
  var scrolls = 0;
  var renderedFrame = false;
  Never fail(String reason) => _failNativeFiles(
    reason,
    container,
    expected,
    action,
    emit: emit,
    controls: control.evaluate().length,
    scrolls: scrolls,
  );
  void checkDeadline() {
    if (!clock().isBefore(deadline)) fail('deadline');
  }

  void requireControl() {
    if (control.evaluate().length != 1) fail('control-not-unique');
    final widget = tester.widget(control);
    if (widget is! IconButton || widget.onPressed == null) {
      fail('control-disabled');
    }
    if (control.hitTestable().evaluate().length != 1) {
      fail('control-obstructed');
    }
  }

  while (true) {
    checkDeadline();
    if (screen.evaluate().length != 1) fail('screen-not-unique');
    if (find.byType(AlertDialog).evaluate().isNotEmpty) {
      fail('modal-obstructed');
    }
    final state = _nativeFilesActionState(container, expected, action, emit);
    if (state == null) {
      await tester.pump(const Duration(milliseconds: 100));
      continue;
    }
    if (!renderedFrame) {
      renderedFrame = true;
      await tester.pump();
      continue;
    }
    final count = control.evaluate().length;
    if (count > 1) fail('control-not-unique');
    if (count == 1) {
      final widget = tester.widget(control);
      if (widget is! IconButton || widget.onPressed == null) {
        fail('control-disabled');
      }
      if (control.hitTestable().evaluate().length == 1) {
        checkDeadline();
        if (_nativeFilesActionState(container, expected, action, emit) ==
            null) {
          fail('state-not-ready');
        }
        requireControl();
        break;
      }
    }
    if (scrolls >= 8) fail('control-not-found-bounded');
    final vertical = find.descendant(
      of: screen,
      matching: find.byWidgetPredicate(
        (widget) =>
            widget is Scrollable && widget.axisDirection == AxisDirection.down,
      ),
    );
    if (vertical.evaluate().length != 1) fail('vertical-scroll-not-unique');
    final entries = state.directoryListing!.entries;
    final index = entries.indexWhere((entry) => entry.id == expected._file);
    final mountedIndices = [
      for (var i = 0; i < entries.length; i++)
        if (find
            .descendant(
              of: screen,
              matching: find.byKey(ValueKey('files-entry-${entries[i].id}')),
            )
            .evaluate()
            .isNotEmpty)
          i,
    ];
    var upwards = mountedIndices.isNotEmpty && index < mountedIndices.first;
    final viewport = find.descendant(
      of: screen,
      matching: find.byType(CustomScrollView),
    );
    if (viewport.evaluate().length != 1) fail('viewport-not-unique');
    if (count == 1) {
      final target = tester.getRect(control).center;
      final bounds = tester.getRect(viewport);
      if (target.dy >= bounds.top && target.dy <= bounds.bottom) {
        fail('control-obstructed');
      }
      upwards = target.dy < bounds.top;
    }

    await tester.drag(
      vertical,
      Offset(0, tester.getSize(viewport).height * (upwards ? 0.65 : -0.65)),
    );
    scrolls++;
    await tester.pump(const Duration(milliseconds: 100));
  }
  if (publishPickerRequest != null) await publishPickerRequest();
  checkDeadline();
  if (screen.evaluate().length != 1 ||
      find.byType(AlertDialog).evaluate().isNotEmpty) {
    fail('screen-obstructed');
  }
  if (_nativeFilesActionState(container, expected, action, emit) == null) {
    fail('state-not-ready');
  }
  requireControl();
  checkDeadline();
  await tester.tap(control);
  if (initialControlCount == 0) {
    (emit ?? _stage)(
      'files-action-visibility-recovered action=${action.name} '
      'initialControlCount=0 finalControlCount=1 mounted=true hittable=true scrollCount=$scrolls supportSafe=true',
    );
  }
  await tester.pump(const Duration(milliseconds: 250));
}

Future<void> _filePreview(
  WidgetTester tester,
  String fileId,
  String expected,
  NativeDualFilesContext context,
  DateTime deadline,
) async {
  if (fileId != context._file) {
    throw TestFailure('Owned Files preview identity changed.');
  }
  await tapNativeDualFilesAction(
    tester,
    ProviderScope.containerOf(tester.element(find.byType(FilesScreen))),
    context,
    NativeDualFilesAction.open,
    deadline: deadline,
  );
  final content = find.byKey(const Key('files-preview-content'));
  await _wait(
    tester,
    () => content.evaluate().length == 1,
    const Duration(seconds: 90),
  );
  expect(tester.widget<SelectableText>(content).data, expected);
  await expectLater(tester, meetsGuideline(iOSTapTargetGuideline));
  await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
  await _tap(
    tester,
    find.widgetWithText(
      TextButton,
      MaterialLocalizations.of(tester.element(content)).closeButtonLabel,
    ),
  );
}

AppLocalizations _copy(WidgetTester tester) =>
    AppLocalizations.of(tester.element(_nativeDualNavigationRoot().first));

Future<void> tapNativeDualBack(
  WidgetTester tester, {
  required DateTime deadline,
}) async {
  final back = find.byType(BackButton);
  expect(back, findsOneWidget);
  expect(back.hitTestable(), findsOneWidget);
  if (!DateTime.now().isBefore(deadline)) {
    throw TestFailure('The real back navigation action deadline expired.');
  }
  await _tap(tester, back);
  await _wait(
    tester,
    () => find.byType(ChatRoomScreen).evaluate().isEmpty,
    deadline.difference(DateTime.now()),
  );
  if (!DateTime.now().isBefore(deadline)) {
    throw TestFailure('The real back navigation action deadline expired.');
  }
}

Finder _nativeDualNavigationId(String id, {bool skipOffstage = true}) =>
    find.byWidgetPredicate(
      (widget) =>
          widget is Semantics && widget.properties.identifier == 'nav-$id',
      skipOffstage: skipOffstage,
    );

Finder _nativeDualNavigationRoot({bool skipOffstage = true}) {
  final semantic = _nativeDualNavigationId('home', skipOffstage: skipOffstage);
  return semantic.evaluate().isNotEmpty
      ? semantic
      : find.byType(NavigationBar, skipOffstage: skipOffstage);
}

Future<void> navigateNativeDualDestination(WidgetTester tester, IconData icon) {
  final id = switch (icon) {
    Icons.home_outlined => 'home',
    Icons.chat_bubble_outline => 'chat',
    Icons.folder_outlined => 'files',
    Icons.calendar_month_outlined => 'calendar',
    Icons.settings_outlined => 'settings',
    _ => throw TestFailure('Unsupported actual navigation destination.'),
  };
  final semantic = _nativeDualNavigationId(id);
  if (semantic.evaluate().isNotEmpty) {
    expect(
      semantic,
      findsOneWidget,
      reason: 'Actual navigation destination must be unambiguous.',
    );
    return _tap(tester, semantic);
  }
  final copy = _copy(tester);
  final label = switch (id) {
    'home' => copy.navHome,
    'chat' => copy.navChat,
    'files' => copy.navFiles,
    'calendar' => copy.navCalendar,
    _ => copy.navSettings,
  };
  return _tap(
    tester,
    find.descendant(of: find.byType(NavigationBar), matching: find.text(label)),
  );
}

void _orderedMessages(WidgetTester tester, String first, String second) {
  expect(_message(first), findsOneWidget);
  expect(_message(second), findsOneWidget);
  expect(
    tester.getCenter(_message(first)).dy,
    lessThan(tester.getCenter(_message(second)).dy),
  );
}

Future<void> openNativeDualChatConversation(
  WidgetTester tester,
  ProviderContainer container,
  String title, {
  required String roomId,
  required DateTime deadline,
}) async {
  expect(DateTime.now().isBefore(deadline), isTrue);
  final room = find.byType(ChatRoomScreen);
  if (room.evaluate().isEmpty) {
    await navigateNativeDualDestination(tester, Icons.chat_bubble_outline);
    await waitNativeDualChatConversation(
      tester,
      container,
      title,
      deadline: deadline,
    );
    await tapNativeDualChatConversation(tester, title);
  }
  final remaining = deadline.difference(DateTime.now());
  const roomWaitLimit = Duration(seconds: 90);
  await _wait(
    tester,
    () =>
        room.evaluate().length == 1 &&
        find
                .descendant(of: room, matching: find.byType(TextField))
                .evaluate()
                .length ==
            1,
    remaining < roomWaitLimit ? remaining : roomWaitLimit,
  );
  final conversation = tester.widget<ChatRoomScreen>(room).conversation;
  expect(conversation.id, roomId);
  expect(conversation.title, title);
  expect(DateTime.now().isBefore(deadline), isTrue);
}

Future<void> readNativeDualChatHistory(
  WidgetTester tester,
  List<String> chronologicalMessages, {
  required String roomId,
  required DateTime deadline,
}) async {
  expect(chronologicalMessages.length, greaterThanOrEqualTo(2));
  final room = find.byType(ChatRoomScreen);
  final timeline = find.descendant(
    of: room,
    matching: find.byWidgetPredicate(
      (widget) => widget is ListView && widget.reverse,
    ),
  );
  void requireCurrentRoom() {
    expect(DateTime.now().isBefore(deadline), isTrue);
    expect(room, findsOneWidget);
    expect(tester.widget<ChatRoomScreen>(room).conversation.id, roomId);
  }

  requireCurrentRoom();
  await _wait(
    tester,
    () => timeline.evaluate().length == 1,
    deadline.difference(DateTime.now()),
  );
  final scrollable = find.descendant(
    of: timeline,
    matching: find.byType(Scrollable),
  );
  expect(scrollable, findsOneWidget);
  expect(tester.widget<Scrollable>(scrollable).axisDirection, AxisDirection.up);
  for (var index = 0; index < chronologicalMessages.length; index++) {
    final message = find.descendant(
      of: timeline,
      matching: _message(chronologicalMessages[index]),
    );
    // A reversed lazy list starts at the newest messages. Read older history
    // first, then follow its chronological order back toward the newest rows.
    for (
      var scrolls = 0;
      message.evaluate().isEmpty && scrolls < 20;
      scrolls++
    ) {
      requireCurrentRoom();
      await tester.drag(scrollable, Offset(0, index == 0 ? 250 : -250));
      await tester.pump(const Duration(milliseconds: 100));
    }
    requireCurrentRoom();
    expect(message, findsOneWidget);
    await tester.ensureVisible(message);
    await tester.pump(const Duration(milliseconds: 100));
    requireCurrentRoom();
    expect(message.hitTestable(), findsOneWidget);
    if (index == 1) {
      // Both original messages must be visible in the same viewport when their
      // rendered order is checked, before scrolling through newer recovery rows.
      expect(_message(chronologicalMessages[0]).hitTestable(), findsOneWidget);
      _orderedMessages(
        tester,
        chronologicalMessages[0],
        chronologicalMessages[1],
      );
    }
  }
}

Future<void> tapNativeDualPeer(
  WidgetTester tester,
  Finder peer, {
  required DateTime deadline,
}) async {
  expect(peer, findsOneWidget);
  expect(tester.widget<CheckboxListTile>(peer).onChanged, isNotNull);
  expect(DateTime.now().isBefore(deadline), isTrue);
  await _tap(tester, peer);
  // The selected-member chip can move the result below the dialog viewport.
  // Reveal the checked result without tapping it again and toggling it off.
  if (peer.hitTestable().evaluate().length != 1) {
    await tester.ensureVisible(peer);
    await tester.pump();
  }
  await requireNativeDualPeerSelected(tester, peer, deadline: deadline);
}

Future<void> requireNativeDualPeerSelected(
  WidgetTester tester,
  Finder peer, {
  required DateTime deadline,
}) async {
  try {
    expect(peer, findsOneWidget);
    final checkbox = tester.widget<CheckboxListTile>(peer);
    expect(checkbox.onChanged, isNotNull);
    expect(peer.hitTestable(), findsOneWidget);
    expect(checkbox.value, isTrue);
    expect(DateTime.now().isBefore(deadline), isTrue);
  } catch (_) {
    await safelyReportDualOnboardingFailure(
      report: () async {
        final count = peer.evaluate().length;
        final checkbox = count == 1
            ? tester.widget<CheckboxListTile>(peer)
            : null;
        _stage(
          'chat-peer-selection-failed peerCount=${count.clamp(0, 999)} '
          'enabled=${checkbox?.onChanged != null} '
          'hittable=${peer.hitTestable().evaluate().length == 1} '
          'checked=${checkbox?.value == true}',
        );
      },
      unavailable: () {},
    );
    rethrow;
  }
}

Future<void> sendNativeDualChatMessage(
  WidgetTester tester,
  String message, {
  required DateTime deadline,
}) async {
  final field = find.descendant(
    of: find.byType(ChatRoomScreen),
    matching: find.byType(TextField),
  );
  final send = find.widgetWithText(
    FilledButton,
    AppLocalizations.of(
      tester.element(find.byType(ChatRoomScreen)),
    ).chatRoomSendButton,
  );
  Duration remaining({Duration? maximum}) {
    final available = deadline.difference(DateTime.now());
    return maximum != null && maximum < available ? maximum : available;
  }

  bool controlsReady() =>
      field.evaluate().length == 1 &&
      tester.widget<TextField>(field).enabled != false &&
      field.hitTestable().evaluate().length == 1 &&
      send.evaluate().length == 1 &&
      tester.widget<FilledButton>(send).onPressed != null &&
      send.hitTestable().evaluate().length == 1;
  try {
    await _wait(tester, controlsReady, remaining());
    if (!DateTime.now().isBefore(deadline)) {
      throw TestFailure('The real Chat send action deadline expired.');
    }
    // A native lifecycle handoff can close the IME while the test binding still
    // remembers this editor. A real tap reacquires its current input connection.
    await _tap(tester, field);
    await tester.enterText(field, message);
    bool inputMatches() =>
        field.evaluate().length == 1 &&
        tester.widget<TextField>(field).controller?.text == message;
    _stage('chat-send-input matchesExpected=${inputMatches()}');
    expect(
      inputMatches(),
      isTrue,
      reason: 'Native Chat keyboard input did not reach the current composer.',
    );
    await _wait(tester, controlsReady, remaining());
    expect(
      inputMatches(),
      isTrue,
      reason: 'Native Chat composer changed before the Send interaction.',
    );
    if (!DateTime.now().isBefore(deadline)) {
      throw TestFailure('The real Chat send action deadline expired.');
    }
    await _tap(tester, send);
    await _wait(
      tester,
      () =>
          _message(message).evaluate().length == 1 &&
          (tester.widget<TextField>(field).controller?.text.isEmpty ?? false),
      remaining(maximum: const Duration(seconds: 60)),
    );
    if (!DateTime.now().isBefore(deadline)) {
      throw TestFailure('The real Chat send proof missed its action deadline.');
    }
  } catch (_) {
    await safelyReportDualOnboardingFailure(
      report: () async {
        final fields = field.evaluate().length;
        final buttons = send.evaluate().length;
        _stage(
          'chat-send-failed composerCount=${fields.clamp(0, 999)} '
          'composerEnabled=${fields == 1 && tester.widget<TextField>(field).enabled != false} '
          'composerHittable=${field.hitTestable().evaluate().length == 1} '
          'composerEmpty=${fields == 1 && (tester.widget<TextField>(field).controller?.text.isEmpty ?? false)} '
          'sendCount=${buttons.clamp(0, 999)} '
          'sendEnabled=${buttons == 1 && tester.widget<FilledButton>(send).onPressed != null} '
          'sendHittable=${send.hitTestable().evaluate().length == 1} '
          'messageCount=${_message(message).evaluate().length.clamp(0, 999)}',
        );
        final app = find.byType(WeaveApp, skipOffstage: false);
        if (app.evaluate().length == 1) {
          final container = ProviderScope.containerOf(tester.element(app));
          emitNativeProductDiagnostic(
            nativeDualChatSurfaceDiagnostic(
              tester,
              container.read(appBootstrapProvider),
              container.read(workspaceCapabilitySnapshotProvider),
            ),
            simulatorDriver: true,
          );
          await safelyReportDualOnboardingFailure(
            report: () async => emitNativeProductDiagnostic(
              await nativeDualChatReceiveDiagnostic(
                store: container.read(secureStoreProvider),
              ).timeout(const Duration(seconds: 5)),
              simulatorDriver: true,
            ),
            unavailable: () => _stage('chat-cached-receive-unavailable'),
          );
        }
      },
      unavailable: () {},
    );
    rethrow;
  }
}

Future<String> nativeDualChatReceiveDiagnostic({
  required SecureStore store,
  RustMatrixCoreBridge bridge = const RustMatrixCoreBridge(),
}) async {
  final bindingKey = await store.read(matrixOAuthCurrentBindingKey);
  if (bindingKey == null ||
      !RegExp(
        '^$matrixOAuthBindingKeyPrefix[0-9a-f]{64}\$',
      ).hasMatch(bindingKey)) {
    throw StateError('Current native Chat binding is unavailable.');
  }
  final rawBinding = await store.read(bindingKey);
  final binding = jsonDecode(rawBinding ?? '') as Map<String, dynamic>;
  final profile = binding['profileKey'];
  if (profile is! String || !RegExp(r'^[0-9a-f]{64}$').hasMatch(profile)) {
    throw StateError('Current native Chat profile is unavailable.');
  }
  // Read already-recorded native counters; do not restore auth, synchronize,
  // reread HTTP history, or become another receive/cursor owner on failure.
  final state = await bridge.loadReceiveDiagnostics(profileKey: profile);
  if (await store.read(matrixOAuthCurrentBindingKey) != bindingKey ||
      await store.read(bindingKey) != rawBinding) {
    throw StateError('Native Chat binding changed during observation.');
  }
  int bounded(int value) => value.clamp(0, 999);
  return 'NATIVE_PRODUCT_STAGE phase=chat-cached-receive '
      'events=${bounded(state.eventCount)} '
      'decrypted=${bounded(state.decryptedCount)} '
      'unableToDecrypt=${bounded(state.unableToDecryptCount)} '
      'plaintext=${bounded(state.plaintextCount)}';
}

String nativeDualChatSurfaceDiagnostic(
  WidgetTester tester,
  AsyncValue<BootstrapState>? bootstrap,
  AsyncValue<WorkspaceCapabilitySnapshot>? capabilities,
) {
  int count(Finder finder) => finder.evaluate().length.clamp(0, 999);
  final room = find.byType(ChatRoomScreen);
  final copy = room.evaluate().length == 1
      ? AppLocalizations.of(tester.element(room))
      : null;
  int withinRoom(Finder finder) =>
      copy == null ? 0 : count(find.descendant(of: room, matching: finder));
  final tabs = find.descendant(of: room, matching: find.byType(TabBar));
  final tabIndex = tabs.evaluate().length == 1
      ? DefaultTabController.maybeOf(tester.element(tabs))?.index
      : null;
  final error = bootstrap?.error;
  final errorClass = switch (error) {
    AppFailure() => 'AppFailure',
    StateError() => 'StateError',
    FormatException() => 'FormatException',
    null => 'none',
    _ => 'unavailable',
  };
  return 'NATIVE_PRODUCT_STAGE phase=chat-send-surface '
      'roomVisible=${count(find.byType(ChatRoomScreen))} '
      'roomMounted=${count(find.byType(ChatRoomScreen, skipOffstage: false))} '
      'invitedRoomVisible=${count(find.byWidgetPredicate((widget) => widget is ChatRoomScreen && widget.conversation.isInvite))} '
      'nonInvitedRoomVisible=${count(find.byWidgetPredicate((widget) => widget is ChatRoomScreen && !widget.conversation.isInvite))} '
      'sessionRequiredCount=${copy == null ? 0 : withinRoom(find.text(copy.chatErrorSessionRequiredGuidance))} '
      'invitationCount=${withinRoom(find.byKey(const Key('chat-accept-invitation-button')))} '
      'archiveReturnCount=${copy == null ? 0 : withinRoom(find.widgetWithText(OutlinedButton, copy.chatRoomActiveTimelineAction))} '
      'workspaceTabCount=${count(tabs)} workspaceTabIndex=${tabIndex?.clamp(0, 999) ?? 'unavailable'} '
      'shellVisible=${count(find.byType(AppShell))} '
      'shellMounted=${count(find.byType(AppShell, skipOffstage: false))} '
      'navigationVisible=${count(_nativeDualNavigationRoot())} '
      'navigationMounted=${count(_nativeDualNavigationRoot(skipOffstage: false))} '
      'gateVisible=${count(find.byType(BootstrapGate))} '
      'chatAdmissionVisible=${count(find.byType(ChatAdmissionGate))} '
      'chatAdmissionMounted=${count(find.byType(ChatAdmissionGate, skipOffstage: false))} '
      'homeVisible=${count(find.byKey(const ValueKey('weave.workspace.home')))} '
      'bootstrapLoading=${bootstrap?.isLoading ?? false} '
      'bootstrapError=${bootstrap?.hasError ?? false} '
      'bootstrapPhase=${bootstrap?.value?.phase.name ?? 'unavailable'} '
      'bootstrapFailure=${bootstrap?.value?.failure?.type.name ?? 'none'} '
      'bootstrapErrorClass=$errorClass '
      'capabilitiesLoading=${capabilities?.isLoading ?? false} '
      'capabilitiesError=${capabilities?.hasError ?? false} '
      'chatReadiness=${capabilities?.asData?.value.chat.readiness.name ?? 'unavailable'} '
      'chatReady=${capabilities?.asData?.value.chat.isReady == true} '
      'bootstrapInitialized=${bootstrap != null} capabilitiesInitialized=${capabilities != null}';
}

Future<void> waitNativeDualChatConversation(
  WidgetTester tester,
  ProviderContainer container,
  String expectedTitle, {
  required DateTime deadline,
}) async {
  final lookupLimit = DateTime.now().add(const Duration(seconds: 90));
  final lookupDeadline = deadline.isBefore(lookupLimit)
      ? deadline
      : lookupLimit;
  var refreshTapped = false;
  try {
    while (find.text(expectedTitle).evaluate().length != 1) {
      if (!DateTime.now().isBefore(lookupDeadline)) {
        throw TestFailure(
          'A required dual-client UI condition did not complete.',
        );
      }
      final screen = find.byType(ChatScreen);
      if (!refreshTapped &&
          find.text(expectedTitle).evaluate().isEmpty &&
          screen.evaluate().length == 1) {
        final copy = AppLocalizations.of(tester.element(screen));
        final refresh = find.ancestor(
          of: find.descendant(
            of: screen,
            matching: find.byWidgetPredicate(
              (widget) =>
                  widget is Text &&
                  (widget.data == copy.chatStaleRoomsRetryButton ||
                      widget.data == copy.retryButton),
            ),
          ),
          matching: find.byWidgetPredicate(
            (widget) => widget is ButtonStyleButton,
          ),
        );
        if (refresh.evaluate().length == 1 &&
            tester.widget<ButtonStyleButton>(refresh).onPressed != null &&
            refresh.hitTestable().evaluate().length == 1 &&
            DateTime.now().isBefore(lookupDeadline)) {
          refreshTapped = true;
          await _tap(tester, refresh);
        }
      }
      await tester.pump(const Duration(milliseconds: 250));
    }
    if (!DateTime.now().isBefore(lookupDeadline)) {
      throw TestFailure(
        'The real Chat room lookup missed its action deadline.',
      );
    }
  } catch (_) {
    await safelyReportDualOnboardingFailure(
      report: () async {
        emitNativeProductDiagnostic(
          nativeDualChatListDiagnostic(
            tester,
            container.exists(chatProvider)
                ? container.read(chatProvider)
                : null,
            container.exists(appBootstrapProvider)
                ? container.read(appBootstrapProvider)
                : null,
            container.exists(workspaceCapabilitySnapshotProvider)
                ? container.read(workspaceCapabilitySnapshotProvider)
                : null,
            expectedTitle,
            refreshTapped: refreshTapped,
          ),
          simulatorDriver: true,
        );
      },
      unavailable: () {},
    );
    rethrow;
  }
}

String nativeDualChatListDiagnostic(
  WidgetTester tester,
  ChatUiState? state,
  AsyncValue<BootstrapState>? bootstrap,
  AsyncValue<WorkspaceCapabilitySnapshot>? capabilities,
  String expectedTitle, {
  bool refreshTapped = false,
}) {
  int count(Finder finder) => finder.evaluate().length.clamp(0, 999);
  final navigation = find.byType(NavigationBar);
  final selectedSemantic = <int>[
    for (var i = 0; i < 5; i++)
      if (_nativeDualNavigationId(
                const ['home', 'chat', 'files', 'calendar', 'settings'][i],
              ).evaluate().length ==
              1 &&
          tester
                  .widget<Semantics>(
                    _nativeDualNavigationId(
                      const [
                        'home',
                        'chat',
                        'files',
                        'calendar',
                        'settings',
                      ][i],
                    ),
                  )
                  .properties
                  .selected ==
              true)
        i,
  ];
  final selected = selectedSemantic.length == 1
      ? selectedSemantic.single
      : navigation.evaluate().length == 1
      ? tester.widget<NavigationBar>(navigation).selectedIndex.clamp(0, 999)
      : null;
  final surface = nativeDualChatSurfaceDiagnostic(
    tester,
    bootstrap,
    capabilities,
  ).replaceFirst('phase=chat-send-surface', 'phase=chat-list-failed');
  return '$surface '
      'listVisible=${count(find.byType(ChatScreen))} '
      'listMounted=${count(find.byType(ChatScreen, skipOffstage: false))} '
      'listPhase=${state?.phase.name ?? 'unavailable'} '
      'listFailure=${state?.failure?.type.name ?? 'none'} '
      'staleFailure=${state?.staleFailure?.type.name ?? 'none'} '
      'listRefreshing=${state?.isRefreshing ?? false} refreshTapped=$refreshTapped '
      'conversationCount=${state?.conversations.length.clamp(0, 999) ?? 0} '
      'invitedCount=${state?.conversations.where((room) => room.isInvite).length.clamp(0, 999) ?? 0} '
      'matchingCount=${state?.conversations.where((room) => room.title == expectedTitle).length.clamp(0, 999) ?? 0} '
      'visibleMatchingCount=${count(find.text(expectedTitle))} '
      'mountedMatchingCount=${count(find.text(expectedTitle, skipOffstage: false))} '
      'navigationSelected=${selected ?? 'unavailable'} listInitialized=${state != null}';
}

Future<void> editNativeDualCalendarEvent(
  WidgetTester tester, {
  required String tooltip,
  required String title,
  required DateTime deadline,
  void Function(String)? emit,
  DateTime Function()? now,
}) async {
  final clock = now ?? DateTime.now;
  final control = find.byWidgetPredicate(
    (widget) => widget is IconButton && widget.tooltip == tooltip,
  );
  Duration remaining() => deadline.difference(clock());
  await _wait(
    tester,
    () =>
        control.evaluate().length == 1 &&
        tester.widget<IconButton>(control).onPressed != null &&
        control.hitTestable().evaluate().length == 1,
    remaining(),
  );
  if (!clock().isBefore(deadline)) {
    throw TestFailure('The real Calendar editor action deadline expired.');
  }
  final copy = AppLocalizations.of(tester.element(control));
  final container = ProviderScope.containerOf(tester.element(control));
  final expected = await _awaitNativeCalendarContext(
    tester,
    container,
    deadline: deadline,
    now: clock,
    emit: emit,
  );
  await _tap(tester, control);
  final field = find.descendant(
    of: find.byType(AlertDialog),
    matching: find.byWidgetPredicate(
      (widget) =>
          widget is TextField &&
          widget.decoration?.labelText == copy.calendarTitleFieldLabel,
    ),
  );
  await _wait(
    tester,
    () =>
        field.evaluate().length == 1 &&
        tester.widget<TextField>(field).enabled != false,
    remaining(),
  );
  if (!clock().isBefore(deadline)) {
    throw TestFailure('The real Calendar editor action deadline expired.');
  }
  expect(field, findsOneWidget);
  await tester.enterText(field, title);
  if (!clock().isBefore(deadline)) {
    throw TestFailure('The real Calendar editor action deadline expired.');
  }
  await expectLater(tester, meetsGuideline(iOSTapTargetGuideline));
  await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
  await _tap(
    tester,
    find.widgetWithText(FilledButton, copy.calendarSaveButton),
  );
  await _waitNativeCalendarEditorClosed(
    tester,
    container: container,
    expected: expected,
    deadline: deadline,
    emit: emit,
    now: now,
  );
}

Finder _message(String value) =>
    find.byWidgetPredicate((widget) => widget is Text && widget.data == value);

Future<Finder> waitNativeDualCalendarRefresh(
  WidgetTester tester, {
  required Finder screen,
  required ProviderContainer container,
  required DateTime deadline,
  DateTime Function()? now,
  void Function(String)? emit,
}) async {
  final clock = now ?? DateTime.now;
  final refresh = find.descendant(
    of: screen,
    matching: find.byType(RefreshIndicator),
  );
  var admittedFramePumped = false;
  while (true) {
    final screenCount = screen.evaluate().length;
    final refreshCount = refresh.evaluate().length;
    final capability = container.read(workspaceCapabilitySnapshotProvider);
    final calendar = capability.asData?.value.calendar;
    Never fail(String reason) {
      final state = capability.hasError
          ? 'error'
          : capability is AsyncLoading<WorkspaceCapabilitySnapshot>
          ? 'loading'
          : 'data';
      (emit ?? _stage)(
        'calendar-readiness-failed reason=$reason '
        'screenCount=${screenCount.clamp(0, 99)} '
        'refreshCount=${refreshCount.clamp(0, 99)} '
        'capabilityState=$state '
        'readiness=${calendar?.readiness.name ?? 'unavailable'} '
        'policy=${calendar?.policyState.name ?? 'unavailable'} '
        'spaceAccessUnavailable=${calendar?.memberSpaceAccessUnavailable ?? false}',
      );
      throw TestFailure('Shared Calendar UI readiness failed: $reason.');
    }

    if (screenCount != 1) fail('calendar-screen-not-unique');
    if (refreshCount > 1) fail('calendar-refresh-not-unique');
    if (capability.hasError) fail('capability-error');
    if (!clock().isBefore(deadline)) fail('readiness-deadline');
    if (capability is AsyncLoading<WorkspaceCapabilitySnapshot>) {
      await tester.pump(const Duration(milliseconds: 250));
      continue;
    }
    if (calendar?.isReady != true ||
        calendar?.policyState != WorkspaceCapabilityPolicyState.allowed ||
        calendar?.memberSpaceAccessUnavailable == true) {
      fail('calendar-not-admitted');
    }
    if (refreshCount == 0 && !admittedFramePumped) {
      // A completed capability future can precede its scheduled widget rebuild.
      admittedFramePumped = true;
      await tester.pump();
      continue;
    }
    if (refreshCount != 1) fail('calendar-refresh-missing');
    return refresh;
  }
}

class _NativeCalendarContext {
  _NativeCalendarContext(this.owner, this.scope, this.selection, this.date);
  final String owner;
  final String scope;
  final String selection;
  final DateTime date;

  factory _NativeCalendarContext.capture(ProviderContainer container) {
    final state = container.read(calendarWorkspaceContextProvider);
    final workspace = state.asData?.value;
    if (state.isLoading || state.hasError || workspace?.selectedScope == null) {
      _failNativeCalendar('context-unavailable', container);
    }
    return _NativeCalendarContext(
      workspace!.ownerKey,
      workspace.selectedScope!.id,
      container.read(selectedCalendarScopeProvider).id,
      container.read(calendarViewDateProvider),
    );
  }

  void check(ProviderContainer container, void Function(String)? emit) {
    final workspace = container.read(calendarWorkspaceContextProvider);
    final current = workspace.asData?.value;
    if (workspace.hasError && !workspace.isLoading) {
      _failNativeCalendar('context-error', container, emit: emit);
    }
    if (container.read(calendarViewDateProvider) != date ||
        container.read(selectedCalendarScopeProvider).id != selection ||
        (current != null &&
            (current.ownerKey != owner ||
                current.selectedScope?.id != scope))) {
      _failNativeCalendar('context-changed', container, emit: emit);
    }
  }
}

Future<_NativeCalendarContext> _awaitNativeCalendarContext(
  WidgetTester tester,
  ProviderContainer container, {
  required DateTime deadline,
  required DateTime Function() now,
  void Function(String)? emit,
}) async {
  while (true) {
    final state = container.read(calendarWorkspaceContextProvider);
    if (state.hasError && !state.isLoading) {
      _failNativeCalendar('context-error', container, emit: emit);
    }
    if (!now().isBefore(deadline)) {
      _failNativeCalendar('context-readiness-deadline', container, emit: emit);
    }
    if (!state.isLoading && state.asData?.value.selectedScope != null) {
      return _NativeCalendarContext.capture(container);
    }
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Never _failNativeCalendar(
  String reason,
  ProviderContainer container, {
  void Function(String)? emit,
  int editorCount = 0,
  bool saveEnabled = false,
  bool visibleFailure = false,
  int modelMatches = 0,
  int rowMatches = 0,
}) {
  final state = container.read(calendarProvider);
  final workspace = container.read(calendarWorkspaceContextProvider);
  final error = state.error ?? workspace.error;
  final failure = error is CalendarFailure ? error : null;
  final status = failure?.httpStatus;
  (emit ?? _stage)(
    'calendar-validation-failed reason=$reason '
    'editorCount=${editorCount.clamp(0, 99)} saveEnabled=$saveEnabled '
    'visibleFailure=$visibleFailure loading=${state.isLoading} error=${state.hasError} '
    'modelMatches=${modelMatches.clamp(0, 99)} rowMatches=${rowMatches.clamp(0, 99)} '
    'failureKind=${failure?.kind.name ?? 'unavailable'} '
    'failureStage=${failure?.stage.name ?? 'unavailable'} '
    'httpStatus=${status != null && status >= 100 && status <= 599 ? status : 'unavailable'} '
    'writeOutcome=${error is CalendarWriteFailure
        ? error.outcome.name
        : failure?.writeMayHaveCommitted == true
        ? 'uncertain'
        : 'unavailable'}',
  );
  throw TestFailure('Shared Calendar validation failed: $reason.');
}

Finder _nativeCalendarEditor() => find.ancestor(
  of: find.byWidgetPredicate(
    (widget) =>
        widget is Semantics &&
        widget.properties.identifier == 'calendar-event-title',
  ),
  matching: find.byType(AlertDialog),
);

Future<void> _waitNativeCalendarEditorClosed(
  WidgetTester tester, {
  required ProviderContainer container,
  required _NativeCalendarContext expected,
  required DateTime deadline,
  DateTime Function()? now,
  void Function(String)? emit,
}) async {
  final clock = now ?? DateTime.now;
  while (true) {
    expected.check(container, emit);
    final editor = _nativeCalendarEditor();
    final count = editor.evaluate().length;
    if (count == 0) {
      if (!clock().isBefore(deadline)) {
        _failNativeCalendar('save-completion-deadline', container, emit: emit);
      }
      return;
    }
    if (count != 1) {
      _failNativeCalendar(
        'editor-not-unique',
        container,
        emit: emit,
        editorCount: count,
      );
    }
    final copy = AppLocalizations.of(tester.element(editor));
    final failures = {
      copy.calendarOperationFailure,
      copy.calendarUnsupportedEdit,
      copy.calendarVersionConflict,
      copy.calendarWriteUncertain,
      copy.calendarTitleRequired,
    };
    final visibleFailure = find
        .descendant(
          of: editor,
          matching: find.byWidgetPredicate(
            (widget) => widget is Text && failures.contains(widget.data),
          ),
        )
        .evaluate()
        .isNotEmpty;
    final save = find.descendant(
      of: find.descendant(
        of: editor,
        matching: find.byWidgetPredicate(
          (widget) =>
              widget is Semantics &&
              widget.properties.identifier == 'calendar-save-event',
        ),
      ),
      matching: find.byType(FilledButton),
    );
    final saveEnabled =
        save.evaluate().length == 1 &&
        tester.widget<FilledButton>(save).onPressed != null;
    if (visibleFailure && saveEnabled) {
      _failNativeCalendar(
        find
                .descendant(
                  of: editor,
                  matching: find.text(copy.calendarWriteUncertain),
                )
                .evaluate()
                .isNotEmpty
            ? 'save-uncertain'
            : 'save-rejected',
        container,
        emit: emit,
        editorCount: count,
        visibleFailure: true,
        saveEnabled: true,
      );
    }
    if (!clock().isBefore(deadline)) {
      _failNativeCalendar(
        'save-pending-deadline',
        container,
        emit: emit,
        editorCount: count,
        visibleFailure: visibleFailure,
        saveEnabled: saveEnabled,
      );
    }
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<CalendarEvent?> _calendarReadback(
  WidgetTester tester,
  String title,
  bool present,
) => readNativeDualCalendarEvent(
  tester,
  title,
  present,
  container: ProviderScope.containerOf(
    tester.element(find.byType(CalendarScreen)),
  ),
  deadline: DateTime.now().add(const Duration(seconds: 90)),
);

/// Reads through the actual refresh control; editor contents never prove an event.
Future<CalendarEvent?> readNativeDualCalendarEvent(
  WidgetTester tester,
  String title,
  bool present, {
  required ProviderContainer container,
  required DateTime deadline,
  DateTime Function()? now,
  void Function(String)? emit,
}) async {
  final clock = now ?? DateTime.now;
  await waitNativeDualCalendarRefresh(
    tester,
    screen: find.byType(CalendarScreen),
    container: container,
    deadline: deadline,
    now: now,
    emit: emit,
  );
  final expected = await _awaitNativeCalendarContext(
    tester,
    container,
    deadline: deadline,
    now: clock,
    emit: emit,
  );
  await _waitNativeCalendarEditorClosed(
    tester,
    container: container,
    expected: expected,
    deadline: deadline,
    now: now,
    emit: emit,
  );
  while (true) {
    expected.check(container, emit);
    await waitNativeDualCalendarRefresh(
      tester,
      screen: find.byType(CalendarScreen),
      container: container,
      deadline: deadline,
      now: now,
      emit: emit,
    );
    final screen = find.byType(CalendarScreen);
    final copy = AppLocalizations.of(tester.element(screen));
    final refresh = find.descendant(
      of: screen,
      matching: find.byWidgetPredicate(
        (widget) =>
            widget is IconButton &&
            widget.tooltip == copy.calendarRefreshButton,
      ),
    );
    if (find.byType(AlertDialog).evaluate().isNotEmpty ||
        refresh.evaluate().length != 1 ||
        refresh.hitTestable().evaluate().length != 1) {
      _failNativeCalendar('refresh-not-hittable', container, emit: emit);
    }
    if (!clock().isBefore(deadline)) {
      _failNativeCalendar('readback-deadline', container, emit: emit);
    }
    final before =
        container.read(calendarProvider).asData?.value.readGeneration ?? 0;
    await _tap(tester, refresh);
    int? renderedGeneration;
    while (true) {
      expected.check(container, emit);
      final current = container.read(calendarProvider);
      if (!clock().isBefore(deadline)) {
        _failNativeCalendar(
          current.hasError ? 'readback-error' : 'readback-deadline',
          container,
          emit: emit,
        );
      }
      final workspace = container.read(calendarWorkspaceContextProvider);
      if (current.hasError && !current.isLoading) {
        _failNativeCalendar('readback-error', container, emit: emit);
      }
      final value = current.asData?.value;
      if (!current.isLoading &&
          !workspace.isLoading &&
          !workspace.hasError &&
          value != null &&
          value.readGeneration > 0 &&
          value.readGeneration > before) {
        if (renderedGeneration != value.readGeneration) {
          // A completed provider request can precede its dependent widget frame.
          renderedGeneration = value.readGeneration;
          await tester.pump();
          continue;
        }
        final matches = value.events
            .where((event) => event.title == title)
            .toList();
        final titleRows = find.descendant(
          of: screen,
          matching: find.byWidgetPredicate(
            (widget) =>
                widget is Semantics &&
                widget.properties.identifier?.startsWith('calendar-event-') ==
                    true &&
                find
                    .descendant(
                      of: find.byWidget(widget),
                      matching: find.byWidgetPredicate(
                        (child) => child is Text && child.data == title,
                      ),
                    )
                    .evaluate()
                    .isNotEmpty,
          ),
        );
        final rowCount = titleRows.evaluate().length;
        if (matches.length > 1 || rowCount > 1) {
          _failNativeCalendar(
            'event-not-unique',
            container,
            emit: emit,
            modelMatches: matches.length,
            rowMatches: rowCount,
          );
        }
        if (!present && matches.isEmpty && rowCount == 0) {
          expected.check(container, emit);
          final after = container.read(calendarProvider);
          final afterWorkspace = container.read(
            calendarWorkspaceContextProvider,
          );
          if (!clock().isBefore(deadline)) {
            _failNativeCalendar('readback-deadline', container, emit: emit);
          }
          if (!after.isLoading &&
              !after.hasError &&
              !afterWorkspace.isLoading &&
              !afterWorkspace.hasError &&
              after.asData?.value.readGeneration == value.readGeneration) {
            return null;
          }
        }
        if (present && matches.length == 1 && rowCount == 1) {
          final event = matches.single;
          final row = find.descendant(
            of: screen,
            matching: find.byWidgetPredicate(
              (widget) =>
                  widget is Semantics &&
                  widget.properties.identifier == 'calendar-event-${event.id}',
            ),
          );
          if (row.evaluate().length == 1) {
            await tester.ensureVisible(row);
            await tester.pump();
            expected.check(container, emit);
            final after = container.read(calendarProvider);
            final afterWorkspace = container.read(
              calendarWorkspaceContextProvider,
            );
            if (!clock().isBefore(deadline)) {
              _failNativeCalendar('readback-deadline', container, emit: emit);
            }
            if (!after.isLoading &&
                !afterWorkspace.isLoading &&
                !afterWorkspace.hasError &&
                !after.hasError &&
                after.asData?.value.readGeneration == value.readGeneration &&
                row.hitTestable().evaluate().length == 1) {
              return event;
            }
          }
        }
        break; // Preserve peer eventual-read retries, always via another real refresh.
      }
      if (!clock().isBefore(deadline)) {
        _failNativeCalendar(
          current.hasError ? 'readback-error' : 'readback-deadline',
          container,
          emit: emit,
        );
      }
      await tester.pump(const Duration(milliseconds: 100));
    }
    if (!clock().isBefore(deadline)) {
      _failNativeCalendar('readback-deadline', container, emit: emit);
    }
  }
}
