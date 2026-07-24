import 'package:weave/core/failures/app_failure.dart';

class MemberHandoff {
  const MemberHandoff({
    required this.handoffRef,
    required this.runId,
    required this.organizationSlug,
    required this.workspaceSlug,
    required this.platformConfigUrl,
    required this.productBaseUrl,
  });

  final String handoffRef;
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

  /// Product/user-facing origin used for browser fallback copy.
  /// Do not derive provider internals from this origin.
  final Uri productBaseUrl;
}

/// Canonical, secret-free input for organization discovery.
///
/// A completion link or QR payload may carry a real [handoff]. A manually
/// entered server URI carries only the organization origin. Both forms resolve
/// the same public discovery document and never manufacture invitation data.
class OrganizationAccess {
  const OrganizationAccess({
    required this.organizationOrigin,
    required this.platformConfigUrl,
    this.handoff,
  });

  final Uri organizationOrigin;
  final Uri platformConfigUrl;
  final MemberHandoff? handoff;

  String get organizationLabel =>
      handoff?.organizationSlug ?? organizationOrigin.host;
}

class OrganizationAccessParser {
  const OrganizationAccessParser();

  OrganizationAccess parse(Uri uri) {
    final directOrigin = _directOrganizationOrigin(uri);
    if (directOrigin != null) {
      const handoffParser = MemberHandoffParser();
      handoffParser._validatePhoneReachable(directOrigin);
      return OrganizationAccess(
        organizationOrigin: directOrigin,
        platformConfigUrl: directOrigin.replace(path: '/api/platform/config'),
      );
    }

    final handoff = const MemberHandoffParser().parse(uri);
    return OrganizationAccess(
      organizationOrigin: handoff.productBaseUrl,
      platformConfigUrl: handoff.platformConfigUrl,
      handoff: handoff,
    );
  }

  Uri? _directOrganizationOrigin(Uri uri) {
    final rawOrigin = switch ((uri.scheme, uri.host, uri.path)) {
      ('', '', '/join') =>
        uri.queryParameters.length == 1
            ? uri.queryParameters['organization_origin']
            : null,
      ('https', _, '' || '/') when !uri.hasQuery && !uri.hasFragment =>
        uri.toString(),
      _ => null,
    };
    if (rawOrigin == null || rawOrigin.trim().isEmpty) {
      return null;
    }

    final parsed = Uri.tryParse(rawOrigin.trim());
    if (parsed == null ||
        !parsed.isAbsolute ||
        parsed.scheme != 'https' ||
        parsed.host.isEmpty ||
        parsed.userInfo.isNotEmpty ||
        parsed.hasQuery ||
        parsed.hasFragment ||
        (parsed.path.isNotEmpty && parsed.path != '/')) {
      throw const AppFailure.validation(
        'WEAVE-ORGANIZATION-ACCESS-INVALID: Enter a secure Weave organization origin without credentials, path, query, or fragment data.',
      );
    }
    return Uri(
      scheme: parsed.scheme,
      host: parsed.host,
      port: parsed.hasPort ? parsed.port : null,
      path: '/',
    );
  }
}

class MemberHandoffParser {
  const MemberHandoffParser();

  MemberHandoff parse(Uri uri) {
    _validateJoinEntrypoint(uri);
    final query = uri.queryParameters;
    final handoffRef = _requiredSafeRef(query, 'handoff_ref');
    final runId = _requiredSafeSlug(query, 'run_id', fallback: 'unknown-run');
    final org = _requiredSafeSlug(query, 'org');
    final workspace = _requiredSafeSlug(query, 'workspace');
    _rejectCredentialBearingQuery(query);

    final productBaseUrl = _productBaseUrlFrom(uri, query);
    _validatePhoneReachable(productBaseUrl);

    final platformConfigUrl = _platformConfigUrlFrom(
      uri,
      query,
      productBaseUrl,
    );
    _validatePhoneReachable(platformConfigUrl);

    return MemberHandoff(
      handoffRef: handoffRef,
      runId: runId,
      organizationSlug: org,
      workspaceSlug: workspace,
      platformConfigUrl: platformConfigUrl,
      productBaseUrl: productBaseUrl,
    );
  }

