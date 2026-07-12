import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

const _liveTestExtraRootEnabled = bool.fromEnvironment(
  'WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_ENABLED',
  defaultValue: false,
);
const _liveTestExtraRootBase64 = String.fromEnvironment(
  'WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_BASE64',
  defaultValue: '',
);
const maximumLiveTestRootPemBytes = 64 * 1024;
const _maximumEncodedRootCharacters =
    ((maximumLiveTestRootPemBytes + 2) ~/ 3) * 4 + 1024;

class LiveTestTlsConfigurationException implements Exception {
  const LiveTestTlsConfigurationException();

  static const code = 'WEAVE_LIVE_TLS_ROOT_INVALID';

  @override
  String toString() => code;
}

/// Explicit TLS trust for live tests without disabling native verification.
///
/// The returned context retains platform roots. When configured, the bounded
/// local CA is added as another trust anchor. Dart's native TLS stack still
/// validates the complete certificate chain and the requested URI hostname.
class LiveTestTlsTrust {
  LiveTestTlsTrust._(this._extraRootPem);

  factory LiveTestTlsTrust.fromCompileTime() {
    return LiveTestTlsTrust.fromEncodedRoot(
      enabled: _liveTestExtraRootEnabled,
      encodedRoot: _liveTestExtraRootBase64,
    );
  }

  factory LiveTestTlsTrust.fromEncodedRoot({
    required bool enabled,
    required String encodedRoot,
  }) {
    if (!enabled) {
      return LiveTestTlsTrust._(null);
    }
    final pem = _decodeAndValidatePem(encodedRoot);
    final trust = LiveTestTlsTrust._(pem);
    // Force native certificate parsing during configuration, before any live
    // request can be created.
    trust.createSecurityContext();
    return trust;
  }

  final Uint8List? _extraRootPem;

  bool get usesExtraRoot => _extraRootPem != null;

  SecurityContext createSecurityContext({SecurityContext? baseContext}) {
    final context = baseContext ?? SecurityContext(withTrustedRoots: true);
    final extraRootPem = _extraRootPem;
    if (extraRootPem == null) {
      return context;
    }
    try {
      context.setTrustedCertificatesBytes(extraRootPem);
      return context;
    } on Object {
      throw const LiveTestTlsConfigurationException();
    }
  }
}

final compileTimeLiveTestTlsTrust = LiveTestTlsTrust.fromCompileTime();

HttpClient createLiveTestHttpClient({LiveTestTlsTrust? trust}) {
  final effectiveTrust = trust ?? compileTimeLiveTestTlsTrust;
  return HttpClient(context: effectiveTrust.createSecurityContext());
}

Uint8List _decodeAndValidatePem(String encodedRoot) {
  final trimmed = encodedRoot.trim();
  if (trimmed.isEmpty || trimmed.length > _maximumEncodedRootCharacters) {
    throw const LiveTestTlsConfigurationException();
  }

  late Uint8List pem;
  try {
    pem = base64Decode(trimmed);
  } on Object {
    throw const LiveTestTlsConfigurationException();
  }
  if (pem.isEmpty || pem.length > maximumLiveTestRootPemBytes) {
    throw const LiveTestTlsConfigurationException();
  }

  late String text;
  try {
    text = utf8.decode(pem);
  } on Object {
    throw const LiveTestTlsConfigurationException();
  }
  if (text.contains('PRIVATE KEY')) {
    throw const LiveTestTlsConfigurationException();
  }
  final certificateBlock = RegExp(
    r'-----BEGIN CERTIFICATE-----\s*([A-Za-z0-9+/=\s]+?)\s*-----END CERTIFICATE-----',
    multiLine: true,
  );
  final matches = certificateBlock.allMatches(text).toList(growable: false);
  if (matches.isEmpty ||
      text.replaceAll(certificateBlock, '').trim().isNotEmpty) {
    throw const LiveTestTlsConfigurationException();
  }
  for (final match in matches) {
    try {
      final der = base64Decode(match.group(1)!.replaceAll(RegExp(r'\s'), ''));
      if (der.isEmpty) {
        throw const LiveTestTlsConfigurationException();
      }
    } on LiveTestTlsConfigurationException {
      rethrow;
    } on Object {
      throw const LiveTestTlsConfigurationException();
    }
  }
  return pem;
}
