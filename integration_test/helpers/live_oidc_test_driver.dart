import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/domain/entities/oidc_constants.dart';
import 'package:weave/features/chat/data/services/matrix_auth_browser.dart';

import 'test_config.dart';

class LiveOidcTestDriver implements OidcClient, MatrixAuthBrowser {
  LiveOidcTestDriver({required TestConfig config}) : _config = config;

  final TestConfig _config;

  @override
  Future<OidcTokenBundle> authorizeAndExchangeCode(
    AuthConfiguration configuration,
  ) async {
    try {
      final discovery = await _readDiscovery(configuration.issuer);
      final authorizationEndpoint = _requireAbsoluteUri(
        discovery,
        'authorization_endpoint',
      );
      final tokenEndpoint = _requireAbsoluteUri(discovery, 'token_endpoint');
      final codeVerifier = _randomUrlSafe(64);
      final state = _randomUrlSafe(32);
      final nonce = _randomUrlSafe(32);
      final callbackUri = await _authenticateInBrowserLikeFlow(
        authorizationUri: authorizationEndpoint.replace(
          queryParameters: <String, String>{
            'client_id': configuration.clientId,
            'redirect_uri': oidcRedirectUri,
            'response_type': 'code',
            'scope': oidcDefaultScopes.join(' '),
            'state': state,
            'nonce': nonce,
            'code_challenge': _sha256UrlSafe(codeVerifier),
            'code_challenge_method': 'S256',
          },
        ),
        redirectUri: Uri.parse(oidcRedirectUri),
      );
      final code = callbackUri.queryParameters['code']?.trim();
      final returnedState = callbackUri.queryParameters['state']?.trim();
      if (code == null || code.isEmpty) {
        throw const AuthFailure.protocol(
          'OIDC sign-in callback did not include an authorization code.',
        );
      }
      if (returnedState != state) {
        throw const AuthFailure.protocol(
          'OIDC sign-in callback returned an unexpected state value.',
        );
      }

      final tokenResponse = await _sendForm(tokenEndpoint, <String, String>{
        'grant_type': 'authorization_code',
        'client_id': configuration.clientId,
        'code': code,
        'redirect_uri': oidcRedirectUri,
        'code_verifier': codeVerifier,
      });
      if (tokenResponse.statusCode != 200) {
        throw AuthFailure.protocol(
          'OIDC token exchange failed with HTTP ${tokenResponse.statusCode}.',
        );
      }
      final payload = _decodeObject(tokenResponse.body, 'OIDC token response');
      return _bundleFromTokenPayload(payload);
    } on AuthFailure {
      rethrow;
    } catch (error) {
      throw AuthFailure.unknown('Unable to complete sign-in.', cause: error);
    }
  }

  @override
  Future<OidcTokenBundle> refresh(
    AuthConfiguration configuration, {
    required String refreshToken,
  }) async {
    final discovery = await _readDiscovery(configuration.issuer);
    final tokenEndpoint = _requireAbsoluteUri(discovery, 'token_endpoint');
    final response = await _sendForm(tokenEndpoint, <String, String>{
      'grant_type': 'refresh_token',
      'client_id': configuration.clientId,
      'refresh_token': refreshToken,
      'scope': oidcDefaultScopes.join(' '),
    });
    if (response.statusCode != 200) {
      throw AuthFailure.protocol(
        'OIDC refresh failed with HTTP ${response.statusCode}.',
      );
    }
    final payload = _decodeObject(response.body, 'OIDC refresh response');
    return _bundleFromTokenPayload(payload);
  }

