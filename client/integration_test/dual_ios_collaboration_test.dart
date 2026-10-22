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
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/widgets/success_state.dart';
import 'package:weave/features/onboarding/domain/entities/member_auth_onboarding_state.dart';
import 'package:weave/features/onboarding/domain/use_cases/discover_organization_access.dart';
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
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/features/files/presentation/providers/files_provider.dart';
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
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_client_provider.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';

const _runId = String.fromEnvironment('WEAVE_NATIVE_TEST_RUN_ID');
const _simulator = bool.fromEnvironment('WEAVE_NATIVE_EXPECT_SIMULATOR');
const _disposable = bool.fromEnvironment('WEAVE_DEVICE_DISPOSABLE_STACK');
const _actorTestBudget = Duration(minutes: 30);

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

void _installNativeDualProxy() {
  if (!(Platform.isIOS && _simulator && _disposable && _runId.isNotEmpty)) {
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
  required String authState,
  required String visibleState,
  required String errorCode,
  required Map<String, int> counts,
}) {
  String known(String value, Set<String> allowed) =>
      allowed.contains(value) ? value : 'unavailable';
  return 'NATIVE_PRODUCT_STAGE phase=onboarding-failed '
      'actor=${known(role, {'A', 'B'})} '
      'authState=${known(authState, MemberAuthOnboardingStage.values.map((state) => state.serialized).toSet())} '
      'visibleState=${known(visibleState, {'handoff_ready', 'handoff_error', 'authenticated_redirect'})} '
      'errorCode=${known(errorCode, {'none', 'WEAVE-APP-START-DISCOVERY-INVALID', 'WEAVE-APP-START-DISCOVERY-FAILED', 'WEAVE-APP-START-TIMEOUT', 'WEAVE-APP-START-TLS-FAILED', 'WEAVE-APP-START-DNS-FAILED', 'WEAVE-APP-START-NETWORK-FAILED', 'WEAVE-LAN-UNREACHABLE', 'WEAVE-HANDOFF-INVALID', 'WEAVE-SSO-CANCELLED', 'WEAVE-SSO-CONFIGURATION-MISSING', 'WEAVE-SSO-NOT-COMPLETE', 'WEAVE-SESSION-STORAGE-FAILED', 'WEAVE-SSO-UNSUPPORTED-DEVICE', 'WEAVE-SSO-UNKNOWN', 'WEAVE-MOBILE-OFFLINE-SESSION-DENIED', 'WEAVE-WORKSPACE-BOOTSTRAP-NOT-READY', 'PlatformException', 'StateError', 'FormatException'})} '
      '${['organization', 'handoff', 'ready', 'signIn', 'workspace'].map((name) => '${name}Count=${(counts[name] ?? 0).clamp(0, 999)}').join(' ')}';
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
  final store = container.read(preferencesStoreProvider);
  Future<Map<String, dynamic>> read(String key) async {
    final raw = await store.getString(key);
    if (raw == null || raw.length > 16384) return {};
    return jsonDecode(raw) as Map<String, dynamic>;
  }

  final auth = await read(dogfoodAuthStateStorageKey);
  final visible = await read(dogfoodVisibleStateStorageKey);
  final consumed = await read(lastHandoffConsumedStorageKey);
  emitNativeProductDiagnostic(
    nativeDualOnboardingDiagnostic(
      role: role,
      authState: auth['state'] is String
          ? auth['state'] as String
          : 'unavailable',
      visibleState: visible['state'] is String
          ? visible['state'] as String
          : 'unavailable',
      errorCode:
          (visible['errorCode'] ??
                  consumed['errorCode'] ??
                  auth['errorCode'] ??
                  'none')
              .toString(),
      counts: {
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
  if (!(Platform.isIOS && _simulator && _disposable && _runId.isNotEmpty)) {
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
  if (fixture['runId'] != _runId || !['A', 'B'].contains(fixture['role'])) {
    throw StateError('Local inspection actor binding is unavailable.');
  }
  // The inspection owner terminates this isolate with its owned app process.
  _installNativeDualProxy();
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
            'runId': _runId,
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
        'runId': _runId,
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
          'runId': _runId,
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
      expect(
        Platform.isIOS && _simulator && _disposable && _runId.isNotEmpty,
        isTrue,
      );
      final support = await getApplicationSupportDirectory();
      final fixtureFile = File('${support.path}/weave-dual-ios-input.json');
      final fixture =
          jsonDecode(await fixtureFile.readAsString()) as Map<String, dynamic>;
      expect(fixture['role'], isIn(['A', 'B']));
      expect(fixture['runId'], _runId);
      final previousHttpOverrides = HttpOverrides.current;
      _installNativeDualProxy();
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
          final rawState = await onboardingContainer
              .read(preferencesStoreProvider)
              .getString(dogfoodAuthStateStorageKey);
          expect(rawState, isNotNull);
          expect(
            (jsonDecode(rawState!) as Map<String, dynamic>)['state'],
            'ready_for_sso',
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
          '${support.path}/weave-native-driver-$_runId.ready',
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
      const conversation = 'Weave UI $_runId';
      const messageA = 'A UI $_runId';
      const messageB = 'B UI $_runId';
      const eventTitle = 'Calendar UI $_runId';
      const updatedTitle = '$eventTitle updated';
      final fileName = fixture['initialFileName'] as String;
      final replacementName = fixture['replacementFileName'] as String;
      final initialContent = fixture['initialContent'] as String;
      final replacementContent = fixture['replacementContent'] as String;
      String? fileId = prior?['resources']?['fileId'] as String?;
      String? fileRevision = recovery['fileRevision'] as String?;
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
        final actionDeadline = DateTime.now().add(const Duration(seconds: 180));
        switch (action) {
          case 'chat-create-send':
            expect(role, 'A');
            late Finder name;
            try {
              name = await openNativeDualChatCreationDialog(
                tester,
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
            await _calendarReadback(tester, eventTitle, true);
            eventId = container
                .read(calendarProvider)
                .requireValue
                .events
                .where((event) => event.title == eventTitle)
                .single
                .id;
          case 'calendar-observe':
            expect(role, 'B');
            await tapNativeDualBack(tester, deadline: actionDeadline);
            await navigateNativeDualDestination(
              tester,
              Icons.calendar_month_outlined,
            );
            await _calendarReadback(tester, eventTitle, true);
            eventId = container
                .read(calendarProvider)
                .requireValue
                .events
                .where((event) => event.title == eventTitle)
                .single
                .id;
          case 'calendar-update':
            expect(role, 'A');
            await editNativeDualCalendarEvent(
              tester,
              tooltip: _copy(tester).calendarEditEventTooltip(eventTitle),
              title: updatedTitle,
              deadline: actionDeadline,
            );
            await _calendarReadback(tester, updatedTitle, true);
            expect(
              container
                  .read(calendarProvider)
                  .requireValue
                  .events
                  .where((event) => event.title == updatedTitle)
                  .single
                  .id,
              eventId,
            );
          case 'calendar-observe-updated':
            expect(role, 'B');
            await _calendarReadback(tester, updatedTitle, true);
            expect(find.text(eventTitle), findsNothing);
            expect(
              container
                  .read(calendarProvider)
                  .requireValue
                  .events
                  .where((event) => event.title == updatedTitle)
                  .single
                  .id,
              eventId,
            );
          case 'calendar-delete':
            expect(role, 'A');
            await navigateNativeDualDestination(
              tester,
              Icons.calendar_month_outlined,
            );
            await _calendarReadback(tester, updatedTitle, true);
            await _tap(
              tester,
              find.byTooltip(
                _copy(tester).calendarDeleteEventTooltip(updatedTitle),
              ),
            );
            final beforeCancellation = container
                .read(calendarProvider)
                .requireValue
                .events
                .where((event) => event.id == eventId)
                .single;
            await _tap(
              tester,
              find.widgetWithText(
                TextButton,
                _copy(tester).calendarCancelButton,
              ),
            );
            await _calendarReadback(tester, updatedTitle, true);
            expect(
              container
                  .read(calendarProvider)
                  .requireValue
                  .events
                  .where((event) => event.id == eventId)
                  .single
                  .etag,
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
              container,
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
              container,
              () => _wait(
                tester,
                () =>
                    container
                        .read(filesProvider)
                        .asData
                        ?.value
                        .uploadStatus
                        .phase ==
                    FilesUploadPhase.completed,
                const Duration(seconds: 90),
              ),
            );
            final uploaded = container
                .read(filesProvider)
                .requireValue
                .directoryListing!
                .entries
                .where((entry) => entry.name == fileName)
                .single;
            fileId = uploaded.id;
            fileRevision = uploaded.revision;
            expect(fileId, isNotEmpty);
            expect(fileRevision, isNotNull);
            await _filePreview(tester, fileId, initialContent);
          case 'files-open':
            expect(role, 'B');
            await _selectSharedFiles(
              tester,
              container,
              fixture['sharedSpaceRef'] as String,
            );
            await _wait(
              tester,
              () =>
                  container
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
            final shared = container
                .read(filesProvider)
                .requireValue
                .directoryListing!
                .entries
                .where((entry) => entry.name == fileName)
                .single;
            fileId = shared.id;
            fileRevision = shared.revision;
            await _filePreview(tester, fileId, initialContent);
          case 'files-replace':
            expect(role, 'A');
            expect(fileId, isNotNull);
            await writeNativeDualPrivateJson(
              File('${support.path}/weave-dual-ios-native-request.json'),
              {'sequence': next, 'mode': 'import', 'fileName': replacementName},
            );
            await _tap(tester, find.byKey(Key('files-update-$fileId')));
            await _wait(tester, () {
              final state = container.read(filesProvider).asData?.value;
              final entry = state?.directoryListing?.entries.where(
                (entry) => entry.id == fileId,
              );
              return state?.entryActionStatus.phase ==
                      FilesEntryActionPhase.replacedContent &&
                  entry?.length == 1 &&
                  entry!.single.revision != fileRevision;
            }, const Duration(seconds: 90));
            final replaced = container
                .read(filesProvider)
                .requireValue
                .directoryListing!
                .entries
                .where((entry) => entry.id == fileId)
                .single;
            expect(replaced.name, fileName);
            expect(replaced.revision, isNot(fileRevision));
            fileRevision = replaced.revision;
            await _filePreview(tester, fileId!, replacementContent);
          case 'files-open-updated':
            expect(role, 'B');
            await navigateNativeDualDestination(tester, Icons.home_outlined);
            await _selectSharedFiles(
              tester,
              container,
              fixture['sharedSpaceRef'] as String,
            );
            await _wait(
              tester,
              () =>
                  container
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
            fileRevision = container
                .read(filesProvider)
                .requireValue
                .directoryListing!
                .entries
                .where((entry) => entry.id == fileId)
                .single
                .revision;
            await _filePreview(tester, fileId!, replacementContent);
          case 'files-export-cancel':
          case 'files-export':
          case 'files-export-updated':
            expect(fileId, isNotNull);
            final documents = await getApplicationDocumentsDirectory();
            exportSentinel ??= await createNativeDualExportSentinel(
              documents,
              _runId,
            );
            await requireNativeDualExportSentinelUnchanged(
              exportSentinel,
              _runId,
            );
            final beforeExport = await nativeDualExportSnapshot(documents);
            final cancelling = action == 'files-export-cancel';
            var exportingObserved = false;
            final exportSubscription = container.listen(filesProvider, (
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
              await writeNativeDualPrivateJson(
                File('${support.path}/weave-dual-ios-native-request.json'),
                {
                  'sequence': next,
                  'mode': cancelling ? 'export-cancel' : 'export',
                  'fileName': fileName,
                },
              );
              await _tap(tester, find.byKey(Key('files-export-$fileId')));
              await _wait(
                tester,
                () =>
                    exportingObserved &&
                    container
                            .read(filesProvider)
                            .asData
                            ?.value
                            .entryActionStatus
                            .phase ==
                        (cancelling
                            ? FilesEntryActionPhase.idle
                            : FilesEntryActionPhase.exportedEntry) &&
                    container.read(filesProvider).asData?.value.isBusy == false,
                const Duration(seconds: 90),
              );
              await requireNativeDualExportSentinelUnchanged(
                exportSentinel,
                _runId,
              );
              if (cancelling) {
                final state = container.read(filesProvider).requireValue;
                expect(state.entryActionStatus.destination, isNull);
                expect(state.directoryFailure, isNull);
                expect(await nativeDualExportSnapshot(documents), beforeExport);
                break;
              }
              final output = await requireNativeDualExportDestination(
                documents,
                beforeExport,
                container
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
              'A recoverable $_runId',
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
              'B remains online $_runId',
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
                  _message('A recoverable $_runId').evaluate().length == 1 &&
                  _message('B remains online $_runId').evaluate().length == 1,
              const Duration(seconds: 90),
            );
            recovery['network'] = true;
          case 'network-peer-readback':
            expect(role, 'B');
            await _wait(
              tester,
              () =>
                  _message('A recoverable $_runId').evaluate().length == 1 &&
                  _message('B remains online $_runId').evaluate().length == 1,
              const Duration(seconds: 90),
            );
            recovery['network'] = true;
          case 'background-peer-send':
            expect(role, 'B');
            await sendNativeDualChatMessage(
              tester,
              'B background catchup $_runId',
              deadline: actionDeadline,
            );
            recovery['background'] = true;
          case 'background-catchup':
            expect(role, 'A');
            await _wait(
              tester,
              () =>
                  _message('B background catchup $_runId').evaluate().length ==
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
            await store.write(
              'dual_native_expiry_$_runId',
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
                'B remains online $_runId',
                'A recoverable $_runId',
                'B background catchup $_runId',
              ],
              roomId: resources['roomId'] as String,
              deadline: actionDeadline,
            );
            await tapNativeDualBack(tester, deadline: actionDeadline);
            await _selectSharedFiles(
              tester,
              container,
              fixture['sharedSpaceRef'] as String,
            );
            final entry = container
                .read(filesProvider)
                .requireValue
                .directoryListing!
                .entries
                .where((entry) => entry.name == fileName)
                .single;
            expect(entry.id, fileId);
            expect(entry.revision, fileRevision);
            await _filePreview(tester, entry.id, replacementContent);
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
              container,
              fixture['sharedSpaceRef'] as String,
            );
            final entry = container
                .read(filesProvider)
                .requireValue
                .directoryListing!
                .entries
                .where((entry) => entry.name == fileName)
                .single;
            expect(entry.id, fileId);
            await _filePreview(tester, entry.id, replacementContent);
            recovery['grantRegrantReadback'] = true;
          case 'expiry-refresh':
            expect(restoring, isTrue);
            final expired = await store.read('dual_native_expiry_$_runId');
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
            await store.delete('dual_native_expiry_$_runId');
            // The normal User UI must still read the actual shared file after
            // refresh; successful token exchange alone is not product evidence.
            await _selectSharedFiles(
              tester,
              container,
              fixture['sharedSpaceRef'] as String,
            );
            await _filePreview(tester, fileId!, replacementContent);
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
              container,
              fixture['sharedSpaceRef'] as String,
            );
            await _filePreview(tester, fileId!, replacementContent);
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

Future<void> _selectSharedFiles(
  WidgetTester tester,
  ProviderContainer container,
  String spaceRef,
) async {
  await navigateNativeDualDestination(tester, Icons.folder_outlined);
  await selectNativeDualFilesScope(tester, spaceRef);
  await _wait(tester, () {
    final state = container.read(filesProvider).asData?.value;
    return state?.rootSpaceRef == spaceRef &&
        state?.directoryListing != null &&
        !state!.isBusy &&
        state.directoryFailure == null;
  }, const Duration(seconds: 90));
}

/// Selects the real popup option by its stable ordinary accessibility identity.
Future<void> selectNativeDualFilesScope(
  WidgetTester tester,
  String? spaceRef,
) async {
  final selector = find.byKey(const Key('files-space-selector'));
  await _wait(
    tester,
    () => selector.evaluate().length == 1,
    const Duration(seconds: 90),
  );
  await _tap(tester, selector);
  final choice = find.byWidgetPredicate(
    (widget) =>
        widget is Semantics &&
        widget.properties.identifier ==
            'files-space-option-${spaceRef ?? 'private'}',
  );
  expect(choice, findsOneWidget);
  await _tap(tester, choice);
}

Future<void> _filePreview(
  WidgetTester tester,
  String fileId,
  String expected,
) async {
  await _tap(tester, find.byKey(Key('files-open-$fileId')));
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
}) async {
  final control = find.byWidgetPredicate(
    (widget) => widget is IconButton && widget.tooltip == tooltip,
  );
  Duration remaining() => deadline.difference(DateTime.now());
  await _wait(
    tester,
    () =>
        control.evaluate().length == 1 &&
        tester.widget<IconButton>(control).onPressed != null &&
        control.hitTestable().evaluate().length == 1,
    remaining(),
  );
  if (!DateTime.now().isBefore(deadline)) {
    throw TestFailure('The real Calendar editor action deadline expired.');
  }
  final copy = AppLocalizations.of(tester.element(control));
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
  if (!DateTime.now().isBefore(deadline)) {
    throw TestFailure('The real Calendar editor action deadline expired.');
  }
  expect(field, findsOneWidget);
  await tester.enterText(field, title);
  if (!DateTime.now().isBefore(deadline)) {
    throw TestFailure('The real Calendar editor action deadline expired.');
  }
  await expectLater(tester, meetsGuideline(iOSTapTargetGuideline));
  await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
  await _tap(
    tester,
    find.widgetWithText(FilledButton, copy.calendarSaveButton),
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

Future<void> _calendarReadback(
  WidgetTester tester,
  String title,
  bool present,
) async {
  final deadline = DateTime.now().add(const Duration(seconds: 90));
  final container = ProviderScope.containerOf(
    tester.element(find.byType(WeaveApp)),
  );
  while (true) {
    final refresh = await waitNativeDualCalendarRefresh(
      tester,
      screen: find.byType(CalendarScreen),
      container: container,
      deadline: deadline,
    );
    await tester.drag(refresh, const Offset(0, 450));
    await tester.pump(const Duration(seconds: 2));
    final current = container.read(calendarProvider);
    if (!current.isLoading &&
        current.hasValue &&
        !current.hasError &&
        find.text(title).evaluate().length == (present ? 1 : 0)) {
      return;
    }
    if (!DateTime.now().isBefore(deadline)) {
      throw TestFailure('Shared Calendar UI readback did not converge.');
    }
  }
}
