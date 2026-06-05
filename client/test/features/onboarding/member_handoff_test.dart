import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';

void main() {
  group('MemberHandoffParser', () {
    test('accepts support-safe LAN handoff for local dogfood', () {
      final handoff = const MemberHandoffParser().parse(
        Uri.parse(
          'http://192.168.42.10:8080/join?handoff_ref=handoff-abc123&org=massimo-dogfood&workspace=home&run_id=s31-check',
        ),
      );

      expect(handoff.profile, 'local-lan-dogfood');
      expect(handoff.appBaseUrl.toString(), 'http://192.168.42.10:8080/');
      expect(
        handoff.issuerUrl.toString(),
        'http://192.168.42.10:8080/auth/realms/weave',
      );
      expect(
        handoff.backendApiBaseUrl.toString(),
        'http://192.168.42.10:8080/api',
      );
    });

    test('rejects loopback and Mac-only handoff targets', () {
      for (final host in ['localhost', '127.0.0.1', '0.0.0.0', 'weave.local']) {
        expect(
          () => const MemberHandoffParser().parse(
            Uri.parse(
              'http://$host:8080/join?handoff_ref=handoff-abc123&org=massimo-dogfood&workspace=home',
            ),
          ),
          throwsA(isA<AppFailure>()),
        );
      }
    });

    test('rejects credential-bearing handoff query data', () {
      expect(
        () => const MemberHandoffParser().parse(
          Uri.parse(
            'http://192.168.42.10:8080/join?handoff_ref=handoff-abc123&org=massimo-dogfood&workspace=home&access_token=secret',
          ),
        ),
        throwsA(isA<AppFailure>()),
      );
    });
  });
}