  @override
  Future<void> endSession(
    AuthConfiguration configuration, {
    required String idTokenHint,
  }) async {
    final discovery = await _readDiscovery(configuration.issuer);
    final endSessionEndpoint = discovery['end_session_endpoint'];
    if (endSessionEndpoint is! String || endSessionEndpoint.trim().isEmpty) {
      return;
    }
    final uri = Uri.parse(endSessionEndpoint).replace(
      queryParameters: <String, String>{
        'client_id': configuration.clientId,
        'id_token_hint': idTokenHint,
        'post_logout_redirect_uri': oidcPostLogoutRedirectUri,
      },
    );
    final client = _newHttpClient();
    try {
      final request = await client.getUrl(uri);
      request.followRedirects = false;
      final response = await request.close();
      await response.drain<List<int>>();
    } finally {
      client.close(force: true);
    }
  }

  @override
  Future<Uri> authenticate({
    required Uri authorizationUri,
    required Uri redirectUri,
  }) {
    return _authenticateInBrowserLikeFlow(
      authorizationUri: authorizationUri,
      redirectUri: redirectUri,
    );
  }

  Future<Uri> _authenticateInBrowserLikeFlow({
    required Uri authorizationUri,
    required Uri redirectUri,
  }) async {
    final client = _newHttpClient();
    try {
      final cookieJar = <_StoredCookie>[];
      Uri? previousUri;
      var nextUri = authorizationUri;
      while (true) {
        final response = await _open(client, nextUri, cookieJar);
        final location = response.headers.value(HttpHeaders.locationHeader);
        final body = await utf8.decodeStream(response);

        if (_isRedirect(response.statusCode) && location != null) {
          previousUri = nextUri;
          final redirected = nextUri.resolve(location);
          if (_matchesRedirect(redirected, redirectUri)) {
            return redirected;
          }
          nextUri = redirected;
          continue;
        }

        final loginForm = _tryParseLoginForm(body, nextUri);
        if (loginForm != null) {
          final postResponse = await _postForm(
            client,
            loginForm.action,
            <String, String>{
              ...loginForm.fields,
              'username': _config.username,
              'password': _config.password,
              'credentialId': '',
              'login': 'Sign In',
            },
            cookieJar,
            referer: previousUri ?? nextUri,
          );
          final postLocation = postResponse.headers.value(
            HttpHeaders.locationHeader,
          );
          final postBody = await utf8.decodeStream(postResponse);

          if (_isRedirect(postResponse.statusCode) && postLocation != null) {
            previousUri = loginForm.action;
            final redirected = loginForm.action.resolve(postLocation);
            if (_matchesRedirect(redirected, redirectUri)) {
              return redirected;
            }
            nextUri = redirected;
            continue;
          }

          if (postBody.contains('Invalid username or password') ||
              postBody.contains('Sign in to your account')) {
            throw StateError(
              'Live browser login did not complete successfully.',
            );
          }

          final followUpForm = _tryParseAutoSubmitForm(
            postBody,
            loginForm.action,
          );
          if (followUpForm != null) {
            nextUri = followUpForm.action;
            final followUpResponse = await _postForm(
              client,
              followUpForm.action,
              followUpForm.fields,
              cookieJar,
              referer: loginForm.action,
            );
            final followUpLocation = followUpResponse.headers.value(
              HttpHeaders.locationHeader,
            );
            await utf8.decodeStream(followUpResponse);
            if (_isRedirect(followUpResponse.statusCode) &&
                followUpLocation != null) {
              previousUri = followUpForm.action;
              final redirected = followUpForm.action.resolve(followUpLocation);
              if (_matchesRedirect(redirected, redirectUri)) {
                return redirected;
              }
              nextUri = redirected;
              continue;
            }
          }

          final snippet = postBody.replaceAll(RegExp(r'\s+'), ' ');
          throw StateError(
            'Unexpected response after submitting the login form '
            'to ${loginForm.action}. Status=${postResponse.statusCode} '
            'Body=${snippet.substring(0, snippet.length > 300 ? 300 : snippet.length)}',
          );
        }

        final followUpForm = _tryParseAutoSubmitForm(body, nextUri);
        if (followUpForm != null) {
          final postResponse = await _postForm(
            client,
            followUpForm.action,
            followUpForm.fields,
            cookieJar,
            referer: previousUri ?? nextUri,
          );
          final postLocation = postResponse.headers.value(
            HttpHeaders.locationHeader,
          );
          await utf8.decodeStream(postResponse);
          if (_isRedirect(postResponse.statusCode) && postLocation != null) {
            previousUri = followUpForm.action;
            final redirected = followUpForm.action.resolve(postLocation);
            if (_matchesRedirect(redirected, redirectUri)) {
              return redirected;
            }
            nextUri = redirected;
            continue;
          }
        }

        throw StateError(
          'Unable to continue the live browser flow for $authorizationUri. '
          'Expected a redirect or supported form on ${nextUri.toString()}.',
        );
      }
    } finally {
      client.close(force: true);
    }
  }

