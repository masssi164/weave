import 'package:weave/core/failures/app_failure.dart';

class MemberHandoff {
  const MemberHandoff({
    required this.handoffRef,
    required this.profile,
    required this.runId,
    required this.organizationSlug,
    required this.workspaceSlug,
    required this.platformConfigUrl,
    required this.productBaseUrl,
  });

  final String handoffRef;
  final String profile;
  final String runId;
  final String organizationSlug;
  final String workspaceSlug;

  /// Public, unauthenticated app-start discovery endpoint.
  ///
  /// This is the single mobile startup contract. It returns support-safe
  /// product configuration such as OIDC issuer/client id and backend facades;
  /// it must not return tokens, secrets, raw provider diagnostics, or admin
  /// control-plane state.
  final Uri platformConfigUrl;

  /// Product/user-facing origin used for browser fallback copy and legacy
  /// local-dev links. Do not derive provider internals from this origin.
  final Uri productBaseUrl;

  /// Backwards-compatible alias for older local dogfood tests/scripts.
  Uri get appBaseUrl => productBaseUrl;

  /// Emergency local-dev fallback only. Production configuration comes from
  /// [platformConfigUrl].
  Uri get fallbackIssuerUrl => _joinProductPath('/auth/realms/weave');

  /// Emergency local-dev fallback only. Production configuration comes from
  /// [platformConfigUrl].
  Uri get fallbackBackendApiBaseUrl => _joinProductPath('/api');

  /// Emergency local-dev fallback only. Production configuration comes from
  /// [platformConfigUrl].
  Uri get fallbackProviderNeutralServiceUrl => _joinProductPath('/');

  Uri _joinProductPath(String path) => Uri(
    scheme: productBaseUrl.scheme,
    host: productBaseUrl.host,
    port: productBaseUrl.hasPort ? productBaseUrl.port : null,
    path: path,
  );
}

class MemberHandoffParser {
  const MemberHandoffParser();

  MemberHandoff parse(Uri uri) {
    _validateJoinEntrypoint(uri);
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

    final productBaseUrl = _productBaseUrlFrom(uri, query);
    _validatePhoneReachable(productBaseUrl, profile: profile);

    final platformConfigUrl = _platformConfigUrlFrom(
      uri,
      query,
      productBaseUrl,
    );
    _validatePhoneReachable(platformConfigUrl, profile: profile);

    return MemberHandoff(
      handoffRef: handoffRef,
      profile: profile,
      runId: runId,
      organizationSlug: org,
      workspaceSlug: workspace,
      platformConfigUrl: platformConfigUrl,
      productBaseUrl: productBaseUrl,
    );
  }

  void _validateJoinEntrypoint(Uri uri) {
    if (uri.scheme != 'http' &&
        uri.scheme != 'https' &&
        uri.scheme != 'weave') {
      throw const AppFailure.validation(
        'WEAVE-HANDOFF-INVALID: The invite must use HTTPS or the Weave app link.',
      );
    }

    final isCustomSchemeJoin =
        uri.scheme == 'weave' && (uri.path == '/join' || uri.host == 'join');
    final isWebJoin =
        (uri.scheme == 'http' || uri.scheme == 'https') && uri.path == '/join';
    if (!isCustomSchemeJoin && !isWebJoin) {
      throw const AppFailure.validation(
        'WEAVE-HANDOFF-INVALID: The invite must point to the Weave join route.',
      );
    }
  }

  Uri _productBaseUrlFrom(Uri uri, Map<String, String> query) {
    final explicit = query['product_base_url'] ?? query['app_base_url'];
    if (explicit != null && explicit.trim().isNotEmpty) {
      final parsed = _parseAbsoluteHttpUri(explicit, 'product_base_url');
      return Uri(
        scheme: parsed.scheme,
        host: parsed.host,
        port: parsed.hasPort ? parsed.port : null,
        path: '/',
      );
    }
    if (uri.scheme == 'weave') {
      throw const AppFailure.validation(
        'WEAVE-HANDOFF-MISSING-BASE: Ask an admin/operator to refresh the invite.',
      );
    }
    return Uri(
      scheme: uri.scheme,
      host: uri.host,
      port: uri.hasPort ? uri.port : null,
      path: '/',
    );
  }

