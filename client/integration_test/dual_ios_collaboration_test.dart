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
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/chat/data/repositories/matrix_device_identity_repository.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/calendar/presentation/calendar_screen.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/features/files/presentation/providers/files_provider.dart';
import 'package:weave/features/onboarding/presentation/organization_access_screen.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';
import 'package:weave/l10n/generated/app_localizations.dart';
import 'package:weave/main.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';
import 'package:weave/integrations/rust_matrix_core/presentation/providers/matrix_crypto_session_provider.dart';

import 'system_browser_auth_e2e_test.dart' show emitNativeProductDiagnostic;

const _runId = String.fromEnvironment('WEAVE_NATIVE_TEST_RUN_ID');
const _simulator = bool.fromEnvironment('WEAVE_NATIVE_EXPECT_SIMULATOR');
const _disposable = bool.fromEnvironment('WEAVE_DEVICE_DISPOSABLE_STACK');

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
  await tester.ensureVisible(finder);
  await tester.tap(finder);
  await tester.pump(const Duration(milliseconds: 250));
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
      final entry = fixture['entryUri'] as String;
      expect(entry, isNotEmpty);
      // No configuration, repositories, authentication services or native picker
      // overrides: the same product bootstrap handles public host and invite input.
      await tester.pumpWidget(const ProviderScope(child: WeaveApp()));
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
      final signIn = find.byKey(const ValueKey('weave.auth.sign-in'));
      await _wait(
        tester,
        () => signIn.evaluate().isNotEmpty,
        const Duration(seconds: 90),
      );
      expect(signIn, findsOneWidget);
      expect(find.byKey(const ValueKey('weave.workspace.home')), findsNothing);
      _stage('shell-ready');
      final barrier = File('${support.path}/weave-native-driver-$_runId.ready');
      await _wait(
        tester,
        () => barrier.existsSync(),
        const Duration(minutes: 2),
      );
      await barrier.delete();
      _stage('driver-attached');
      // Actual XCTest taps the accessible Sign In control and supplies credentials
      // exclusively to the real issuer web form through ASWebAuthenticationSession.
      await _wait(
        tester,
        () => find
            .byKey(const ValueKey('weave.workspace.home'))
            .evaluate()
            .isNotEmpty,
        const Duration(minutes: 5),
      );
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
      final resources = <String, Object>{};
      final result = {
        'role': role,
        'identityDigest': sha256.convert(utf8.encode(subject)).toString(),
        'matrixDeviceDigest': sha256.convert(utf8.encode(device!)).toString(),
        'storageDigest': fixture['storageDigest'],
        'scenarios': scenarios,
        'resources': resources,
      };
      final resultFile = File('${support.path}/weave-dual-ios-result.json');
      await _writePrivateJson(resultFile, result);
      final commandFile = File('${support.path}/weave-dual-ios-command.json');
      var sequence = 0;
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
      String? fileId;
      String? fileRevision;
      while (true) {
        await _wait(tester, () {
          if (!commandFile.existsSync()) return false;
          final value =
              jsonDecode(commandFile.readAsStringSync())
                  as Map<String, dynamic>;
          return value['sequence'] is int &&
              (value['sequence'] as int) > sequence;
        }, const Duration(minutes: 3));
        final command =
            jsonDecode(await commandFile.readAsString())
                as Map<String, dynamic>;
        final action = command['action'] as String;
        final next = command['sequence'] as int;
        expect(next, greaterThan(sequence));
        switch (action) {
          case 'chat-create-send':
            expect(role, 'A');
            await _tap(
              tester,
              find.byKey(const Key('chat-create-conversation-button')),
            );
            await tester.enterText(
              find.byKey(const Key('chat-create-conversation-name-field')),
              conversation,
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
            await _tap(tester, peer);
            await _tap(
              tester,
              find.byKey(const Key('chat-create-conversation-submit-button')),
            );
            await _wait(
              tester,
              () => find.byType(ChatRoomScreen).evaluate().length == 1,
              const Duration(seconds: 90),
            );
            await _send(tester, messageA);
          case 'chat-open-reply':
            expect(role, 'B');
            await _navigate(tester, Icons.home_outlined);
            await _navigate(tester, Icons.chat_bubble_outline);
            await _wait(
              tester,
              () => find.text(conversation).evaluate().length == 1,
              const Duration(seconds: 90),
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
            await _send(tester, messageB);
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
            await _navigate(tester, Icons.calendar_month_outlined);
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
            await _navigate(tester, Icons.calendar_month_outlined);
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
            await _navigate(tester, Icons.home_outlined);
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
          case 'recovery':
            throw TestFailure(
              'Independent actual transport and process recovery proof is incomplete.',
            );
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
        if (action == 'finish') break;
      }
    },
    timeout: const Timeout(Duration(minutes: 30)),
  );
}

Future<void> _selectSharedFiles(
  WidgetTester tester,
  ProviderContainer container,
  String spaceRef,
) async {
  await _navigate(tester, Icons.folder_outlined);
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

Future<void> _navigate(WidgetTester tester, IconData icon) => _tap(
  tester,
  find.descendant(of: find.byType(NavigationBar), matching: find.byIcon(icon)),
);

void _orderedMessages(WidgetTester tester, String first, String second) {
  expect(_message(first), findsOneWidget);
  expect(_message(second), findsOneWidget);
  expect(
    tester.getCenter(_message(first)).dy,
    lessThan(tester.getCenter(_message(second)).dy),
  );
}

Future<void> _send(WidgetTester tester, String message) async {
  final field = find.descendant(
    of: find.byType(ChatRoomScreen),
    matching: find.byType(TextField),
  );
  expect(field, findsOneWidget);
  await tester.enterText(field, message);
  await _tap(
    tester,
    find.widgetWithText(FilledButton, _copy(tester).chatRoomSendButton),
  );
  await _wait(
    tester,
    () =>
        _message(message).evaluate().length == 1 &&
        (tester.widget<TextField>(field).controller?.text.isEmpty ?? false),
    const Duration(seconds: 60),
  );
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