  Future<Map<String, dynamic>> _readDiscovery(Uri issuer) async {
    final client = _newHttpClient();
    try {
      final request = await client.getUrl(
        issuer.replace(
          pathSegments: [
            ...issuer.pathSegments.where((segment) => segment.isNotEmpty),
            '.well-known',
            'openid-configuration',
          ],
        ),
      );
      final response = await request.close();
      final body = await utf8.decodeStream(response);
      if (response.statusCode != 200) {
        throw AuthFailure.protocol(
          'Unable to read OIDC discovery (HTTP ${response.statusCode}).',
        );
      }
      return _decodeObject(body, 'OIDC discovery document');
    } finally {
      client.close(force: true);
    }
  }

  Future<_SimpleHttpResponse> _sendForm(
    Uri uri,
    Map<String, String> body,
  ) async {
    final client = _newHttpClient();
    try {
      final response = await _postForm(client, uri, body, <_StoredCookie>[]);
      final responseBody = await utf8.decodeStream(response);
      return _SimpleHttpResponse(response.statusCode, responseBody);
    } finally {
      client.close(force: true);
    }
  }

  Future<HttpClientResponse> _open(
    HttpClient client,
    Uri uri,
    List<_StoredCookie> cookieJar,
  ) async {
    final request = await client.getUrl(uri);
    request.followRedirects = false;
    request.headers.set(HttpHeaders.acceptHeader, 'text/html,application/json');
    request.headers.set(HttpHeaders.userAgentHeader, 'WeaveLiveOidcTest/1.0');
    _applyCookies(request, uri, cookieJar);
    final response = await request.close();
    _storeCookies(response, uri, cookieJar);
    return response;
  }

  Future<HttpClientResponse> _postForm(
    HttpClient client,
    Uri uri,
    Map<String, String> fields,
    List<_StoredCookie> cookieJar, {
    Uri? referer,
  }) async {
    final request = await client.postUrl(uri);
    request.followRedirects = false;
    request.headers.set(
      HttpHeaders.contentTypeHeader,
      'application/x-www-form-urlencoded',
    );
    request.headers.set(HttpHeaders.acceptHeader, 'text/html,application/json');
    request.headers.set(HttpHeaders.userAgentHeader, 'WeaveLiveOidcTest/1.0');
    if (referer != null) {
      request.headers.set(HttpHeaders.refererHeader, referer.toString());
      request.headers.set('origin', referer.origin);
    }
    _applyCookies(request, uri, cookieJar);
    request.write(_formEncode(fields));
    final response = await request.close();
    _storeCookies(response, uri, cookieJar);
    return response;
  }

  void _applyCookies(
    HttpClientRequest request,
    Uri uri,
    List<_StoredCookie> cookieJar,
  ) {
    for (final cookie in cookieJar) {
      if (cookie.matches(uri)) {
        request.cookies.add(cookie.toCookie());
      }
    }
  }

  void _storeCookies(
    HttpClientResponse response,
    Uri uri,
    List<_StoredCookie> cookieJar,
  ) {
    for (final cookie in response.cookies) {
      cookieJar.removeWhere(
        (existing) =>
            existing.name == cookie.name && existing.domain == cookie.domain,
      );
      cookieJar.add(_StoredCookie.fromCookie(cookie, uri));
    }
  }

