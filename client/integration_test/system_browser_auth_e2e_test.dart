import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:ui' as ui;

import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:path_provider/path_provider.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/bootstrap/presentation/bootstrap_gate.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/onboarding/presentation/organization_access_screen.dart';
import 'package:weave/core/persistence/secure_store.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/domain/entities/auth_session.dart';
import 'package:weave/features/auth/domain/entities/oidc_constants.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/auth/presentation/providers/auth_flow_controller.dart';
import 'package:weave/features/chat/data/repositories/matrix_device_identity_repository.dart';
import 'package:weave/features/chat/data/repositories/native_matrix_chat_repository.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/entities/calendar_failure.dart';
import 'package:weave/features/calendar/presentation/calendar_screen.dart';
import 'package:weave/features/chat/presentation/chat_screen.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/files_screen.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_session_access.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/weave_member_matrix_session_coordinator.dart';
import 'package:weave/integrations/rust_matrix_core/presentation/providers/matrix_crypto_session_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_client_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';
import 'package:weave/integrations/weave_api/data/services/weave_user_api_client.dart';
import 'package:weave/generated/user_api/api.dart' as user_api;
import 'package:weave/main.dart';

import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/widgets/success_state.dart';
import 'package:weave/features/onboarding/presentation/member_handoff_screen.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import 'helpers/native_acceptance_preferences_store.dart';
import 'helpers/test_config.dart';

// test_api routes test-zone prints into result messages. flutter drive consumes
// the final result rather than streaming those messages to the native driver.
// Only these existing bounded native diagnostics bypass that captured zone.
void emitNativeProductDiagnostic(
  String message, {
  required bool simulatorDriver,
}) {
  if (message.length > 2048 ||
      message.contains('\n') ||
      message.contains('\r') ||
      ![
        'NATIVE_PRODUCT_STAGE ',
        'NATIVE_PRODUCT_INITIAL_RESULT ',
        'NATIVE_PRODUCT_SIGN_IN_RESULT ',
      ].any(message.startsWith)) {
    throw ArgumentError('Unsupported native diagnostic record.');
  }
  if (simulatorDriver) {
    // ignore: avoid_print
    Zone.root.run(() => print(message));
  } else {
    debugPrint(message);
  }
}

void _nativeProductDiagnostic(String message) => emitNativeProductDiagnostic(
  message,
  simulatorDriver: _usesSimulatorDriver(
    const String.fromEnvironment('WEAVE_NATIVE_TEST_RUN_ID'),
  ),
);

({String failure, String status}) nativeBackendFailureDiagnostic(
  Object? cause,
) {
  var category = 'none';
  // Inspect only our typed wrappers, never arbitrary exception text or payloads.
  for (var depth = 0; depth < 4; depth++) {
    if (cause is AppFailure) {
      final mapped = switch (cause.message) {
        'The Weave backend rejected the current session.' => 'session-rejected',
        'Unable to reach the Weave backend right now.' => 'timeout',
        'The Weave backend failed to reconcile organization access.' =>
          'request-failed',
        'The Weave backend returned an invalid identity-session reconciliation payload.' =>
          'invalid-payload',
        'The Weave backend returned an unknown identity-session reconciliation state.' =>
          'unknown-state',
        'The Weave backend returned an inconsistent identity-session reconciliation state.' =>
          'inconsistent-state',
        'Unable to decode a Weave backend response right now.' =>
          'response-decode',
        _ => 'none',
      };
      if (mapped != 'none') category = mapped;
      cause = cause.cause;
    } else if (cause is AuthFailure) {
      cause = cause.cause;
    } else {
      return (
        failure: category,
        status: cause is int && cause >= 100 && cause <= 599
            ? cause.toString()
            : 'unavailable',
      );
    }
  }
  return (failure: category, status: 'unavailable');
}

void emitNativeAuthFailureDiagnostic(
  AuthFailure failure, {
  required bool simulatorDriver,
}) {
  final cause = failure.cause;
  final appFailure = cause is AppFailure ? cause : null;
  final nestedCause = appFailure?.cause ?? cause;
  final platform = nestedCause is FlutterAppAuthPlatformException
      ? nestedCause
      : null;
  final backend = nativeBackendFailureDiagnostic(failure);
  String safeCode(String? value) =>
      value != null && RegExp(r'^[A-Za-z0-9_.-]{1,80}$').hasMatch(value)
      ? value
      : 'unavailable';
  emitNativeProductDiagnostic(
    'NATIVE_PRODUCT_STAGE phase=auth-failed '
    'category=${failure.type.name} causeType=${cause.runtimeType} '
    'appFailureType=${appFailure?.type.name ?? 'none'} '
    'nestedCauseType=${nestedCause.runtimeType} '
    'backendFailure=${backend.failure} backendStatus=${backend.status} '
    'appAuthCode=${safeCode(platform?.code)} '
    'nativeType=${safeCode(platform?.platformErrorDetails.type)} '
    'nativeCode=${safeCode(platform?.platformErrorDetails.code)} '
    'nativeDomain=${safeCode(platform?.platformErrorDetails.domain)}',
    simulatorDriver: simulatorDriver,
  );
}

String nativeBootstrapDiagnostic({
  required String asyncState,
  required String bootstrapPhase,
  required String failureType,
  required String causeType,
  required String platformCode,
  required String lifecycle,
  String backendFailure = 'none',
  String backendStatus = 'unavailable',
  required Map<String, int> counts,
  required bool simulatorDriver,
  required bool productEnabled,
  required bool disposable,
  required bool offline,
}) {
  String known(String value, List<String> allowed) =>
      allowed.contains(value) ? value : 'unavailable';
  final code =
      RegExp(r'^-?[0-9]{1,6}$').stringMatch(platformCode) == platformCode
      ? platformCode
      : 'unavailable';
  return 'NATIVE_PRODUCT_STAGE phase=shell-bootstrap-failed '
      'asyncState=${known(asyncState, ['loading', 'error', 'data'])} '
      'bootstrapPhase=${known(bootstrapPhase, ['loading', 'needsSetup', 'needsSignIn', 'ready', 'error'])} '
      'failureType=${known(failureType, ['none', 'bootstrap', 'storage', 'validation', 'unknown'])} '
      'causeType=${known(causeType, ['none', 'AppFailure', 'AuthFailure', 'PlatformException', 'StateError', 'FormatException', 'TimeoutException', 'SocketException', 'FlutterAppAuthPlatformException'])} '
      'platformCode=$code '
      'lifecycle=${known(lifecycle, ['resumed', 'inactive', 'hidden', 'paused', 'detached'])} '
      'backendFailure=${known(backendFailure, ['none', 'session-rejected', 'timeout', 'request-failed', 'invalid-payload', 'unknown-state', 'inconsistent-state', 'response-decode'])} '
      'backendStatus=${RegExp(r"^[1-5][0-9]{2}$").stringMatch(backendStatus) == backendStatus ? backendStatus : 'unavailable'} '
      '${['root', 'bootstrapGate', 'error', 'loading', 'onboarding', 'signIn', 'workspace'].map((key) => '${key}Count=${(counts[key] ?? 0).clamp(0, 999)}').join(' ')} '
      'simulatorDriver=$simulatorDriver productEnabled=$productEnabled '
      'disposable=$disposable offline=$offline';
}

void safelyReportNativeBootstrapFailure({
  required void Function() report,
  required void Function() unavailable,
}) {
  try {
    report();
  } catch (_) {
    try {
      unavailable();
    } catch (_) {
      // Reporting must never replace the original integration failure.
    }
  }
}

