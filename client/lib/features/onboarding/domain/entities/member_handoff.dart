import 'package:weave/core/failures/app_failure.dart';

class MemberHandoff {
  const MemberHandoff({
    required this.handoffRef,
    required this.profile,
    required this.runId,
    required this.organizationSlug,
    required this.workspaceSlug,
    required this.appBaseUrl,
  });

  final String handoffRef;
  final String profile;
  final String runId;
  final String organizationSlug;
  final String workspaceSlug;
  final Uri appBaseUrl;

  Uri get issuerUrl => _withoutQuery('/auth/realms/weave');

  Uri get backendApiBaseUrl => _withoutQuery('/api');

  Uri get providerNeutralServiceUrl => _withoutQuery('/');

  Uri _withoutQuery(String path) => Uri(
    scheme: appBaseUrl.scheme,
    host: appBaseUrl.host,
    port: appBaseUrl.hasPort ? appBaseUrl.port : null,
    path: path,
  );
}

class MemberHandoffParser {
  const MemberHandoffParser();

  MemberHandoff parse(Uri uri) {
    final query = uri.queryParameters;
    final handoffRef = _requiredSafeRef(query, 'handoff_ref');
    final profile = _requiredSafeSlug(
      query,
      'profile',
      fallback: 'local-lan-dogfood',
    );
    final runId = _requiredSafeSlug(query, 'run_id', fallback: 'unknown-run');
    final org = _requiredSafeSlug(query, 'org');
    final workspace = _requiredSafeSlug(query, 'workspace');
    _rejectCredentialBearingQuery(query);

    final appBaseUrl = _baseUrlFrom(uri);
    _validatePhoneReachable(appBaseUrl);

    return MemberHandoff(
      handoffRef: handoffRef,
      profile: profile,
      runId: runId,
      organizationSlug: org,
      workspaceSlug: workspace,
      appBaseUrl: appBaseUrl,
    );
  }

  Uri _baseUrlFrom(Uri uri) {
    if (uri.scheme == 'weave') {
      final raw = uri.queryParameters['app_base_url'];
      if (raw == null || raw.trim().isEmpty) {
        throw const AppFailure.validation(
          'WEAVE-HANDOFF-MISSING-BASE: Ask an admin/operator to refresh the invite.',
        );
      }
      return Uri.parse(raw);
    }
    return Uri(
      scheme: uri.scheme,
      host: uri.host,
      port: uri.hasPort ? uri.port : null,
      path: '/',
    );
  }

  String _requiredSafeRef(Map<String, String> query, String key) {
    final value = query[key]?.trim();
    if (value == null ||
        value.isEmpty ||
        !RegExp(r'^[A-Za-z0-9._:-]{6,96}$').hasMatch(value)) {
      throw const AppFailure.validation(
        'WEAVE-HANDOFF-INVALID: The invite is not a valid Weave handoff.',
      );
    }
    return value;
  }

  String _requiredSafeSlug(
    Map<String, String> query,
    String key, {
    String? fallback,
  }) {
    final value = query[key]?.trim() ?? fallback;
    if (value == null ||
        value.isEmpty ||
        !RegExp(r'^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$').hasMatch(value)) {
      throw const AppFailure.validation(
        'WEAVE-HANDOFF-INVALID: The invite is missing safe organization context.',
      );
    }
    return value;
  }

  void _rejectCredentialBearingQuery(Map<String, String> query) {
    const forbidden = {
      'token',
      'access_token',
      'refresh_token',
      'id_token',
      'client_secret',
      'password',
      'matrix_url',
      'nextcloud_url',
      'provider_url',
      'secret_ref',
      'credential_url',
    };
    for (final key in query.keys) {
      if (forbidden.contains(key.toLowerCase())) {
        throw const AppFailure.validation(
          'WEAVE-HANDOFF-SECRET-BLOCKED: Ask an admin/operator for a support-safe invite.',
        );
      }
    }
  }

  void _validatePhoneReachable(Uri uri) {
    final host = uri.host.toLowerCase();
    if (host.isEmpty ||
        host == 'localhost' ||
        host == 'host.docker.internal' ||
        host == 'docker.for.mac.localhost' ||
        host.endsWith('.local')) {
      throw const AppFailure.validation(
        'WEAVE-LAN-UNREACHABLE: The invite does not point to a phone-reachable LAN address.',
      );
    }
    final parts = host.split('.');
    if (parts.length == 4 &&
        parts.every((part) => int.tryParse(part) != null)) {
      final octets = parts.map(int.parse).toList(growable: false);
      final loopback = octets.first == 127;
      final unspecified = octets.every((octet) => octet == 0);
      final privateLan =
          octets.first == 10 ||
          (octets.first == 172 && octets[1] >= 16 && octets[1] <= 31) ||
          (octets.first == 192 && octets[1] == 168);
      if (loopback || unspecified || !privateLan) {
        throw const AppFailure.validation(
          'WEAVE-LAN-UNREACHABLE: The invite does not point to a phone-reachable LAN address.',
        );
      }
    } else if (!host.endsWith('.lan') &&
        !host.endsWith('.home') &&
        !host.endsWith('.home.internal') &&
        !host.endsWith('.internal')) {
      throw const AppFailure.validation(
        'WEAVE-LAN-UNREACHABLE: The invite does not point to a phone-reachable LAN address.',
      );
    }
  }
}
