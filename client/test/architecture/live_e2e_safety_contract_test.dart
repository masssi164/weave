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
          'authHelper.signIn(config)',
      'integration_test/live_stack_app_e2e_test.dart':
          'AuthHelper().signInForAppSession(config)',
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
    expect(multiUserSource, contains('redactEventsAndVerify('));
    expect(multiUserSource, contains('leaveRoom('));
    expect(e2eeSource, contains('createEncryptedRoom('));
    expect(e2eeSource, contains('redactEventsAndVerify('));
    expect(e2eeSource, contains('leaveRoom('));
    expect(appSource, contains('enableEncryptionOnJoinedRoom('));
    expect(appSource, contains('redactEventsAndVerify('));
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
    expect(source, isNot(contains('_FixedServerConfigurationRepository')));
    expect(source, isNot(contains('_IsolatedSecureStore')));
    expect(source, isNot(contains('_IsolatedPreferencesStore')));
  });
}