void _reportNativeBootstrapFailure(WidgetTester tester, TestConfig config) {
  final root = find.byType(WeaveApp);
  var asyncState = 'unavailable';
  var bootstrapPhase = 'unavailable';
  AppFailure? failure;
  Object? error;
  if (root.evaluate().length == 1) {
    final container = ProviderScope.containerOf(tester.element(root));
    container
        .read(appBootstrapProvider)
        .when(
          loading: () => asyncState = 'loading',
          error: (cause, _) {
            asyncState = 'error';
            error = cause;
            if (cause is AppFailure) failure = cause;
          },
          data: (state) {
            asyncState = 'data';
            bootstrapPhase = state.phase.name;
            failure = state.failure;
          },
        );
  }
  final cause = failure?.cause ?? error;
  final backend = nativeBackendFailureDiagnostic(failure ?? error);
  _nativeProductDiagnostic(
    nativeBootstrapDiagnostic(
      asyncState: asyncState,
      bootstrapPhase: bootstrapPhase,
      failureType: failure?.type.name ?? 'none',
      causeType: cause?.runtimeType.toString() ?? 'none',
      platformCode: cause is PlatformException ? cause.code : '',
      backendFailure: backend.failure,
      backendStatus: backend.status,
      lifecycle: tester.binding.lifecycleState?.name ?? 'unavailable',
      counts: {
        'root': root.evaluate().length,
        'bootstrapGate': find.byType(BootstrapGate).evaluate().length,
        'error': find.byType(ErrorState).evaluate().length,
        'loading': find.byType(LoadingState).evaluate().length,
        'onboarding': find.byType(OrganizationAccessScreen).evaluate().length,
        'signIn': find
            .byKey(const ValueKey('weave.auth.sign-in'))
            .evaluate()
            .length,
        'workspace': find
            .byKey(const ValueKey('weave.workspace.home'))
            .evaluate()
            .length,
      },
      simulatorDriver: _usesSimulatorDriver(
        const String.fromEnvironment('WEAVE_NATIVE_TEST_RUN_ID'),
      ),
      productEnabled: const bool.fromEnvironment(
        'WEAVE_SINGLE_SIGN_IN_PRODUCT_E2E',
      ),
      disposable: const bool.fromEnvironment('WEAVE_DEVICE_DISPOSABLE_STACK'),
      offline: config.offlineContractOnly,
    ),
  );
}

