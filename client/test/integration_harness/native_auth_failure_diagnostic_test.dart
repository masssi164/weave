import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';

import '../../integration_test/system_browser_auth_e2e_test.dart'
    show emitNativeAuthFailureDiagnostic;

void main() {
  late List<String> records;

  setUp(() {
    records = <String>[];
    final previous = debugPrint;
    debugPrint = (message, {wrapWidth}) {
      if (message != null) records.add(message);
    };
    addTearDown(() => debugPrint = previous);
  });

  test('storage diagnostics emit only bounded integer OSStatus values', () {
    const statuses = [-999999, -34018, -1, 0, 999999];
    for (final status in statuses) {
      emitNativeAuthFailureDiagnostic(
        AuthFailure.storage(
          'sensitive account and item',
          cause: PlatformException(
            code: 'Unexpected security result code',
            message: 'sensitive token and value',
            details: status,
          ),
        ),
        simulatorDriver: false,
      );
    }

    expect(records, [
      for (final status in statuses)
        'NATIVE_PRODUCT_STAGE phase=auth-failed '
            'category=storage causeType=PlatformException '
            'appFailureType=none nestedCauseType=PlatformException '
            'backendFailure=none backendStatus=unavailable '
            'appAuthCode=unavailable nativeType=unavailable '
            'nativeCode=$status nativeDomain=unavailable',
    ]);
    expect(records.join(), isNot(contains('sensitive')));
  });

  test('storage diagnostics reject untyped and oversized details', () {
    final details = <Object?>[
      '-34018',
      'sensitive token',
      {'status': -34018, 'account': 'sensitive account'},
      [-34018, 'sensitive value'],
      null,
      true,
      -34018.0,
      -1000000,
      1000000,
    ];
    for (final detail in details) {
      emitNativeAuthFailureDiagnostic(
        AuthFailure.storage(
          'sensitive item',
          cause: PlatformException(
            code: 'Unexpected security result code',
            message: 'sensitive token',
            details: detail,
          ),
        ),
        simulatorDriver: false,
      );
    }

    expect(records, hasLength(details.length));
    for (final record in records) {
      expect(record, contains('nativeCode=unavailable '));
      expect(record, contains('nativeType=unavailable '));
      expect(record, endsWith('nativeDomain=unavailable'));
    }
    expect(records.join(), isNot(contains('sensitive')));
    expect(records.join(), isNot(contains('-34018')));
    expect(records.join(), isNot(contains('1000000')));
  });

  test('storage diagnostics require the exact secure-storage plugin code', () {
    for (final code in [
      'sensitive arbitrary code',
      'Unexpected security result code ',
      'unexpected security result code',
    ]) {
      emitNativeAuthFailureDiagnostic(
        AuthFailure.storage(
          'sensitive account',
          cause: PlatformException(
            code: code,
            message: 'sensitive token',
            details: -34018,
          ),
        ),
        simulatorDriver: false,
      );
    }

    expect(records, hasLength(3));
    for (final record in records) {
      expect(record, contains('nativeCode=unavailable '));
    }
    expect(records.join(), isNot(contains('sensitive')));
    expect(records.join(), isNot(contains('-34018')));
  });

  test('non-storage failures do not expose secure-storage details', () {
    final types = AuthFailureType.values.where(
      (type) => type != AuthFailureType.storage,
    );
    for (final type in types) {
      emitNativeAuthFailureDiagnostic(
        AuthFailure(
          type: type,
          message: 'sensitive account',
          cause: PlatformException(
            code: 'Unexpected security result code',
            message: 'sensitive token',
            details: -34018,
          ),
        ),
        simulatorDriver: false,
      );
    }

    expect(records, hasLength(types.length));
    for (final record in records) {
      expect(record, contains('nativeCode=unavailable '));
    }
    expect(records.join(), isNot(contains('sensitive')));
    expect(records.join(), isNot(contains('-34018')));
  });

  test('AppAuth diagnostics retain their existing platform fields', () {
    const cases = [
      (
        type: AuthFailureType.protocol,
        code: 'token_failed',
        expectedCode: 'token_failed',
      ),
      (
        type: AuthFailureType.storage,
        code: 'Unexpected security result code',
        expectedCode: 'unavailable',
      ),
    ];
    for (final appAuthCase in cases) {
      emitNativeAuthFailureDiagnostic(
        AuthFailure(
          type: appAuthCase.type,
          message: 'sensitive account',
          cause: FlutterAppAuthPlatformException(
            code: appAuthCase.code,
            message: 'sensitive token',
            legacyDetails: -34018,
            platformErrorDetails: FlutterAppAuthPlatformErrorDetails(
              type: 'org.openid.appauth.oauth_token',
              code: '-6',
              domain: 'org.openid.appauth.oauth_token',
              error: FlutterAppAuthOAuthError.invalidGrant,
              errorDescription: 'sensitive value',
              rootCauseDebugDescription: 'sensitive item',
            ),
          ),
        ),
        simulatorDriver: false,
      );
    }

    expect(records, [
      for (final appAuthCase in cases)
        'NATIVE_PRODUCT_STAGE phase=auth-failed '
            'category=${appAuthCase.type.name} causeType=FlutterAppAuthPlatformException '
            'appFailureType=none nestedCauseType=FlutterAppAuthPlatformException '
            'backendFailure=none backendStatus=unavailable '
            'appAuthCode=${appAuthCase.expectedCode} nativeType=org.openid.appauth.oauth_token '
            'nativeCode=-6 nativeDomain=org.openid.appauth.oauth_token',
    ]);
    expect(records.join(), isNot(contains('sensitive')));
    expect(records.join(), isNot(contains('invalid_grant')));
    expect(records.join(), isNot(contains('-34018')));
  });
}