  HttpClient _newHttpClient() {
    return HttpClient()
      ..badCertificateCallback = (cert, host, port) =>
          host == 'localhost' ||
          host == '127.0.0.1' ||
          host.endsWith('.localhost') ||
          host.endsWith('.weave.local');
  }

  bool _isRedirect(int statusCode) =>
      statusCode == 301 ||
      statusCode == 302 ||
      statusCode == 303 ||
      statusCode == 307 ||
      statusCode == 308;

  bool _matchesRedirect(Uri candidate, Uri expected) {
    return candidate.scheme == expected.scheme &&
        candidate.path == expected.path &&
        (expected.host.isEmpty || candidate.host == expected.host);
  }

  Map<String, dynamic> _decodeObject(String body, String label) {
    final decoded = jsonDecode(body);
    if (decoded is! Map<String, dynamic>) {
      throw StateError('$label was not a JSON object.');
    }
    return decoded;
  }

  Uri _requireAbsoluteUri(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is! String || value.trim().isEmpty) {
      throw StateError('Missing OIDC discovery field "$key".');
    }
    final uri = Uri.parse(value);
    if (!uri.isAbsolute) {
      throw StateError('OIDC discovery field "$key" was not absolute.');
    }
    return uri;
  }

  OidcTokenBundle _bundleFromTokenPayload(Map<String, dynamic> payload) {
    final accessToken = payload['access_token'];
    if (accessToken is! String || accessToken.isEmpty) {
      throw const AuthFailure.protocol(
        'OIDC response did not include an access token.',
      );
    }
    final expiresIn = payload['expires_in'];
    return OidcTokenBundle(
      accessToken: accessToken,
      refreshToken: payload['refresh_token'] as String?,
      idToken: payload['id_token'] as String?,
      expiresAt: expiresIn is int
          ? DateTime.now().toUtc().add(Duration(seconds: expiresIn))
          : null,
      tokenType: payload['token_type'] as String?,
      scopes: _scopesFrom(payload['scope']),
    );
  }

  List<String> _scopesFrom(Object? value) {
    if (value is String && value.trim().isNotEmpty) {
      return value.split(' ').where((scope) => scope.isNotEmpty).toList();
    }
    return oidcDefaultScopes;
  }

  _ParsedLoginForm? _tryParseLoginForm(String html, Uri baseUri) {
    final formMatch = RegExp(
      r'<form([^>]*)>([\s\S]*?)</form>',
      caseSensitive: false,
    ).firstMatch(html);
    if (formMatch == null) {
      return null;
    }
    final formAttributes = formMatch.group(1) ?? '';
    final formHtml = formMatch.group(0)!;
    if (!formHtml.contains('name="username"') ||
        !formHtml.contains('name="password"')) {
      return null;
    }
    return _ParsedLoginForm(
      action: _resolveFormAction(formAttributes, baseUri),
      fields: _parseFormFields(formHtml),
    );
  }

  _ParsedLoginForm? _tryParseAutoSubmitForm(String html, Uri baseUri) {
    final formMatches = RegExp(
      r'<form([^>]*)>([\s\S]*?)</form>',
      caseSensitive: false,
    ).allMatches(html);
    for (final match in formMatches) {
      final formAttributes = match.group(1) ?? '';
      final formHtml = match.group(0)!;
      if (formHtml.contains('name="username"') &&
          formHtml.contains('name="password"')) {
        continue;
      }
      final fields = _parseFormFields(formHtml);
      if (fields.isEmpty) {
        continue;
      }
      if (!RegExp(r'type="submit"', caseSensitive: false).hasMatch(formHtml) &&
          !formHtml.contains('Create Account') &&
          !formHtml.contains('Continue')) {
        continue;
      }
      return _ParsedLoginForm(
        action: _resolveFormAction(formAttributes, baseUri),
        fields: fields,
      );
    }
    return null;
  }

  Uri _resolveFormAction(String formAttributes, Uri baseUri) {
    final actionMatch = RegExp(
      r'action="([^"]*)"',
      caseSensitive: false,
    ).firstMatch(formAttributes);
    final rawAction = _htmlDecode(actionMatch?.group(1) ?? '').trim();
    if (rawAction.isEmpty) {
      return baseUri;
    }
    return baseUri.resolve(rawAction);
  }

  Map<String, String> _parseFormFields(String formHtml) {
    final fields = <String, String>{};
    final inputMatches = RegExp(
      r'<input([^>]*)>',
      caseSensitive: false,
    ).allMatches(formHtml);
    for (final match in inputMatches) {
      final attributes = match.group(1) ?? '';
      final name = _extractHtmlAttribute(attributes, 'name');
      if (name == null || name.isEmpty) {
        continue;
      }
      final type = (_extractHtmlAttribute(attributes, 'type') ?? 'text')
          .toLowerCase();
      final isChecked = RegExp(
        r'\schecked(?:\s|>|$)',
        caseSensitive: false,
      ).hasMatch(attributes);
      if ((type == 'checkbox' || type == 'radio') && !isChecked) {
        continue;
      }
      fields[name] = _extractHtmlAttribute(attributes, 'value') ?? 'on';
    }
    return fields;
  }

  String? _extractHtmlAttribute(String html, String name) {
    final match = RegExp(
      '$name="([^"]*)"',
      caseSensitive: false,
    ).firstMatch(html);
    final value = match?.group(1);
    if (value == null) {
      return null;
    }
    return _htmlDecode(value);
  }

  String _formEncode(Map<String, String> fields) {
    return fields.entries
        .map(
          (entry) =>
              '${Uri.encodeQueryComponent(entry.key)}=${Uri.encodeQueryComponent(entry.value)}',
        )
        .join('&');
  }

  String _htmlDecode(String value) {
    return value
        .replaceAll('&amp;', '&')
        .replaceAll('&#x2F;', '/')
        .replaceAll('&quot;', '"')
        .replaceAll('&#39;', "'");
  }

  String _randomUrlSafe(int length) {
    const chars =
        'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
    final random = Random.secure();
    return List<String>.generate(
      length,
      (_) => chars[random.nextInt(chars.length)],
    ).join();
  }

  String _sha256UrlSafe(String input) {
    final bytes = sha256.convert(utf8.encode(input)).bytes;
    return base64UrlEncode(bytes).replaceAll('=', '');
  }
}

