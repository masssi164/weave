import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:crypto/crypto.dart';
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
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/widgets/success_state.dart';
import 'package:weave/features/onboarding/domain/entities/member_auth_onboarding_state.dart';
import 'package:weave/features/onboarding/domain/use_cases/discover_organization_access.dart';
import 'package:weave/features/onboarding/presentation/member_handoff_screen.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
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

Future<void> _writePrivateJson(File destination, Object value) async {
  final staging = File('${destination.path}.tmp');
  if (await FileSystemEntity.isLink(destination.path) ||
      await FileSystemEntity.isLink(staging.path)) {
    throw TestFailure('Unsafe private actor proof path.');
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
  }
  await tester.tap(finder);
  await tester.pump(const Duration(milliseconds: 250));
}

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

void main() {
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
      final scenarios = <String, Map<String, dynamic>>{
        if (prior != null)
          for (final entry
              in (prior['scenarios'] as Map<String, dynamic>).entries)
            entry.key: Map<String, dynamic>.from(entry.value as Map),
        'authentication': {'status': 'passed', 'evidence': 'flutter-ui'},
      };
      await expectLater(tester, meetsGuideline(iOSTapTargetGuideline));
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
      scenarios['accessibility'] = {
        'status': 'passed',
        'evidence': 'flutter-ui',
      };
      await _tap(
        tester,
        find.descendant(
          of: find.byType(NavigationBar),
          matching: find.byIcon(Icons.chat_bubble_outline),
        ),
      );
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
        'identityDigest': sha256.convert(utf8.encode(subject)).toString(),
        'matrixDeviceDigest': sha256.convert(utf8.encode(device!)).toString(),
        'storageDigest': fixture['storageDigest'],
        'scenarios': scenarios,
        'resources': resources,
        'recovery': recovery,
        if (prior != null) 'encryption': prior['encryption'],
      };
      await _writePrivateJson(resultFile, result);
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
            await _writePrivateJson(resultFile, result);
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
            await _tap(tester, find.text(conversation));
            await _wait(
              tester,
              () =>
                  find
                      .byKey(const Key('chat-accept-invitation-button'))
                      .evaluate()
                      .length ==
                  1,
              const Duration(seconds: 60),
            );
            await _tap(
              tester,
              find.byKey(const Key('chat-accept-invitation-button')),
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
            await tester.pageBack();
            await navigateNativeDualDestination(
              tester,
              Icons.calendar_month_outlined,
            );
            await _tap(
              tester,
              find.byTooltip(_copy(tester).calendarCreateButton),
            );
            await _editTitle(tester, eventTitle);
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
            await tester.pageBack();
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
            await _tap(
              tester,
              find.byTooltip(
                _copy(tester).calendarEditEventTooltip(eventTitle),
              ),
            );
            await _editTitle(tester, updatedTitle);
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
            await _writePrivateJson(
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
            await _wait(
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
            await _writePrivateJson(
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
          case 'files-export':
          case 'files-export-updated':
            expect(fileId, isNotNull);
            final output = File(
              '${(await getApplicationDocumentsDirectory()).path}/$fileName',
            );
            expect(
              await output.exists(),
              isFalse,
              reason: 'An existing fixture cannot prove native export.',
            );
            await _writePrivateJson(
              File('${support.path}/weave-dual-ios-native-request.json'),
              {'sequence': next, 'mode': 'export', 'fileName': fileName},
            );
            await _tap(tester, find.byKey(Key('files-export-$fileId')));
            await _wait(
              tester,
              () =>
                  container
                      .read(filesProvider)
                      .asData
                      ?.value
                      .entryActionStatus
                      .phase ==
                  FilesEntryActionPhase.exportedEntry,
              const Duration(seconds: 90),
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
            await _openDualRoom(tester, conversation);
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
            await _tap(tester, find.text(conversation));
            await _wait(
              tester,
              () =>
                  _message(messageA).evaluate().length == 1 &&
                  _message(messageB).evaluate().length == 1,
              const Duration(seconds: 90),
            );
            for (final retained in [
              'A recoverable $_runId',
              'B remains online $_runId',
              'B background catchup $_runId',
            ]) {
              await _wait(
                tester,
                () => _message(retained).evaluate().length == 1,
                const Duration(seconds: 90),
              );
            }
            _orderedMessages(tester, messageA, messageB);
            await tester.pageBack();
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
        await _writePrivateJson(resultFile, result);
        final ack = File('${support.path}/weave-dual-ios-ack.json');
        await _writePrivateJson(ack, {
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
  final selector = find.byKey(const Key('files-space-selector'));
  await _wait(
    tester,
    () => selector.evaluate().length == 1,
    const Duration(seconds: 90),
  );
  await _tap(tester, selector);
  final choice = find
      .text(_copy(tester).filesSharedOrganizationSpace)
      .hitTestable();
  expect(choice, findsOneWidget);
  await _tap(tester, choice);
  await _wait(tester, () {
    final state = container.read(filesProvider).asData?.value;
    return state?.rootSpaceRef == spaceRef &&
        state?.directoryListing != null &&
        !state!.isBusy &&
        state.directoryFailure == null;
  }, const Duration(seconds: 90));
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
  await _tap(
    tester,
    find.widgetWithText(
      TextButton,
      MaterialLocalizations.of(tester.element(content)).closeButtonLabel,
    ),
  );
}

AppLocalizations _copy(WidgetTester tester) =>
    AppLocalizations.of(tester.element(find.byType(NavigationBar)));

Future<void> navigateNativeDualDestination(WidgetTester tester, IconData icon) {
  final copy = _copy(tester);
  final label = switch (icon) {
    Icons.home_outlined => copy.navHome,
    Icons.chat_bubble_outline => copy.navChat,
    Icons.folder_outlined => copy.navFiles,
    Icons.calendar_month_outlined => copy.navCalendar,
    Icons.settings_outlined => copy.navSettings,
    _ => throw TestFailure('Unsupported actual navigation destination.'),
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

Future<void> _openDualRoom(WidgetTester tester, String title) async {
  if (find.byType(ChatRoomScreen).evaluate().isNotEmpty) return;
  await navigateNativeDualDestination(tester, Icons.chat_bubble_outline);
  await _tap(tester, find.text(title));
  await _wait(
    tester,
    () => find.byType(TextField).evaluate().isNotEmpty,
    const Duration(seconds: 90),
  );
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
    await tester.enterText(field, message);
    await _wait(tester, controlsReady, remaining());
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
        }
      },
      unavailable: () {},
    );
    rethrow;
  }
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
      'sessionRequiredCount=${copy == null ? 0 : withinRoom(find.text(copy.chatErrorSessionRequiredGuidance))} '
      'invitationCount=${withinRoom(find.byKey(const Key('chat-accept-invitation-button')))} '
      'archiveReturnCount=${copy == null ? 0 : withinRoom(find.widgetWithText(OutlinedButton, copy.chatRoomActiveTimelineAction))} '
      'workspaceTabCount=${count(tabs)} workspaceTabIndex=${tabIndex?.clamp(0, 999) ?? 'unavailable'} '
      'shellVisible=${count(find.byType(AppShell))} '
      'shellMounted=${count(find.byType(AppShell, skipOffstage: false))} '
      'navigationVisible=${count(find.byType(NavigationBar))} '
      'navigationMounted=${count(find.byType(NavigationBar, skipOffstage: false))} '
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
  final selected = navigation.evaluate().length == 1
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

Future<void> _editTitle(WidgetTester tester, String title) async {
  final copy = _copy(tester);
  final field = find.descendant(
    of: find.byType(AlertDialog),
    matching: find.byWidgetPredicate(
      (widget) =>
          widget is TextField &&
          widget.decoration?.labelText == copy.calendarTitleFieldLabel,
    ),
  );
  expect(field, findsOneWidget);
  await tester.enterText(field, title);
  await _tap(
    tester,
    find.widgetWithText(FilledButton, copy.calendarSaveButton),
  );
}

Finder _message(String value) =>
    find.byWidgetPredicate((widget) => widget is Text && widget.data == value);

Future<void> _calendarReadback(
  WidgetTester tester,
  String title,
  bool present,
) async {
  final deadline = DateTime.now().add(const Duration(seconds: 90));
  while (true) {
    final refresh = find.descendant(
      of: find.byType(CalendarScreen),
      matching: find.byType(RefreshIndicator),
    );
    expect(refresh, findsOneWidget);
    await tester.drag(refresh, const Offset(0, 450));
    await tester.pump(const Duration(seconds: 2));
    final container = ProviderScope.containerOf(
      tester.element(find.byType(WeaveApp)),
    );
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