// flutter drive preserves the simulator installation between process phases;
// select the same journey formerly selected by flutter test --plain-name.
bool nativeProductPhaseEnabled({
  required bool simulator,
  required String selected,
  required String journey,
}) => !simulator || selected == journey;

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  // The macOS accessibility service can activate after testWidgets records
  // its semantics-handle baseline. Keep semantics enabled from the outset so
  // the platform-owned handle is included without masking later leaks.
  binding.platformDispatcher.semanticsEnabledTestValue = true;
  // Flutter's test dispatcher does not forward setSemanticsTreeEnabled. Enable
  // the real engine publication as well so native accessibility sees the tree.
  ui.PlatformDispatcher.instance.setSemanticsTreeEnabled(true);
  // Native XCTest and physical-device interactions must reach the production
  // controls instead of being dropped by the widget test binding.
  binding.shouldPropagateDevicePointerEvents = true;

  const enabled = bool.fromEnvironment('WEAVE_SYSTEM_BROWSER_AUTH_E2E');
  const matrixEnabled = bool.fromEnvironment('WEAVE_MEMBER_MATRIX_E2E');
  const productEnabled = bool.fromEnvironment(
    'WEAVE_SINGLE_SIGN_IN_PRODUCT_E2E',
  );
  const disposableMessageEnabled = bool.fromEnvironment(
    'WEAVE_DISPOSABLE_MATRIX_MESSAGE_E2E',
  );
  const twoDeviceRecoveryEnabled = bool.fromEnvironment(
    'WEAVE_TWO_DEVICE_MATRIX_RECOVERY_E2E',
  );
  const disposableStack = bool.fromEnvironment('WEAVE_DEVICE_DISPOSABLE_STACK');
  const nativeTestRunId = String.fromEnvironment('WEAVE_NATIVE_TEST_RUN_ID');
  const productPhase = String.fromEnvironment('WEAVE_DEVICE_PRODUCT_PHASE');
  final simulatorProduct =
      productEnabled && _usesSimulatorDriver(nativeTestRunId);
  final config = TestConfig.fromEnvironment();

  test('native acceptance requires an explicitly selected live journey', () {
    expect(
      enabled || matrixEnabled || productEnabled,
      isTrue,
      reason:
          'No native acceptance journey was selected. An all-skipped '
          'Flutter run is not integration evidence.',
    );
    if (productEnabled ||
        disposableMessageEnabled ||
        twoDeviceRecoveryEnabled) {
      expect(
        disposableStack,
        isTrue,
        reason:
            'Product and Matrix mutation journeys require a disposable stack.',
      );
      expect(
        nativeTestRunId,
        isNotEmpty,
        reason:
            'Live mutation journeys require an isolated native storage account.',
      );
    }
    expect(config.offlineContractOnly, isFalse);
    if (simulatorProduct) {
      expect(productPhase, isIn(['initial', 'restore']));
    }
  });

  testWidgets(
    'activation and OIDC Authorization Code with PKCE use the system browser',
    (tester) async {
      final container = await _openWeaveSession(tester, config);
      final refreshed = await container
          .read(authSessionRepositoryProvider)
          .refreshSession(
            AuthConfiguration(
              issuer: config.issuerUrl,
              clientId: config.clientId,
            ),
          );
      expect(refreshed.isAuthenticated, isTrue);
      expect(refreshed.session?.accessToken, isNotEmpty);
      debugPrint(
        'PHYSICAL_AUTH_SESSION_RESULT status=passed activation=system-browser '
        'pkce=true workspaceRestored=true refresh=true supportSafe=true',
      );
    },
    skip: !enabled || simulatorProduct,
    timeout: const Timeout(Duration(minutes: 7)),
  );

  testWidgets(
    'one fresh sign-in makes Files Calendar and native Matrix usable',
    (tester) async {
      final container = await _openWeaveSession(
        tester,
        config,
        requireFreshSignIn: true,
      );
      final initialSession = await container
          .read(authSessionRepositoryProvider)
          .restoreSession(
            AuthConfiguration(
              issuer: config.issuerUrl,
              clientId: config.clientId,
            ),
          );
      expect(initialSession.isAuthenticated, isTrue);
      final expiryProbeToken = initialSession.session!.accessToken;
      final files = container.read(filesRepositoryProvider);
      final calendar = container.read(calendarRepositoryProvider);
      final coordinator = container.read(
        matrixCryptoSessionCoordinatorProvider,
      );
      try {
        _nativeProductDiagnostic('NATIVE_PRODUCT_STAGE phase=files-start');
        expect(
          (await files.restoreConnection()).status,
          FilesConnectionStatus.connected,
        );
        final root = await files.listDirectory('/');
        expect(root.path, '/');
        expect(root.allowedActions, contains('upload'));
        final fileName =
            'weave-native-${DateTime.now().toUtc().microsecondsSinceEpoch}.txt';
        final fileBytes = utf8.encode(
          'Weave native Files acceptance $fileName',
        );
        await files.uploadFile(
          '/',
          FileUploadRequest(
            fileName: fileName,
            sizeInBytes: fileBytes.length,
            byteStream: Stream.value(fileBytes),
          ),
        );
        final uploaded = (await files.listDirectory(
          '/',
        )).entries.where((entry) => entry.name == fileName).single;
        expect(uploaded.id, startsWith('file:'));
        expect(uploaded.isDirectory, isFalse);
        final download = await (files as FilesExportRepository).downloadFile(
          uploaded,
        );
        expect(download.bytes, orderedEquals(fileBytes));
        _nativeProductDiagnostic('NATIVE_PRODUCT_STAGE phase=files-passed');

        _nativeProductDiagnostic('NATIVE_PRODUCT_STAGE phase=calendar-start');
        final scopes = await calendar.loadScopes();
        expect(scopes.scopes, isNotEmpty);
        CalendarEventList agenda;
        try {
          agenda = await calendar.loadEvents(scope: scopes.scopes.first);
        } on CalendarFailure catch (error) {
          _nativeProductDiagnostic(
            'NATIVE_PRODUCT_STAGE phase=calendar-agenda-failed kind=${error.kind.name}',
          );
          rethrow;
        } catch (error) {
          _nativeProductDiagnostic(
            'NATIVE_PRODUCT_STAGE phase=calendar-agenda-failed type=${error.runtimeType}',
          );
          rethrow;
        }
        expect(agenda.scope.id, scopes.scopes.first.id);
        final writableScope = scopes.scopes.firstWhere(
          (scope) => scope.capabilities.contains('create'),
        );
        final eventStart = DateTime.now().toUtc().add(const Duration(days: 2));
        final eventDraft = CalendarEventDraft(
          title: 'Weave native Calendar acceptance',
          startTime: eventStart,
          endTime: eventStart.add(const Duration(hours: 1)),
          timezone: 'UTC',
          timeKind: CalendarTimeKind.utc,
          scope: writableScope,
        );
        final created = await calendar.createEvent(eventDraft);
        expect((await calendar.readEvent(created.id)).title, eventDraft.title);
        final updated = await calendar.updateEvent(
          created.id,
          CalendarEventDraft(
            title: 'Weave native Calendar updated',
            startTime: eventDraft.startTime,
            endTime: eventDraft.endTime,
            timezone: eventDraft.timezone,
            timeKind: eventDraft.timeKind,
            scope: writableScope,
          ),
          etag: created.etag,
        );
        expect((await calendar.readEvent(updated.id)).title, updated.title);
        await calendar.deleteEvent(updated.id, etag: updated.etag);
        await expectLater(calendar.readEvent(updated.id), throwsA(anything));
        _nativeProductDiagnostic('NATIVE_PRODUCT_STAGE phase=calendar-passed');

        _nativeProductDiagnostic('NATIVE_PRODUCT_STAGE phase=matrix-start');
        MatrixCryptoSession matrix;
        try {
          matrix = await coordinator.open(allowInteractiveSignIn: false);
        } on ChatFailure catch (error) {
          _nativeProductDiagnostic(
            'NATIVE_PRODUCT_STAGE phase=matrix-open-failed '
            'type=${error.type.name} code=${error.message} '
            'causeType=${error.cause.runtimeType} '
            'httpStatus=${error.cause is int ? error.cause : 'none'}',
          );
          if (error.message == 'M_WEAVE_MATRIX_ACCESS_DENIED') {
            try {
              final session = await container.read(
                weaveAuthenticatedSessionProvider.future,
              );
              if (session != null) {
                final api = weaveUserApiClient(
                  apiBaseUrl: session.apiBaseUrl,
                  accessToken: session.accessToken,
                  httpClient: container.read(weaveApiHttpClientProvider),
                );
                final identity = await user_api.IdentityApi(api).me();
                final capabilities = await user_api.WorkspaceApi(
                  api,
                ).capabilities();
                final chat = capabilities?.chat;
                _nativeProductDiagnostic(
                  'NATIVE_PRODUCT_STAGE phase=matrix-admission '
                  'identityPresent=${identity?.subject?.isNotEmpty == true} '
                  'issuerMatches=${identity?.identityIssuer == config.issuerUrl.toString()} '
                  'organizationPresent=${identity?.organizationId?.isNotEmpty == true} '
                  'chatEnabled=${chat?.enabled == true} '
                  'policy=${chat?.policyState?.toString() ?? 'none'} '
                  'readiness=${chat?.readiness?.toString() ?? 'none'} '
                  'readGranted=${chat?.grantedCapabilities.contains('chat.read') == true}',
                );
              }
            } catch (diagnosticFailure) {
              _nativeProductDiagnostic(
                'NATIVE_PRODUCT_STAGE phase=matrix-admission-diagnostic-failed '
                'type=${diagnosticFailure.runtimeType}',
              );
            }
          }
          rethrow;
        } on RustMatrixCoreBridgeException catch (error) {
          _nativeProductDiagnostic(
            'NATIVE_PRODUCT_STAGE phase=matrix-open-failed '
            'type=RustMatrixCoreBridgeException code=${error.code}',
          );
          rethrow;
        } catch (error) {
          _nativeProductDiagnostic(
            'NATIVE_PRODUCT_STAGE phase=matrix-open-failed '
            'type=${error.runtimeType}',
          );
          rethrow;
        }
        expect(matrix.userId, startsWith('@'));
        expect(matrix.deviceId, isNotEmpty);
        expect(disposableStack, isTrue);
        final chat = container.read(chatRepositoryProvider);
        final marker =
            'Weave product E2E ${DateTime.now().toUtc().microsecondsSinceEpoch}';
        final room = await chat.createConversation(title: marker);
        expect(room.id, startsWith('!'));
        await chat.sendMessage(roomId: room.id, message: marker);
        await _requireBusinessRoomReadback(chat, room.id, marker);
        _nativeProductDiagnostic('NATIVE_PRODUCT_STAGE phase=matrix-passed');

        await tester.tap(
          find.descendant(
            of: find.byType(NavigationBar),
            matching: find.byIcon(Icons.folder_outlined),
          ),
        );
        await tester.pump(const Duration(seconds: 1));
        expect(find.byType(FilesScreen), findsOneWidget);
        await tester.tap(
          find.descendant(
            of: find.byType(NavigationBar),
            matching: find.byIcon(Icons.calendar_month_outlined),
          ),
        );
        await tester.pump(const Duration(seconds: 1));
        expect(find.byType(CalendarScreen), findsOneWidget);
        await tester.tap(
          find.descendant(
            of: find.byType(NavigationBar),
            matching: find.byIcon(Icons.chat_bubble_outline),
          ),
        );
        await tester.pump(const Duration(seconds: 1));
        expect(find.byType(ChatScreen), findsOneWidget);
        _nativeProductDiagnostic(
          'NATIVE_PRODUCT_STAGE phase=navigation-passed',
        );

        final refreshed = await container
            .read(authSessionRepositoryProvider)
            .refreshSession(
              AuthConfiguration(
                issuer: config.issuerUrl,
                clientId: config.clientId,
              ),
            );
        expect(refreshed.isAuthenticated, isTrue);
        _nativeProductDiagnostic('NATIVE_PRODUCT_STAGE phase=refresh-passed');
        await coordinator.disposePreservingCryptoState();
        final reopened = await coordinator.open(allowInteractiveSignIn: false);
        expect(reopened.userId, matrix.userId);
        expect(reopened.deviceId, matrix.deviceId);
        await _requireBusinessRoomReadback(chat, room.id, marker);
        expect((await files.listDirectory('/')).path, '/');
        expect(
          (await files.listDirectory(
            '/',
          )).entries.where((entry) => entry.name == fileName).single.id,
          uploaded.id,
        );
        expect((await calendar.loadScopes()).scopes, isNotEmpty);
        _nativeProductDiagnostic(
          'NATIVE_PRODUCT_STAGE phase=session-reopen-passed',
        );

        await coordinator.disposePreservingCryptoState();
        await tester.pumpWidget(const SizedBox.shrink());
        final restoredContainer = await _openWeaveSession(
          tester,
          config,
          requireRestoredSession: true,
        );
        final restoredFiles = restoredContainer.read(filesRepositoryProvider);
        final restoredCalendar = restoredContainer.read(
          calendarRepositoryProvider,
        );
        final restoredCoordinator = restoredContainer.read(
          matrixCryptoSessionCoordinatorProvider,
        );
        try {
          expect((await restoredFiles.listDirectory('/')).path, '/');
          expect(
            (await restoredFiles.listDirectory(
              '/',
            )).entries.where((entry) => entry.name == fileName).single.id,
            uploaded.id,
          );
          expect((await restoredCalendar.loadScopes()).scopes, isNotEmpty);
          final restoredMatrix = await restoredCoordinator.open(
            allowInteractiveSignIn: false,
          );
          expect(restoredMatrix.userId, matrix.userId);
          expect(restoredMatrix.deviceId, matrix.deviceId);
          await _requireBusinessRoomReadback(
            restoredContainer.read(chatRepositoryProvider),
            room.id,
            marker,
          );
          _nativeProductDiagnostic(
            'NATIVE_PRODUCT_STAGE phase=app-state-restored',
          );
          await (await _nativeCheckpoint(nativeTestRunId)).writeAsString(
            jsonEncode({
              'fileId': uploaded.id,
              'fileName': fileName,
              'roomId': room.id,
              'roomMessage': marker,
              'matrixUserId': matrix.userId,
              'matrixDeviceId': matrix.deviceId,
            }),
            flush: true,
          );
          await restoredContainer
              .read(secureStoreProvider)
              .write(_expiryProbeKey(nativeTestRunId), expiryProbeToken);
        } finally {
          await restoredCoordinator.disposePreservingCryptoState();
        }

        // Unmount the app before Flutter checks for leaked test-owned handles.
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pumpAndSettle();

        _preserveNativePreferencesForRestore = true;
        _nativeProductDiagnostic(
          'NATIVE_PRODUCT_INITIAL_RESULT status=passed login=single '
          'files=generated-upload-read calendar=generated-crud matrix=native '
          'businessRoomSendRead=true '
          'refresh=true sessionReopen=true appStateRecreated=true '
          'checkpointPrivate=true supportSafe=true',
        );
        debugPrint(
          'PHYSICAL_AUTH_SESSION_RESULT status=passed activation=system-browser '
          'pkce=true workspaceRestored=true refresh=true supportSafe=true',
        );
      } finally {
        await coordinator.disposePreservingCryptoState();
      }
    },
    skip:
        !productEnabled ||
        !nativeProductPhaseEnabled(
          simulator: simulatorProduct,
          selected: productPhase,
          journey: 'initial',
        ),
    timeout: const Timeout(Duration(minutes: 12)),
  );

  testWidgets(
    'native process restart restores Files Calendar Matrix then logout',
    (tester) async {
      final checkpointFile = await _nativeCheckpoint(nativeTestRunId);
      final checkpoint =
          jsonDecode(await checkpointFile.readAsString())
              as Map<String, dynamic>;
      await checkpointFile.delete();
      final container = await _openWeaveSession(
        tester,
        config,
        requireRestoredSession: true,
      );
      final files = container.read(filesRepositoryProvider);
      final calendar = container.read(calendarRepositoryProvider);
      final coordinator = container.read(
        matrixCryptoSessionCoordinatorProvider,
      );
      try {
        final file = (await files.listDirectory('/')).entries
            .where((entry) => entry.name == checkpoint['fileName'])
            .single;
        expect(file.id, checkpoint['fileId']);
        expect(
          (await (files as FilesExportRepository).downloadFile(file)).bytes,
          orderedEquals(
            utf8.encode('Weave native Files acceptance ${file.name}'),
          ),
        );
        expect((await calendar.loadScopes()).scopes, isNotEmpty);
        final matrix = await coordinator.open(allowInteractiveSignIn: false);
        expect(matrix.userId, checkpoint['matrixUserId']);
        expect(matrix.deviceId, checkpoint['matrixDeviceId']);
        await _requireBusinessRoomReadback(
          container.read(chatRepositoryProvider),
          checkpoint['roomId'] as String,
          checkpoint['roomMessage'] as String,
        );
        _nativeProductDiagnostic(
          'NATIVE_PRODUCT_STAGE phase=process-restart-restored',
        );

        // This is the real bearer issued by the first process's native PKCE
        // login. It is used only for denial probes, never to establish a session.
        final secureStore = container.read(secureStoreProvider);
        final expiryProbeToken = await secureStore.read(
          _expiryProbeKey(nativeTestRunId),
        );
        await secureStore.delete(_expiryProbeKey(nativeTestRunId));
        expect(expiryProbeToken, isNotEmpty);
        await _waitForActualBearerExpiry(expiryProbeToken!);

        // Initiate a real membership change at the Matrix facade, then verify
        // its effect through the native Rust SDK rather than a second HTTP
        // read. This proves own-room leave, not admin Space revocation.
        final roomId = checkpoint['roomId'] as String;
        final authRepository = container.read(authSessionRepositoryProvider);
        final authConfiguration = AuthConfiguration(
          issuer: config.issuerUrl,
          clientId: config.clientId,
        );
        final memberSession = await authRepository.restoreSession(
          authConfiguration,
        );
        expect(memberSession.isAuthenticated, isTrue);
        final earlierToken = memberSession.session!.accessToken;
        final refreshed = await authRepository.refreshSession(
          authConfiguration,
        );
        expect(refreshed.isAuthenticated, isTrue);
        final memberToken = refreshed.session!.accessToken;
        expect(
          memberToken != earlierToken,
          isTrue,
          reason: 'AppAuth refresh must issue a new bearer.',
        );
        final refreshedMatrix = await coordinator.open(
          allowInteractiveSignIn: false,
        );
        expect(refreshedMatrix.deviceId, matrix.deviceId);
        final deviceProof = await container
            .read(secureStoreProvider)
            .read('matrix_member_device_proof_v1_${matrix.profileKey}');
        expect(deviceProof, isNotEmpty);
        await _requireExpiredBearerDenied(
          container,
          config,
          expiryProbeToken,
          memberToken,
          matrix.deviceId,
          deviceProof!,
        );
        _nativeProductDiagnostic(
          'NATIVE_PRODUCT_STAGE phase=expired-bearer-denied',
        );
        final leave = await container
            .read(weaveApiHttpClientProvider)
            .post(
              config.matrixHomeserverUrl.resolve(
                '/_matrix/client/v3/rooms/${Uri.encodeComponent(roomId)}/leave',
              ),
              headers: <String, String>{
                'Authorization': 'Bearer $memberToken',
                'x-weave-matrix-device-id': matrix.deviceId,
                'x-weave-matrix-device-proof': deviceProof,
                'Content-Type': 'application/json',
              },
              body: '{}',
            );
        expect(leave.statusCode, 200);
        final chat = container.read(chatRepositoryProvider);
        expect(
          (await chat.loadConversations()).where((room) => room.id == roomId),
          isEmpty,
        );
        await expectLater(
          nativeRoomDenialProbe(chat.loadRoomTimeline(roomId)),
          throwsA(
            isA<ChatFailure>().having(
              (failure) => failure.type,
              'type',
              ChatFailureType.sessionRequired,
            ),
          ),
        );
        _nativeProductDiagnostic(
          'NATIVE_PRODUCT_STAGE phase=own-room-leave-denied',
        );

        await container.read(authFlowControllerProvider.notifier).signOut();
        await _waitFor(
          tester,
          const ValueKey('weave.auth.sign-in'),
          timeout: const Duration(minutes: 1),
        );
        expect(
          (await container
                  .read(authSessionRepositoryProvider)
                  .restoreSession(
                    AuthConfiguration(
                      issuer: config.issuerUrl,
                      clientId: config.clientId,
                    ),
                  ))
              .isAuthenticated,
          isFalse,
        );
        await expectLater(
          coordinator.open(allowInteractiveSignIn: false),
          throwsA(anything),
        );
        await expectLater(
          container
              .read(chatRepositoryProvider)
              .loadRoomTimeline(checkpoint['roomId'] as String),
          throwsA(
            isA<ChatFailure>().having(
              (failure) => failure.type,
              'type',
              ChatFailureType.sessionRequired,
            ),
          ),
        );
        await expectLater(files.listDirectory('/'), throwsA(anything));
        await expectLater(calendar.loadScopes(), throwsA(anything));
        expect(
          _bearerExpiry(memberToken).isAfter(DateTime.now().toUtc()),
          isTrue,
        );
        final retainedUserFiles = user_api.FilesApi(
          weaveUserApiClient(
            apiBaseUrl: config.backendApiBaseUrl,
            accessToken: memberToken,
            httpClient: container.read(weaveApiHttpClientProvider),
          ),
        );
        await expectLater(
          retainedUserFiles.getFilesReadiness(),
          throwsA(
            isA<user_api.ApiException>().having(
              (e) => e.code,
              'HTTP status',
              401,
            ),
          ),
        );
        for (final token in <String>[earlierToken, memberToken]) {
          final revokedMatrix = await container
              .read(weaveApiHttpClientProvider)
              .get(
                config.matrixHomeserverUrl.resolve(
                  '/_matrix/client/v3/account/whoami',
                ),
                headers: <String, String>{
                  'Authorization': 'Bearer $token',
                  'x-weave-matrix-device-id': matrix.deviceId,
                  'x-weave-matrix-device-proof': deviceProof,
                },
              );
          expect(revokedMatrix.statusCode, 401);
          expect(jsonDecode(revokedMatrix.body)['errcode'], 'M_UNKNOWN_TOKEN');
        }
        _nativeProductDiagnostic(
          'NATIVE_PRODUCT_STAGE phase=logout-denial-passed',
        );
      } finally {
        await coordinator.disposePreservingCryptoState();
      }
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpAndSettle();
      _nativeProductDiagnostic(
        'NATIVE_PRODUCT_SIGN_IN_RESULT status=passed login=single '
        'files=generated-upload-read calendar=generated-crud matrix=native '
        'businessRoomSendRead=true refresh=true sessionReopen=true '
        'processRestart=true expiredBearerDenied=true userLogoutDenied=true logoutDenied=true '
        'supportSafe=true',
      );
    },
    skip:
        !productEnabled ||
        !nativeProductPhaseEnabled(
          simulator: simulatorProduct,
          selected: productPhase,
          journey: 'restore',
        ),
    timeout: const Timeout(Duration(minutes: 8)),
  );

  testWidgets(
    'one Weave login opens and restores the native Matrix member session',
    (tester) async {
      final container = await _openWeaveSession(tester, config);
      final coordinator = container.read(
        matrixCryptoSessionCoordinatorProvider,
      );
      try {
        final first = await coordinator.open(allowInteractiveSignIn: false);
        expect(first.userId, startsWith('@'));
        expect(first.deviceId, isNotEmpty);

        await coordinator.disposePreservingCryptoState();
        final refreshed = await container
            .read(authSessionRepositoryProvider)
            .refreshSession(
              AuthConfiguration(
                issuer: config.issuerUrl,
                clientId: config.clientId,
              ),
            );
        expect(refreshed.isAuthenticated, isTrue);
        final restored = await coordinator.open(allowInteractiveSignIn: false);
        expect(restored.userId, first.userId);
        expect(restored.deviceId, first.deviceId);
        debugPrint(
          'PHYSICAL_MATRIX_MEMBER_RESULT status=passed login=single '
          'nativeSdk=true sync=true tokenRefresh=true deviceRetained=true '
          'supportSafe=true',
        );
      } finally {
        await coordinator.disposePreservingCryptoState();
      }
    },
    skip: !matrixEnabled,
    timeout: const Timeout(Duration(minutes: 9)),
  );

  testWidgets(
    'one Weave login sends and reads a business-room Matrix event on a disposable stack',
    (tester) async {
      final container = await _openWeaveSession(tester, config);
      final coordinator = container.read(
        matrixCryptoSessionCoordinatorProvider,
      );
      final chat = container.read(chatRepositoryProvider);
      final marker =
          'Weave native Matrix E2E ${DateTime.now().toUtc().microsecondsSinceEpoch}';
      try {
        final first = await coordinator.open(allowInteractiveSignIn: false);
        final room = await chat.createConversation(title: marker);
        expect(room.id, startsWith('!'));
        await chat.sendMessage(roomId: room.id, message: marker);

        await _requireBusinessRoomReadback(chat, room.id, marker);
        await coordinator.disposePreservingCryptoState();
        final refreshed = await container
            .read(authSessionRepositoryProvider)
            .refreshSession(
              AuthConfiguration(
                issuer: config.issuerUrl,
                clientId: config.clientId,
              ),
            );
        expect(refreshed.isAuthenticated, isTrue);
        final restored = await coordinator.open(allowInteractiveSignIn: false);
        expect(restored.userId, first.userId);
        expect(restored.deviceId, first.deviceId);
        await _requireBusinessRoomReadback(chat, room.id, marker);
        debugPrint(
          'NATIVE_MATRIX_MESSAGE_RESULT status=passed login=single '
          'nativeSdk=true businessRoomSendRead=true tokenRefresh=true '
          'deviceRetained=true supportSafe=true',
        );
      } finally {
        await coordinator.disposePreservingCryptoState();
      }
    },
    skip: !matrixEnabled || !disposableMessageEnabled,
    timeout: const Timeout(Duration(minutes: 12)),
  );

  testWidgets(
    'two isolated native devices recover backed-up room history and reject revocation',
    (tester) async {
      final container = await _openWeaveSession(tester, config);
      final authConfiguration = AuthConfiguration(
        issuer: config.issuerUrl,
        clientId: config.clientId,
      );
      final firstAuth = await container
          .read(authSessionRepositoryProvider)
          .restoreSession(authConfiguration);
      expect(firstAuth.isAuthenticated, isTrue);
      final firstSession = firstAuth.session!;

      final firstCoordinator = container.read(
        matrixCryptoSessionCoordinatorProvider,
      );
      final firstChat = container.read(chatRepositoryProvider);
      const bridge = RustMatrixCoreBridge();
      final temporaryStore = await Directory.systemTemp.createTemp(
        'weave-matrix-second-device-',
      );
      final secondSecureStore = _EphemeralSecureStore();
      final secondAuth = OidcAuthSessionRepository(
        secureStore: secondSecureStore,
        oidcClient: _FreshBrowserOidcClient(),
      );
      final secondCoordinator = WeaveMemberMatrixSessionCoordinator(
        serverConfigurationRepository: container.read(
          serverConfigurationRepositoryProvider,
        ),
        authSessionRepository: secondAuth,
        matrixDeviceIdentityRepository: MatrixDeviceIdentityRepository(
          secureStore: secondSecureStore,
        ),
        matrixSessionAccess: GeneratedMatrixSessionAccess(
          httpClient: container.read(weaveApiHttpClientProvider),
        ),
        matrixHttpClient: container.read(weaveApiHttpClientProvider),
        secureStore: secondSecureStore,
        storeRootLoader: () async => temporaryStore,
      );
      final secondChat = NativeMatrixChatRepository(
        matrixCryptoSessionCoordinator: secondCoordinator,
      );
      final marker =
          'Weave native backup ${DateTime.now().toUtc().microsecondsSinceEpoch}';
      try {
        final first = await firstCoordinator.open(
          allowInteractiveSignIn: false,
        );
        final room = await bridge.createEncryptedRoom(
          profileKey: first.profileKey,
          title: marker,
        );
        expect(room.encrypted, isTrue);
        await firstChat.sendMessage(roomId: room.roomId, message: marker);
        final firstEventId = await _waitForEncryptedMessage(
          firstChat,
          room.roomId,
          marker,
        );
        // Bootstrap after the send so the SDK waits for this room key to be
        // uploaded before the second device attempts recovery.
        final recoveryKey = await bridge.bootstrapRecovery(
          profileKey: first.profileKey,
        );
        expect(recoveryKey, isNotEmpty);

        // This second AppAuth login is a separate device session. A reused
        // Keycloak sid cannot be bound to another Matrix device by the server.
        final secondAuthState = await secondAuth.signIn(authConfiguration);
        expect(secondAuthState.isAuthenticated, isTrue);
        final secondSession = secondAuthState.session!;
        expect(
          _idTokenClaim(secondSession, 'sub') ==
              _idTokenClaim(firstSession, 'sub'),
          isTrue,
          reason: 'Both device sessions must belong to the same member.',
        );
        final firstSessionId = _oidcSessionId(firstSession);
        final secondSessionId = _oidcSessionId(secondSession);
        expect(firstSessionId, isNotEmpty);
        expect(
          secondSessionId != firstSessionId,
          isTrue,
          reason:
              'The identity provider reused the first device session. Use an '
              'independent OIDC browser session for the second device.',
        );

        final second = await secondCoordinator.open(
          allowInteractiveSignIn: false,
        );
        expect(second.userId, first.userId);
        expect(second.deviceId, isNot(first.deviceId));
        expect(second.profileKey, isNot(first.profileKey));
        await bridge.recover(
          profileKey: second.profileKey,
          recoveryKeyOrPassphrase: recoveryKey,
        );
        expect(
          (await bridge.loadSecurityState(
            profileKey: second.profileKey,
          )).recoveryState,
          'enabled',
        );
        expect(
          await _waitForEncryptedMessage(secondChat, room.roomId, marker),
          firstEventId,
        );

        final secondProof = await secondSecureStore.read(
          'matrix_member_device_proof_v1_${second.profileKey}',
        );
        expect(secondProof, isNotNull);
        final matrixHeaders = <String, String>{
          'Authorization': 'Bearer ${secondSession.accessToken}',
          'x-weave-matrix-device-id': second.deviceId,
          'x-weave-matrix-device-proof': secondProof!,
        };
        final matrixBase = config.matrixHomeserverUrl;
        final revoke = await container
            .read(weaveApiHttpClientProvider)
            .delete(
              matrixBase.resolve(
                '/_matrix/client/v3/devices/${Uri.encodeComponent(second.deviceId)}',
              ),
              headers: matrixHeaders,
            );
        expect(revoke.statusCode, 200);
        final denied = await container
            .read(weaveApiHttpClientProvider)
            .get(
              matrixBase.resolve('/_matrix/client/v3/account/whoami'),
              headers: matrixHeaders,
            );
        expect(denied.statusCode, 401);
        expect(jsonDecode(denied.body)['errcode'], 'M_UNKNOWN_TOKEN');
        await expectLater(
          secondCoordinator.open(allowInteractiveSignIn: false),
          throwsA(anything),
        );
        debugPrint(
          'NATIVE_MATRIX_TWO_DEVICE_RESULT status=passed '
          'separateOidcSessions=true nativeSdk=true backupRecovery=true '
          'sameEventReadback=true revokedDeviceDenied=true supportSafe=true',
        );
      } finally {
        await secondCoordinator.disposePreservingCryptoState();
        await secondAuth.clearLocalSession();
        secondSecureStore.clear();
        await firstCoordinator.disposePreservingCryptoState();
        if (await temporaryStore.exists()) {
          await temporaryStore.delete(recursive: true);
        }
      }
    },
    skip:
        !matrixEnabled ||
        !disposableMessageEnabled ||
        !twoDeviceRecoveryEnabled ||
        !disposableStack,
    timeout: const Timeout(Duration(minutes: 18)),
  );
}