  Uri _platformConfigUrlFrom(
    Uri uri,
    Map<String, String> query,
    Uri productBaseUrl,
  ) {
    final explicit = query['platform_config_url'] ?? query['discovery_url'];
    if (explicit != null && explicit.trim().isNotEmpty) {
      return _parseAbsoluteHttpUri(explicit, 'platform_config_url');
    }

    final base = uri.scheme == 'weave'
        ? productBaseUrl
        : Uri(
            scheme: uri.scheme,
            host: uri.host,
            port: uri.hasPort ? uri.port : null,
            path: '/',
          );
    return Uri(
      scheme: base.scheme,
      host: base.host,
      port: base.hasPort ? base.port : null,
      path: '/api/platform/config',
    );
  }

  Uri _parseAbsoluteHttpUri(String rawValue, String fieldName) {
    final uri = Uri.tryParse(rawValue.trim());
    if (uri == null || !uri.isAbsolute || uri.host.isEmpty) {
      throw AppFailure.validation(
        'WEAVE-HANDOFF-INVALID: $fieldName must be an absolute URL.',
      );
    }
    if (uri.scheme != 'http' && uri.scheme != 'https') {
      throw AppFailure.validation(
        'WEAVE-HANDOFF-INVALID: $fieldName must use HTTP or HTTPS.',
      );
    }
    if (uri.hasQuery || uri.hasFragment) {
      throw AppFailure.validation(
        'WEAVE-HANDOFF-INVALID: $fieldName must not include query or fragment data.',
      );
    }
    return uri;
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

  void _validatePhoneReachable(Uri uri, {required String profile}) {
    final host = uri.host.toLowerCase();
    final hostClass = _hostClass(host);
    if (hostClass.startsWith('forbidden')) {
      throw const AppFailure.validation(
        'WEAVE-LINK-UNREACHABLE: The invite does not point to a phone-reachable address.',
      );
    }

    final localProfile =
        profile == 'local-lan-dogfood' ||
        profile == 'dev' ||
        profile == 'local-dogfood';
    if (localProfile) {
      if (hostClass != 'rfc1918-lan-ip' &&
          hostClass != 'lan-ipv6' &&
          hostClass != 'lan-dns' &&
          hostClass != 'dns') {
        throw const AppFailure.validation(
          'WEAVE-LAN-UNREACHABLE: The local invite must point to a LAN-reachable address.',
        );
      }
      return;
    }

    if (hostClass != 'dns' &&
        hostClass != 'public-ip' &&
        hostClass != 'lan-dns') {
      throw const AppFailure.validation(
        'WEAVE-LINK-UNREACHABLE: The invite must point to a public app-start link or managed organization domain.',
      );
    }
  }

  String _hostClass(String host) {
    if (host.isEmpty ||
        host == 'localhost' ||
        host == 'host.docker.internal' ||
        host == 'docker.for.mac.localhost' ||
        host.endsWith('.local')) {
      return 'forbidden-local-only';
    }

    final v4Parts = host.split('.');
    if (v4Parts.length == 4 &&
        v4Parts.every((part) => int.tryParse(part) != null)) {
      final octets = v4Parts.map(int.parse).toList(growable: false);
      if (octets.any((octet) => octet < 0 || octet > 255)) {
        return 'forbidden-invalid-ip';
      }
      if (octets.first == 127 || octets.every((octet) => octet == 0)) {
        return 'forbidden-loopback';
      }
      final privateLan =
          octets.first == 10 ||
          (octets.first == 172 && octets[1] >= 16 && octets[1] <= 31) ||
          (octets.first == 192 && octets[1] == 168);
      return privateLan ? 'rfc1918-lan-ip' : 'public-ip';
    }

    if (host.contains(':')) {
      if (host == '::1' || host.startsWith('fe80:')) {
        return 'forbidden-loopback';
      }
      return host.startsWith('fd') || host.startsWith('fc')
          ? 'lan-ipv6'
          : 'public-ip';
    }

    if (host.endsWith('.lan') ||
        host.endsWith('.home') ||
        host.endsWith('.home.internal') ||
        host.endsWith('.internal')) {
      return 'lan-dns';
    }

    if (RegExp(r'^[a-z0-9-]+$').hasMatch(host)) {
      return 'forbidden-container-only-name';
    }

    return 'dns';
  }
}
