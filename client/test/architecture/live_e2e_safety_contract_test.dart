import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../../integration_test/helpers/isolated_stack_scope.dart';
import '../../integration_test/helpers/multi_user_test_config.dart';

void main() {
  test('destructive E2EE scope guard fails closed', () {
    expect(() => requireIsolatedStackScope(scope: 'isolated'), returnsNormally);
    for (final unsafeScope in <String>[
      '',
      'dogfood',
      'persistent',
      'ISOLATED',
    ]) {
      expect(
        () => requireIsolatedStackScope(scope: unsafeScope),
        throwsStateError,
      );
    }
  });

  test('multi-user execution modes are explicit and fail closed', () {
    expect(
      MultiUserExecutionMode.parse('collaboration'),
      MultiUserExecutionMode.collaboration,
    );
    expect(
      MultiUserExecutionMode.parse('calendar-failure-containment'),
      MultiUserExecutionMode.calendarFailureContainment,
    );
    expect(() => MultiUserExecutionMode.parse('both'), throwsStateError);
  });

  test(
    'live evidence cannot replace real capability state with an override',
    () {
      final source = File(
        'integration_test/multi_user_collaboration_e2e_test.dart',
      ).readAsStringSync();

      expect(
        source,
        isNot(
          contains('workspaceCapabilitySnapshotProvider.overrideWithValue'),
        ),
      );
      expect(source, contains('weaveApiWorkspaceCapabilitySnapshotProvider'));
      expect(source, contains('WEAVE_E2E_EXECUTION_MODE'));
    },
  );

  test('destructive live guards run before authentication', () {
    final guardedSources = <String, String>{
      'integration_test/matrix_e2ee_live_e2e_test.dart':
          'liveAuth.accessToken(config)',
      'integration_test/live_stack_app_e2e_test.dart':
          '.read(authSessionRepositoryProvider)',
      'integration_test/multi_user_collaboration_e2e_test.dart':
          '_provisionEncryptedSharedRoom(',
    };
    for (final entry in guardedSources.entries) {
      final source = File(entry.key).readAsStringSync();
      final guardIndex = source.indexOf('requireIsolatedStackScope();');
      final authenticationIndex = source.indexOf(entry.value);
      expect(guardIndex, greaterThanOrEqualTo(0), reason: entry.key);
      expect(authenticationIndex, greaterThan(guardIndex), reason: entry.key);
    }

    final makefile = File('Makefile').readAsStringSync();
    expect(makefile, contains('--arg WEAVE_E2E_STACK_SCOPE'));
    expect(
      makefile,
      contains('integration-app-e2e requires WEAVE_E2E_STACK_SCOPE=isolated'),
    );
  });

  test('live client authentication is authorization code with PKCE only', () {
    final liveSources = Directory('integration_test')
        .listSync(recursive: true)
        .whereType<File>()
        .where((file) => file.path.endsWith('.dart'));
    for (final file in liveSources) {
      final source = file.readAsStringSync();
      expect(
        source,
        isNot(contains("'grant_type': 'password'")),
        reason: file.path,
      );
      expect(source, isNot(contains('grant_type=password')), reason: file.path);
    }

    final driver = File(
      'integration_test/helpers/live_oidc_test_driver.dart',
    ).readAsStringSync();
    expect(driver, contains("'grant_type': 'authorization_code'"));
    expect(driver, contains("'code_challenge_method': 'S256'"));
  });

  test('live Chat suites arrange and clean encrypted rooms explicitly', () {
    final multiUserSource = File(
      'integration_test/multi_user_collaboration_e2e_test.dart',
    ).readAsStringSync();
    final e2eeSource = File(
      'integration_test/matrix_e2ee_live_e2e_test.dart',
    ).readAsStringSync();
    final appSource = File(
      'integration_test/live_stack_app_e2e_test.dart',
    ).readAsStringSync();

    expect(multiUserSource, contains('createEncryptedRoom('));
    expect(multiUserSource, contains('_establishEncryptedDeviceExchange('));
    expect(multiUserSource, contains('_updateCalendarEventEventually('));
    expect(multiUserSource, contains('redactEventsAndVerify('));
    expect(multiUserSource, contains('leaveRoom('));
    expect(e2eeSource, contains('createEncryptedRoom('));
    expect(e2eeSource, contains('redactEventsAndVerify('));
    expect(e2eeSource, contains('leaveRoom('));
    expect(appSource, contains('createEncryptedRoom('));
    expect(appSource, contains('redactEventsAndVerify('));
    expect(appSource, contains('leaveRoom('));
    expect(
      appSource,
      isNot(contains("conversationIdFragment: 'channel-general'")),
    );
  });

  test('Matrix background sync advances the explicit processed cursor', () {
    final source = File(
      '../rust/matrix-core/src/flutter_crypto.rs',
    ).readAsStringSync();
    final backgroundLoop = source.substring(
      source.indexOf('async fn run_background_sync('),
      source.indexOf('fn background_sync_retry_delay('),
    );

    expect(source, contains('initial_cursor: String'));
    expect(backgroundLoop, contains('Some(cursor.as_str())'));
    expect(backgroundLoop, contains('cursor = completed.next_batch.clone()'));
    expect(
      backgroundLoop.indexOf('cursor = completed.next_batch.clone()'),
      lessThan(
        backgroundLoop.indexOf('publish_completed_sync(&progress, &completed)'),
      ),
    );
  });

  test('live provider convergence is explicit and version-safe', () {
    final matrixDriver = File(
      'integration_test/helpers/matrix_live_room_driver.dart',
    ).readAsStringSync();
    final roomCreation = matrixDriver.substring(
      matrixDriver.indexOf('Future<MatrixLiveRoomProvisioning>'),
      matrixDriver.indexOf('Future<String> requireJoinedRoom'),
    );
    final calendarRepository = File(
      'lib/features/calendar/data/repositories/backend_calendar_repository.dart',
    ).readAsStringSync();
    final multiUserSource = File(
      'integration_test/multi_user_collaboration_e2e_test.dart',
    ).readAsStringSync();

    expect(matrixDriver, contains("'keys', 'query'"));
    expect(
      matrixDriver,
      contains('M_WEAVE_LIVE_MATRIX_DEVICE_KEYS_NOT_CONVERGED'),
    );
    expect(
      matrixDriver,
      contains('M_WEAVE_LIVE_MATRIX_KEY_MATERIAL_NOT_CONVERGED'),
    );
    expect(roomCreation, contains('requireMutualDeviceKeys('));
    expect(roomCreation, contains('retainOnlyCurrentDevice('));
    expect(roomCreation, contains("'createRoom'"));
    expect(
      roomCreation.indexOf('requireMutualDeviceKeys('),
      lessThan(roomCreation.indexOf("'createRoom'")),
    );
    expect(
      roomCreation.indexOf('requireMutualDeviceKeys('),
      lessThan(roomCreation.indexOf('retainOnlyCurrentDevice(')),
    );
    expect(
      roomCreation.indexOf('retainOnlyCurrentDevice('),
      lessThan(roomCreation.indexOf("'createRoom'")),
    );
    expect(multiUserSource, contains('pruneStaleActorDevices: true'));
    expect(calendarRepository, contains('draft.toPatch(etag: etag)'));
    expect(multiUserSource, contains('readEvent(eventId)'));
    expect(
      multiUserSource,
      contains('updateEvent(eventId, draft, etag: etag)'),
    );
    expect(
      multiUserSource,
      isNot(contains('return session.calendar.updateEvent(eventId, draft);')),
    );
    expect(multiUserSource, contains('!candidate.calendar.isReady'));
  });

  test('live workflow reports provider test failure on its owning step', () {
    final workflow = File(
      '../.github/workflows/live-stack-e2e.yml',
    ).readAsStringSync();

    expect(
      workflow,
      contains(
        r'if [ "$single_user_status" -ne 0 ] || '
        r'[ "$multi_user_status" -ne 0 ]; then',
      ),
    );
    expect(
      workflow,
      contains(
        'Live provider tests failed; cleanup and support-safe evidence '
        'collection will continue.',
      ),
    );
  });

  test('live actor profiles use discovery and namespaced device storage', () {
    final source = File(
      'integration_test/helpers/live_actor_session.dart',
    ).readAsStringSync();

    expect(source, contains('ConsumeMemberHandoff('));
    expect(source, contains('AppStartDiscoveryClient('));
    expect(source, contains('SharedPreferencesServerConfigurationRepository('));
    expect(source, contains('FlutterSecureStore()'));
    expect(source, contains('SharedPreferencesStore()'));
    expect(source, contains('removeTouchedKeys'));
    expect(source, contains("apiUri('/api/dav/files')"));
    expect(source, isNot(contains('_expectedFilesProductUrl')));
    expect(source, isNot(contains('_FixedServerConfigurationRepository')));
    expect(source, isNot(contains('_IsolatedSecureStore')));
    expect(source, isNot(contains('_IsolatedPreferencesStore')));
  });
}