Future<File> _nativeCheckpoint(String runId) async {
  if (!RegExp(r'^[A-Za-z0-9-]+$').hasMatch(runId)) {
    throw StateError('Invalid isolated native test run ID.');
  }
  final support = await getApplicationSupportDirectory();
  await support.create(recursive: true);
  return File('${support.path}/weave-native-acceptance-$runId.json');
}

bool _simulatorDriverAttached = false;

const _simulatorBrowserDriver = bool.fromEnvironment(
  'WEAVE_NATIVE_EXPECT_SIMULATOR',
);

bool _usesSimulatorDriver(String runId) =>
    Platform.isIOS &&
    _simulatorBrowserDriver &&
    const bool.fromEnvironment('WEAVE_DEVICE_DISPOSABLE_STACK') &&
    runId.isNotEmpty;

Future<void> _waitForSimulatorDriver(WidgetTester tester, String runId) async {
  if (!_usesSimulatorDriver(runId) || _simulatorDriverAttached) {
    return;
  }
  final checkpoint = await _nativeCheckpoint(runId);
  final barrier = File(
    '${checkpoint.parent.path}/weave-native-driver-$runId.ready',
  );
  final deadline = DateTime.now().add(const Duration(minutes: 2));
  while (DateTime.now().isBefore(deadline)) {
    if (await barrier.exists()) {
      await barrier.delete();
      _simulatorDriverAttached = true;
      _nativeProductDiagnostic('NATIVE_PRODUCT_STAGE phase=driver-attached');
      return;
    }
    await tester.pump(const Duration(milliseconds: 250));
    await Future<void>.delayed(const Duration(milliseconds: 250));
  }
  fail('The isolated simulator driver did not attach before authentication.');
}

