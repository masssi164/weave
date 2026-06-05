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
      expect(handoff.productBaseUrl.toString(), 'http://192.168.42.10:8080/');
      expect(
        handoff.platformConfigUrl.toString(),
        'http://192.168.42.10:8080/api/platform/config',
      );
    });

    test('accepts production HTTPS universal link shape', () {
      final handoff = const MemberHandoffParser().parse(
        Uri.parse(
          'https://join.weave.example/join?handoff_ref=invite-abc123&org=acme&workspace=main&profile=production&run_id=prod-001',
        ),
      );

      expect(handoff.profile, 'production');
      expect(handoff.productBaseUrl.toString(), 'https://join.weave.example/');
      expect(
        handoff.platformConfigUrl.toString(),
        'https://join.weave.example/api/platform/config',
      );
    });

    test('accepts custom scheme handoff with explicit platform config', () {
      final handoff = const MemberHandoffParser().parse(
        Uri.parse(
          'weave:/join?handoff_ref=handoff-abc123&org=massimo-dogfood&workspace=home&run_id=s31-check&product_base_url=https%3A%2F%2F192.168.42.10%3A44443&platform_config_url=https%3A%2F%2F192.168.42.10%3A44443%2Fapi%2Fplatform%2Fconfig',
        ),
      );

      expect(handoff.productBaseUrl.toString(), 'https://192.168.42.10:44443/');
      expect(
        handoff.platformConfigUrl.toString(),
        'https://192.168.42.10:44443/api/platform/config',
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
            'https://join.weave.example/join?handoff_ref=handoff-abc123&org=massimo-dogfood&workspace=home&access_token=secret',
          ),
        ),
        throwsA(isA<AppFailure>()),
      );
    });
  });
}