class _ParsedLoginForm {
  const _ParsedLoginForm({required this.action, required this.fields});

  final Uri action;
  final Map<String, String> fields;
}

class _SimpleHttpResponse {
  const _SimpleHttpResponse(this.statusCode, this.body);

  final int statusCode;
  final String body;
}

class _StoredCookie {
  const _StoredCookie({
    required this.name,
    required this.value,
    required this.domain,
    required this.path,
    required this.secure,
  });

  factory _StoredCookie.fromCookie(Cookie cookie, Uri uri) {
    return _StoredCookie(
      name: cookie.name,
      value: cookie.value,
      domain: cookie.domain ?? uri.host,
      path: cookie.path ?? '/',
      secure: cookie.secure,
    );
  }

  final String name;
  final String value;
  final String domain;
  final String path;
  final bool secure;

  bool matches(Uri uri) {
    final normalizedDomain = domain.startsWith('.')
        ? domain.substring(1)
        : domain;
    final hostMatches =
        uri.host == normalizedDomain || uri.host.endsWith('.$normalizedDomain');
    final pathMatches = uri.path.startsWith(path);
    final schemeMatches = !secure || uri.scheme == 'https';
    return hostMatches && pathMatches && schemeMatches;
  }

  Cookie toCookie() {
    final cookie = Cookie(name, value)
      ..domain = domain
      ..path = path
      ..secure = secure;
    return cookie;
  }
}