String _expiryProbeKey(String runId) => 'native_acceptance_expiry_probe_$runId';

DateTime _bearerExpiry(String token) {
  try {
    final parts = token.split('.');
    if (parts.length != 3) throw const FormatException();
    final claims =
        jsonDecode(utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))))
            as Map<String, dynamic>;
    final expiry = claims['exp'];
    if (expiry is! int) throw const FormatException();
    return DateTime.fromMillisecondsSinceEpoch(expiry * 1000, isUtc: true);
  } catch (_) {
    throw StateError('Native bearer expiry is unavailable.');
  }
}

Future<void> _waitForActualBearerExpiry(String token) async {
  // Production Spring JWT validators permit 60 seconds of clock skew.
  final expiredAt = _bearerExpiry(token).add(const Duration(seconds: 65));
  expect(
    expiredAt.isBefore(DateTime.now().toUtc().add(const Duration(minutes: 6))),
    isTrue,
    reason: 'The disposable native expiry proof must finish within its bound.',
  );
  while (DateTime.now().toUtc().isBefore(expiredAt)) {
    await Future<void>.delayed(const Duration(seconds: 1));
  }
}

Future<void> _requireExpiredBearerDenied(
  ProviderContainer container,
  TestConfig config,
  String expiredToken,
  String freshToken,
  String deviceId,
  String deviceProof,
) async {
  final http = container.read(weaveApiHttpClientProvider);
  user_api.FilesApi filesFor(String token) => user_api.FilesApi(
    weaveUserApiClient(
      apiBaseUrl: config.backendApiBaseUrl,
      accessToken: token,
      httpClient: http,
    ),
  );
  expect((await filesFor(freshToken).getFilesReadiness())?.enabled, isTrue);
  await expectLater(
    filesFor(expiredToken).getFilesReadiness(),
    throwsA(
      isA<user_api.ApiException>().having((e) => e.code, 'HTTP status', 401),
    ),
  );
  for (final token in <String>[freshToken, expiredToken]) {
    final identity = await http.get(
      config.matrixHomeserverUrl.resolve('/_matrix/client/v3/account/whoami'),
      headers: <String, String>{
        'Authorization': 'Bearer $token',
        'x-weave-matrix-device-id': deviceId,
        'x-weave-matrix-device-proof': deviceProof,
      },
    );
    if (token == freshToken) {
      expect(identity.statusCode, 200);
      expect(_bearerExpiry(token).isAfter(DateTime.now().toUtc()), isTrue);
    } else {
      expect(identity.statusCode, 401);
      expect(jsonDecode(identity.body)['errcode'], 'M_UNKNOWN_TOKEN');
    }
  }
}

