import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('credential-driven Flutter integration harnesses stay removed', () {
    for (final obsoletePath in const <String>[
      'integration_test/app_test.dart',
      'integration_test/live_stack_app_e2e_test.dart',
      'integration_test/matrix_e2ee_live_e2e_test.dart',
      'integration_test/multi_user_collaboration_e2e_test.dart',
      'integration_test/helpers/live_oidc_test_driver.dart',
      'integration_test/helpers/test_http_overrides.dart',
    ]) {
      expect(File(obsoletePath).existsSync(), isFalse, reason: obsoletePath);
    }
  });

  test('physical auth proof uses production AppAuth and no credentials', () {
    final testSource = File(
      'integration_test/system_browser_auth_e2e_test.dart',
    ).readAsStringSync();
    final appAuthSource = File(
      'lib/features/auth/data/services/flutter_appauth_oidc_client.dart',
    ).readAsStringSync();
    final makefile = File('Makefile').readAsStringSync();
    final combined = '$testSource\n$makefile';

    expect(testSource, contains('WeaveApp()'));
    expect(testSource, contains('authSessionRepositoryProvider'));
    expect(testSource, isNot(contains('oidcClientProvider.override')));
    expect(appAuthSource, contains('FlutterAppAuth'));
    expect(appAuthSource, contains('AuthorizationTokenRequest'));
    expect(appAuthSource, contains('issuer:'));
    expect(appAuthSource, contains('nonce:'));
    expect(makefile, contains('physical-device-auth-e2e'));
    expect(makefile, contains('.emulator == false'));
    for (final forbidden in const <String>[
      'WEAVE_TEST_USERNAME',
      'WEAVE_TEST_PASSWORD',
      "grant_type': 'password",
      'grant_type=password',
      'client_secret',
    ]) {
      expect(combined, isNot(contains(forbidden)), reason: forbidden);
    }
  });

  test('automatic product flow owns activation PKCE and MCP evidence', () {
    final productFlow = File(
      '../weave-product-e2e/src/main/java/com/massimotter/weave/e2e/FreshProductFlow.java',
    ).readAsStringSync();
    final browserJourney = File(
      '../weave-product-e2e/src/main/java/com/massimotter/weave/e2e/OidcBrowserJourney.java',
    ).readAsStringSync();
    final workflow = File(
      '../.github/workflows/live-stack-e2e.yml',
    ).readAsStringSync();

    expect(productFlow, contains('activation=browser'));
    expect(productFlow, contains('MailpitActivationInbox'));
    expect(browserJourney, contains('code_challenge'));
    expect(browserJourney, contains('S256'));
    expect(productFlow, contains('private_key_jwt'));
    expect(productFlow, contains('files.search'));
    expect(
      workflow,
      contains('./gradlew --no-daemon specCorpusConformance testApp'),
    );
    expect(workflow, isNot(contains('WEAVE_TEST_PASSWORD')));
    expect(workflow, isNot(contains('WEAVE_TEST_USERNAME')));
  });

  test('Matrix crypto still has one explicit sync and store owner', () {
    final source = File(
      '../rust/matrix-client/src/flutter_crypto.rs',
    ).readAsStringSync();
    final syncCycle = source.substring(
      source.indexOf('async fn complete_sync_cycle('),
      source.indexOf('fn client_and_sync_cursor('),
    );

    expect(source, contains('sync_cursor: Option<String>'));
    expect(source, isNot(contains('run_background_sync')));
    expect(source, isNot(contains('tokio::spawn')));
    expect(syncCycle, contains('remember_sync_cursor('));
    expect(
      syncCycle.indexOf('reconcile_verification_requests'),
      lessThan(syncCycle.indexOf('remember_sync_cursor(')),
    );
  });
}
