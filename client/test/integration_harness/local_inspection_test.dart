import 'dart:async';
import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';

import '../../integration_test/dual_ios_collaboration_test.dart'
    show nativeLocalInspectionSessionSnapshot;

void main() {
  final configuration = AuthConfiguration(
    issuer: Uri.parse('https://auth.example.invalid/realms/weave'),
    clientId: 'native-client',
  );
  final fixture = <String, dynamic>{
    'identityDigestExpected': sha256.convert(utf8.encode('actor-a')).toString(),
    'identityIssuer': configuration.issuer.toString(),
    'nativeOrganizationId': 'native-org',
  };
  String stored({String subject = 'actor-a'}) {
    final claims = {
      'sub': subject,
      'iss': configuration.issuer.toString(),
      'organization': {
        'organization-alias': {'id': 'native-org'},
      },
    };
    final token =
        'header.${base64Url.encode(utf8.encode(jsonEncode(claims)))}.signature';
    return AuthSessionDto(
      issuer: configuration.issuer.toString(),
      clientId: configuration.clientId,
      accessToken: token,
      refreshToken: 'private-refresh',
      idToken: null,
      expiresAt: '2000-01-01T00:00:00Z',
      tokenType: 'Bearer',
      scopes: const [],
    ).encode();
  }

  test(
    'passive snapshot reads stored identity without refreshing expired auth',
    () async {
      var reads = 0;
      final result = await nativeLocalInspectionSessionSnapshot(
        readSession: () async {
          reads++;
          return stored();
        },
        readDevice: () async => 'private-device',
        workspaceCurrent: () => true,
        configuration: configuration,
        fixture: fixture,
      );
      expect(reads, 2);
      expect(result?['identityDigest'], fixture['identityDigestExpected']);
      expect(
        result?['matrixDeviceDigest'],
        sha256.convert(utf8.encode('private-device')).toString(),
      );
      expect(jsonEncode(result), isNot(contains('private-')));
    },
  );

  test(
    'held session read cannot publish after workspace leaves ready',
    () async {
      final held = Completer<String?>();
      var current = true;
      var deviceReads = 0;
      final result = nativeLocalInspectionSessionSnapshot(
        readSession: () => held.future,
        readDevice: () async {
          deviceReads++;
          return 'device';
        },
        workspaceCurrent: () => current,
        configuration: configuration,
        fixture: fixture,
      );
      current = false;
      held.complete(stored());
      expect(await result, isNull);
      expect(deviceReads, 0);
    },
  );

  test(
    'held device read cannot publish after logout or configuration replacement',
    () async {
      final held = Completer<String?>();
      var current = true;
      final result = nativeLocalInspectionSessionSnapshot(
        readSession: () async => stored(),
        readDevice: () => held.future,
        workspaceCurrent: () => current,
        configuration: configuration,
        fixture: fixture,
      );
      await Future<void>.delayed(Duration.zero);
      current = false;
      held.complete('device');
      expect(await result, isNull);
    },
  );

  test(
    'session replacement during device read rejects old principal proof',
    () async {
      var reads = 0;
      expect(
        await nativeLocalInspectionSessionSnapshot(
          readSession: () async =>
              stored(subject: ++reads == 1 ? 'actor-a' : 'actor-b'),
          readDevice: () async => null,
          workspaceCurrent: () => true,
          configuration: configuration,
          fixture: fixture,
        ),
        isNull,
      );
    },
  );

  test('wrong actual subject fails with fixed comparison reason', () async {
    await expectLater(
      nativeLocalInspectionSessionSnapshot(
        readSession: () async => stored(subject: 'private-other-subject'),
        readDevice: () async => null,
        workspaceCurrent: () => true,
        configuration: configuration,
        fixture: fixture,
      ),
      throwsA(
        isA<TestFailure>().having(
          (error) => error.message,
          'private reason',
          isNot(contains('private-other-subject')),
        ),
      ),
    );
  });
}