// Shared independent denial oracle; callers supply only their actual resolved
// canonical configuration and native session/device, never an injected session.
Future<void> requireActualNativeBearerExpiry(String token) =>
    _waitForActualBearerExpiry(token);

Future<void> requireNativeExpiredBearerDenied(
  ProviderContainer container,
  TestConfig config,
  String expiredToken,
  String freshToken,
  String deviceId,
  String deviceProof,
) => _requireExpiredBearerDenied(
  container,
  config,
  expiredToken,
  freshToken,
  deviceId,
  deviceProof,
);

Future<String> _waitForEncryptedMessage(
  ChatRepository chat,
  String roomId,
  String marker,
) async {
  for (var attempt = 0; attempt < 30; attempt++) {
    final timeline = await chat.loadRoomTimeline(roomId);
    final matching = timeline.messages
        .where((message) => message.text == marker)
        .toList(growable: false);
    if (matching.length == 1) {
      expect(matching.single.id, startsWith(r'$'));
      return matching.single.id;
    }
    await Future<void>.delayed(const Duration(seconds: 1));
  }
  fail('Native Matrix encrypted message readback did not arrive.');
}

String _idTokenClaim(AuthSession session, String claim) {
  final parts = session.idToken?.split('.') ?? const <String>[];
  if (parts.length != 3) throw StateError('OIDC ID token is unavailable.');
  final payload = jsonDecode(
    utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))),
  );
  if (payload is! Map || payload[claim] is! String) {
    throw StateError('OIDC $claim claim is unavailable.');
  }
  return payload[claim] as String;
}