  void _validateJoinEntrypoint(Uri uri) {
    if (uri.scheme.isNotEmpty &&
        uri.scheme != 'https' &&
        uri.scheme != 'weave') {
      throw const AppFailure.validation(
        'WEAVE-HANDOFF-INVALID: The invite must use HTTPS or the Weave app link.',
      );
    }
    _rejectEmbeddedCredentials(uri, 'invite');

    final isCustomSchemeJoin =
        uri.scheme == 'weave' && (uri.path == '/join' || uri.host == 'join');
    final isWebJoin = uri.scheme == 'https' && uri.path == '/join';
    final isInAppJoin =
        uri.scheme.isEmpty && uri.host.isEmpty && uri.path == '/join';
    if (!isCustomSchemeJoin && !isWebJoin && !isInAppJoin) {
      throw const AppFailure.validation(
        'WEAVE-HANDOFF-INVALID: The invite must point to the Weave join route.',
      );
    }
  }

  Uri _productBaseUrlFrom(Uri uri, Map<String, String> query) {
    final explicit = query['product_base_url'];
    if (explicit != null && explicit.trim().isNotEmpty) {
      final parsed = _parseAbsoluteHttpUri(explicit, 'product_base_url');
      return Uri(
        scheme: parsed.scheme,
        host: parsed.host,
        port: parsed.hasPort ? parsed.port : null,
        path: '/',
      );
    }
    if (uri.scheme == 'weave' || uri.scheme.isEmpty) {
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

    final base = uri.scheme == 'weave' || uri.scheme.isEmpty
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
    _rejectEmbeddedCredentials(uri, fieldName);
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

  void _rejectEmbeddedCredentials(Uri uri, String fieldName) {
    if (uri.userInfo.isNotEmpty) {
      throw AppFailure.validation(
        'WEAVE-HANDOFF-SECRET-BLOCKED: $fieldName must not embed credentials.',
      );
    }
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
    final hostClass = _hostClass(host);
    if (hostClass == 'rfc1918-lan-ip' || hostClass == 'lan-ipv6') {
      throw const AppFailure.validation(
        'WEAVE-LAN-UNREACHABLE: Use the managed organization DNS name so the phone can validate the TLS identity.',
      );
    }
    if (hostClass.startsWith('forbidden')) {
      throw const AppFailure.validation(
        'WEAVE-LINK-UNREACHABLE: The invite does not point to a phone-reachable address.',
      );
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
        host == 'docker.for.mac.localhost') {
      return 'forbidden-local-only';
    }

    if (host == 'weave.test' || host.endsWith('.weave.test')) {
      return 'lan-dns';
    }

    if (host.endsWith('.local')) {
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

class MemberHandoffPayloadBuilder {
  const MemberHandoffPayloadBuilder();

  Uri inviteLink({
    required Uri productBaseUrl,
    required String handoffRef,
    required String organizationSlug,
    required String workspaceSlug,
    String runId = 'unknown-run',
  }) {
    final link = Uri(
      scheme: productBaseUrl.scheme,
      host: productBaseUrl.host,
      port: productBaseUrl.hasPort ? productBaseUrl.port : null,
      path: '/join',
      queryParameters: {
        'handoff_ref': handoffRef,
        'org': organizationSlug,
        'workspace': workspaceSlug,
        'run_id': runId,
      },
    );

    // Re-parse the generated link so invite links and QR payloads share the
    // same support-safe validation path as incoming mobile deep links.
    const MemberHandoffParser().parse(link);
    return link;
  }

  String qrPayload({
    required Uri productBaseUrl,
    required String handoffRef,
    required String organizationSlug,
    required String workspaceSlug,
    String runId = 'unknown-run',
  }) => inviteLink(
    productBaseUrl: productBaseUrl,
    handoffRef: handoffRef,
    organizationSlug: organizationSlug,
    workspaceSlug: workspaceSlug,
    runId: runId,
  ).toString();
}
