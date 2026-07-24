import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';

void main() {
  group('OrganizationAccessParser', () {
    test('accepts a direct organization origin without a handoff', () {
      final access = const OrganizationAccessParser().parse(
        Uri.parse('/join?organization_origin=https%3A%2F%2Fweave.example%2F'),
      );

      expect(access.organizationOrigin, Uri.parse('https://weave.example/'));
      expect(
        access.platformConfigUrl,
        Uri.parse('https://weave.example/api/platform/config'),
      );
      expect(access.handoff, isNull);
      expect(access.organizationLabel, 'weave.example');
    });

    test('preserves real completion-link metadata as optional context', () {
      final access = const OrganizationAccessParser().parse(
        Uri.parse(
          'https://weave.example/join?handoff_ref=invite-123&org=acme&workspace=home&run_id=email-123',
        ),
      );

      expect(access.organizationOrigin, Uri.parse('https://weave.example/'));
      expect(access.handoff?.handoffRef, 'invite-123');
      expect(access.handoff?.organizationSlug, 'acme');
      expect(access.organizationLabel, 'acme');
    });

    test('rejects a credential-bearing direct organization origin', () {
      expect(
        () => const OrganizationAccessParser().parse(
          Uri.parse(
            '/join?organization_origin=https%3A%2F%2Fuser%3Asecret%40weave.example%2F',
          ),
        ),
        throwsA(isA<AppFailure>()),
      );
    });
  });

  group('MemberHandoffParser', () {
    test('accepts DNS-first weave.test invite link for local dogfood', () {
      final handoff = const MemberHandoffParser().parse(
        Uri.parse(
          'https://weave.test:44443/join?handoff_ref=handoff-abc123&org=massimo-dogfood&workspace=home&run_id=s32-check',
        ),
      );

      expect(handoff.productBaseUrl.toString(), 'https://weave.test:44443/');
      expect(
        handoff.platformConfigUrl.toString(),
        'https://weave.test:44443/api/platform/config',
      );
    });

    test('rejects HTTP invite links even when the join route is present', () {
      expect(
        () => const MemberHandoffParser().parse(
          Uri.parse(
            'http://weave.test:8080/join?handoff_ref=handoff-abc123&org=massimo-dogfood&workspace=home&run_id=s32-check',
          ),
        ),
        throwsA(isA<AppFailure>()),
      );
    });

    test('rejects LAN IP invite links with a stable DNS/TLS remedy', () {
      expect(
        () => const MemberHandoffParser().parse(
          Uri.parse(
            'https://192.168.42.10:44443/join?handoff_ref=handoff-abc123&org=massimo-dogfood&workspace=home&run_id=s32-check',
          ),
        ),
        throwsA(
          isA<AppFailure>().having(
            (failure) => failure.message,
            'message',
            contains('WEAVE-LAN-UNREACHABLE'),
          ),
        ),
      );
    });

    test('treats legacy profile as migration data, not runtime policy', () {
      final handoff = const MemberHandoffParser().parse(
        Uri.parse(
          'https://join.weave.example/join?handoff_ref=handoff-abc123&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-check',
        ),
      );

      expect(handoff.productBaseUrl, Uri.parse('https://join.weave.example/'));
    });

    test('accepts production HTTPS universal link shape', () {
      final handoff = const MemberHandoffParser().parse(
        Uri.parse(
          'https://join.weave.example/join?handoff_ref=invite-abc123&org=acme&workspace=main&run_id=prod-001',
        ),
      );

      expect(handoff.productBaseUrl.toString(), 'https://join.weave.example/');
      expect(
        handoff.platformConfigUrl.toString(),
        'https://join.weave.example/api/platform/config',
      );
    });

    test('accepts custom scheme handoff with explicit platform config', () {
      final handoff = const MemberHandoffParser().parse(
        Uri.parse(
          'weave:/join?handoff_ref=handoff-abc123&org=massimo-dogfood&workspace=home&run_id=s32-check&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fapi.weave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
        ),
      );

      expect(handoff.productBaseUrl.toString(), 'https://weave.test:44443/');
      expect(
        handoff.platformConfigUrl.toString(),
        'https://api.weave.test:44443/api/platform/config',
      );
    });

    test('accepts installed iOS custom scheme handoff shape', () {
      final handoff = const MemberHandoffParser().parse(
        Uri.parse(
          'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&run_id=s32-massimo-dogfood&product_base_url=https://weave.test:44443&platform_config_url=https://api.weave.test:44443/api/platform/config',
        ),
      );

      expect(handoff.handoffRef, 'handoff-s32-massimo-dogfood-home');
      expect(handoff.productBaseUrl.toString(), 'https://weave.test:44443/');
      expect(
        handoff.platformConfigUrl.toString(),
        'https://api.weave.test:44443/api/platform/config',
      );
    });

    test(
      'accepts routed in-app join handoff shape from installed iOS launch',
      () {
        final handoff = const MemberHandoffParser().parse(
          Uri.parse(
            '/join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fweave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
          ),
        );

        expect(handoff.handoffRef, 'handoff-s32-massimo-dogfood-home');
        expect(handoff.productBaseUrl.toString(), 'https://weave.test:44443/');
        expect(
          handoff.platformConfigUrl.toString(),
          'https://weave.test:44443/api/platform/config',
        );
      },
    );

    test('generates deterministic QR payload matching the invite contract', () {
      final payload = const MemberHandoffPayloadBuilder().qrPayload(
        productBaseUrl: Uri.parse('https://weave.test:44443'),
        handoffRef: 'handoff-qr123',
        organizationSlug: 'massimo-dogfood',
        workspaceSlug: 'home',
        runId: 's32-qr',
      );

      final decoded = Uri.parse(payload);
      final handoff = const MemberHandoffParser().parse(decoded);

      expect(decoded.host, 'weave.test');
      expect(decoded.path, '/join');
      expect(handoff.handoffRef, 'handoff-qr123');
      expect(
        handoff.platformConfigUrl.toString(),
        'https://weave.test:44443/api/platform/config',
      );
      expect(payload, isNot(contains('password')));
      expect(payload, isNot(contains('token')));
      expect(payload, isNot(contains('profile')));
    });

    test('rejects loopback and Mac-only handoff targets', () {
      for (final host in [
        'localhost',
        '127.0.0.1',
        '0.0.0.0',
        'printer.local',
      ]) {
        expect(
          () => const MemberHandoffParser().parse(
            Uri.parse(
              'https://$host:44443/join?handoff_ref=handoff-abc123&org=massimo-dogfood&workspace=home',
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

    test('rejects embedded credentials in canonical app-start URLs', () {
      const parser = MemberHandoffParser();

      for (final uri in [
        Uri.parse(
          'https://user:pass@join.weave.example/join?handoff_ref=handoff-abc123&org=massimo-dogfood&workspace=home',
        ),
        Uri.parse(
          'weave:/join?handoff_ref=handoff-abc123&org=massimo-dogfood&workspace=home&product_base_url=https%3A%2F%2Fuser%3Apass%40join.weave.example&platform_config_url=https%3A%2F%2Fapi.weave.example%2Fapi%2Fplatform%2Fconfig',
        ),
        Uri.parse(
          'weave:/join?handoff_ref=handoff-abc123&org=massimo-dogfood&workspace=home&product_base_url=https%3A%2F%2Fjoin.weave.example&platform_config_url=https%3A%2F%2Fuser%3Apass%40api.weave.example%2Fapi%2Fplatform%2Fconfig',
        ),
      ]) {
        expect(() => parser.parse(uri), throwsA(isA<AppFailure>()));
      }
    });
  });
}