String _oidcSessionId(AuthSession session) {
  try {
    return _idTokenClaim(session, 'sid');
  } on StateError {
    return _idTokenClaim(session, 'session_state');
  }
}

class _EphemeralSecureStore implements SecureStore {
  final _values = <String, String>{};

  void clear() => _values.clear();

  @override
  Future<String?> read(String key) async => _values[key];

  @override
  Future<void> write(String key, String value) async {
    _values[key] = value;
  }

  @override
  Future<void> delete(String key) async {
    _values.remove(key);
  }
}

class _FreshBrowserOidcClient implements OidcClient {
  final _appAuth = const FlutterAppAuth();
  final _normalClient = FlutterAppAuthOidcClient();

  @override
  Future<OidcTokenBundle> authorizeAndExchangeCode(
    AuthConfiguration configuration,
  ) async {
    final random = Random.secure();
    final nonceBytes = List<int>.generate(32, (_) => random.nextInt(256));
    final response = await _appAuth.authorizeAndExchangeCode(
      AuthorizationTokenRequest(
        configuration.clientId,
        oidcRedirectUri,
        issuer: configuration.issuer.toString(),
        scopes: oidcDefaultScopes,
        nonce: base64UrlEncode(nonceBytes).replaceAll('=', ''),
        promptValues: const <String>['login'],
        externalUserAgent:
            ExternalUserAgent.ephemeralAsWebAuthenticationSession,
      ),
    );
    final accessToken = response.accessToken;
    if (accessToken == null || accessToken.isEmpty) {
      throw StateError('The second OIDC device session was not established.');
    }
    return OidcTokenBundle(
      accessToken: accessToken,
      refreshToken: response.refreshToken,
      idToken: response.idToken,
      expiresAt: response.accessTokenExpirationDateTime,
      tokenType: response.tokenType,
      scopes: response.scopes ?? const <String>[],
    );
  }

  @override
  Future<OidcTokenBundle> refresh(
    AuthConfiguration configuration, {
    required String refreshToken,
  }) => _normalClient.refresh(configuration, refreshToken: refreshToken);

  @override
  Future<void> endSession(
    AuthConfiguration configuration, {
    required String idTokenHint,
  }) => _normalClient.endSession(configuration, idTokenHint: idTokenHint);
}

Finder _nativeSignIn(WidgetTester tester) {
  final handoff = find.byType(MemberHandoffScreen);
  if (handoff.evaluate().isEmpty) {
    return find.byKey(const ValueKey('weave.auth.sign-in'));
  }
  final copy = AppLocalizations.of(tester.element(handoff));
  return find.descendant(
    of: find.descendant(of: handoff, matching: find.byType(SuccessState)),
    matching: find.byWidgetPredicate(
      (widget) =>
          widget is AccessibleButton &&
          widget.onPressed != null &&
          widget.semanticLabel == copy.signInButton,
    ),
  );
}

Future<void> prepareNativeCanonicalOrganization(
  WidgetTester tester,
  Uri organizationOrigin, {
  required DateTime deadline,
  required bool restoring,
}) async {
  var entered = false;
  while (DateTime.now().isBefore(deadline)) {
    final home = find.byKey(const ValueKey('weave.workspace.home'));
    if (home.evaluate().length == 1) return;
    final entry = find.byType(OrganizationAccessScreen);
    if (restoring) {
      expect(
        entry,
        findsNothing,
        reason: 'Restore must reuse the persisted canonical organization.',
      );
      expect(
        _nativeSignIn(tester),
        findsNothing,
        reason: 'Restore must not start another browser sign-in.',
      );
    } else {
      if (_nativeSignIn(tester).evaluate().length == 1) return;
      if (!entered && entry.evaluate().length == 1) {
        final copy = AppLocalizations.of(tester.element(entry));
        final field = find.descendant(
          of: entry,
          matching: find.byType(TextField),
        );
        expect(field, findsOneWidget);
        await tester.enterText(field, organizationOrigin.toString());
        final next = find.widgetWithText(
          AccessibleButton,
          copy.setupOrganizationContinueButton,
        );
        expect(next, findsOneWidget);
        expect(tester.widget<AccessibleButton>(next).onPressed, isNotNull);
        await tester.ensureVisible(next);
        await tester.pump();
        expect(DateTime.now().isBefore(deadline), isTrue);
        await tester.tap(next);
        entered = true;
      }
    }
    await tester.pump(const Duration(milliseconds: 100));
  }
  fail('Canonical organization onboarding did not reach the required state.');
}

bool _preserveNativePreferencesForRestore = false;

Future<ChatRoomTimeline> nativeRoomDenialProbe(
  Future<ChatRoomTimeline> read,
) async {
  try {
    return await read;
  } on ChatFailure catch (failure) {
    const allowed = {
      'M_FORBIDDEN',
      'M_NOT_FOUND',
      'M_WEAVE_CHAT_JOIN_REQUIRED',
      'M_UNKNOWN_TOKEN',
      'M_MISSING_TOKEN',
      'M_WEAVE_MATRIX_SESSION_EXPIRED',
      'M_WEAVE_E2EE_SYNC',
      'M_WEAVE_E2EE_TIMELINE',
    };
    final cause = failure.cause;
    final code =
        cause is RustMatrixCoreBridgeException && allowed.contains(cause.code)
        ? cause.code
        : 'unavailable';
    safelyReportNativeBootstrapFailure(
      report: () => _nativeProductDiagnostic(
        'NATIVE_PRODUCT_STAGE phase=room-denial-observed type=${failure.type.name} causeCode=$code supportSafe=true',
      ),
      unavailable: () => _nativeProductDiagnostic(
        'NATIVE_PRODUCT_STAGE phase=room-denial-diagnostic-unavailable',
      ),
    );
    rethrow;
  }
}

void expectNativeMatrixEndpoint(Uri? actual, Uri expected) {
  expect(actual, isNotNull);
  final actualOrigin = TestConfig.parseOrganizationOrigin(actual!.toString());
  final expectedOrigin = TestConfig.parseOrganizationOrigin(
    expected.toString(),
  );
  expect(actualOrigin.origin, expectedOrigin.origin);
}

Future<ProviderContainer> _openWeaveSession(
  WidgetTester tester,
  TestConfig config, {
  bool requireFreshSignIn = false,
  bool requireRestoredSession = false,
}) async {
  const nativeTestRunId = String.fromEnvironment('WEAVE_NATIVE_TEST_RUN_ID');
  final origin = config.organizationOrigin;
  if (origin == null || nativeTestRunId.isEmpty) {
    fail('Native acceptance requires its owned canonical organization input.');
  }
  final preferences = NativeAcceptancePreferencesStore.production(
    nativeTestRunId,
  );
  addTearDown(() async {
    if (!_preserveNativePreferencesForRestore) {
      await preferences.clearOwnedKeys();
    }
  });
  final readinessDeadline = DateTime.now().add(const Duration(minutes: 1));
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        if (nativeTestRunId.isNotEmpty)
          secureStoreProvider.overrideWithValue(
            FlutterSecureStore(
              storage: const FlutterSecureStorage(
                mOptions: MacOsOptions(
                  accountName: 'weave-native-acceptance-$nativeTestRunId',
                  accessibility: KeychainAccessibility.first_unlock_this_device,
                ),
              ),
            ),
          ),
        preferencesStoreProvider.overrideWithValue(preferences),
      ],
      child: const WeaveApp(),
    ),
  );
  if (requireFreshSignIn) {
    final repository = ProviderScope.containerOf(
      tester.element(find.byType(WeaveApp)),
    ).read(serverConfigurationRepositoryProvider);
    expect(
      await repository.loadConfiguration(),
      isNull,
      reason:
          'Fresh native acceptance must begin without an owned canonical profile.',
    );
  }
  try {
    await prepareNativeCanonicalOrganization(
      tester,
      origin,
      deadline: readinessDeadline,
      restoring: requireRestoredSession,
    );
  } catch (_) {
    safelyReportNativeBootstrapFailure(
      report: () => _reportNativeBootstrapFailure(tester, config),
      unavailable: () => _nativeProductDiagnostic(
        'NATIVE_PRODUCT_STAGE phase=shell-bootstrap-diagnostic-unavailable',
      ),
    );
    rethrow;
  }
  if (requireFreshSignIn) {
    expect(
      _nativeSignIn(tester),
      findsOneWidget,
      reason:
          'Install the test app with fresh local state before this journey.',
    );
  }
  if (requireRestoredSession) {
    expect(
      find.byKey(const ValueKey('weave.workspace.home')),
      findsOneWidget,
      reason:
          'The member session must restore without another browser sign-in.',
    );
  }
  final signIn = _nativeSignIn(tester);
  final needsSignIn = signIn.evaluate().isNotEmpty;
  final authContainer = ProviderScope.containerOf(
    tester.element(find.byType(WeaveApp)),
  );
  final selected = await ProviderScope.containerOf(
    tester.element(find.byType(WeaveApp)),
  ).read(serverConfigurationRepositoryProvider).loadConfiguration();
  expect(selected?.organizationOrigin?.origin, origin.origin);
  expect(selected?.oidcIssuerUrl, config.issuerUrl);
  expect(selected?.oidcClientRegistration.clientId, config.clientId);
  expectNativeMatrixEndpoint(
    selected?.serviceEndpoints.matrixHomeserverUrl,
    config.matrixHomeserverUrl,
  );
  expect(
    selected?.serviceEndpoints.backendApiBaseUrl,
    config.backendApiBaseUrl,
  );
  if (needsSignIn) {
    await tester.ensureVisible(signIn);
    await tester.pump();
    expect(tester.widget<AccessibleButton>(signIn).onPressed, isNotNull);
  }
  _nativeProductDiagnostic(
    'NATIVE_PRODUCT_STAGE phase=shell-ready issuerPort=${config.issuerUrl.port}',
  );
  await _waitForSimulatorDriver(tester, nativeTestRunId);
  if (needsSignIn) {
    // Simulator qualification delegates the real accessible control tap to
    // XCTest after attaching to this existing process. Physical HIL and macOS
    // retain their existing interaction; production AppAuth remains unchanged.
    if (!_usesSimulatorDriver(nativeTestRunId)) {
      await tester.tap(signIn);
    }
    await tester.pump();
    final started = authContainer.read(authFlowControllerProvider);
    _nativeProductDiagnostic(
      'NATIVE_PRODUCT_STAGE phase=sign-in-tapped '
      'busy=${started.isBusy} failure=${started.failure?.type.name ?? 'none'}',
    );
    await _waitForWorkspaceAfterSignIn(tester, authContainer);
  } else {
    await _waitFor(
      tester,
      const ValueKey('weave.workspace.home'),
      timeout: const Duration(minutes: 5),
    );
  }
  // The production FlutterAppAuthOidcClient owns the system-browser transition.
  // The native acceptance runner must complete the IdP interaction without
  // injecting tokens into this app or replacing the AppAuth callback.
  _nativeProductDiagnostic('NATIVE_PRODUCT_STAGE phase=workspace-ready');
  return ProviderScope.containerOf(tester.element(find.byType(WeaveApp)));
}

Future<void> _waitForWorkspaceAfterSignIn(
  WidgetTester tester,
  ProviderContainer container,
) async {
  final deadline = DateTime.now().add(const Duration(minutes: 5));
  var pendingReported = false;
  var requestReported = false;
  while (DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(seconds: 1));
    if (find
        .byKey(const ValueKey('weave.workspace.home'))
        .evaluate()
        .isNotEmpty) {
      return;
    }
    final state = container.read(authFlowControllerProvider);
    if (state.isBusy && !requestReported) {
      _nativeProductDiagnostic(
        'NATIVE_PRODUCT_STAGE phase=appauth-requested busy=true',
      );
      requestReported = true;
    }
    final failure = state.failure;
    if (failure != null) {
      emitNativeAuthFailureDiagnostic(
        failure,
        simulatorDriver: _usesSimulatorDriver(
          const String.fromEnvironment('WEAVE_NATIVE_TEST_RUN_ID'),
        ),
      );
      fail('Native OIDC sign-in failed (${failure.type.name}).');
    }
    if (!pendingReported &&
        DateTime.now().isAfter(
          deadline.subtract(const Duration(minutes: 4, seconds: 45)),
        )) {
      _nativeProductDiagnostic(
        'NATIVE_PRODUCT_STAGE phase=appauth-pending busy=${state.isBusy} '
        'signInVisible=${find.byKey(const ValueKey('weave.auth.sign-in')).evaluate().isNotEmpty} '
        'bootstrap=${container.read(appBootstrapProvider).maybeWhen(data: (value) => value.phase.name, orElse: () => 'pending')}',
      );
      pendingReported = true;
    }
    if (!requestReported &&
        !state.isBusy &&
        failure == null &&
        find
            .byKey(const ValueKey('weave.auth.sign-in'))
            .evaluate()
            .isNotEmpty &&
        DateTime.now().isAfter(
          deadline.subtract(const Duration(minutes: 4, seconds: 30)),
        )) {
      fail('The native sign-in control did not start OIDC authentication.');
    }
  }
  fail('Native OIDC sign-in did not establish the workspace.');
}

Future<void> _requireBusinessRoomReadback(
  ChatRepository chat,
  String roomId,
  String marker,
) async {
  for (var attempt = 0; attempt < 20; attempt++) {
    final timeline = await chat.loadRoomTimeline(roomId);
    final matching = timeline.messages
        .where((message) => message.text == marker)
        .toList(growable: false);
    if (matching.length == 1) {
      expect(matching.single.isMine, isTrue);
      expect(matching.single.id, startsWith(r'$'));
      return;
    }
    await Future<void>.delayed(const Duration(seconds: 1));
  }
  fail('Native Matrix business-room message readback did not arrive.');
}

Future<void> _waitFor(
  WidgetTester tester,
  Key key, {
  required Duration timeout,
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(seconds: 1));
    if (find.byKey(key).evaluate().isNotEmpty) {
      return;
    }
  }
  fail('Timed out waiting for widget key $key.');
}
