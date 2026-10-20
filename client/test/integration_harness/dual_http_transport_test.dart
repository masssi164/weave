import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../../integration_test/dual_ios_collaboration_test.dart'
    show NativeDualHttpOverrides, nativeDualProxyDirective;

Map<String, String> _environment(int port) => {
  for (final key in ['http_proxy', 'https_proxy', 'HTTP_PROXY', 'HTTPS_PROXY'])
    key: 'http://127.0.0.1:$port',
  'no_proxy': '',
  'NO_PROXY': '',
};

void main() {
  test('owned relay requires all matching settings and empty bypass lists', () {
    expect(
      nativeDualProxyDirective(_environment(12345)),
      'PROXY 127.0.0.1:12345',
    );
    for (final key in _environment(12345).keys) {
      final missing = _environment(12345)..remove(key);
      expect(() => nativeDualProxyDirective(missing), throwsStateError);
      final inconsistent = _environment(12345)..[key] = 'private-value';
      expect(() => nativeDualProxyDirective(inconsistent), throwsStateError);
    }
    for (final value in [
      'DIRECT',
      'http://localhost:12345',
      'https://127.0.0.1:12345',
      'http://127.0.0.1:0',
      'http://127.0.0.1:0123',
      'http://127.0.0.1:65536',
      'http://127.0.0.1:12345/',
      'http://127.0.0.1:12345?bypass=true',
      'http://user:secret@127.0.0.1:12345',
    ]) {
      final invalid = _environment(12345);
      for (final key in invalid.keys.take(4).toList()) {
        invalid[key] = value;
      }
      expect(() => nativeDualProxyDirective(invalid), throwsStateError);
    }
  });

  test(
    'real HTTP traffic uses the owned relay and fails when it closes',
    () async {
      final relay = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final received = <Uri>[];
      relay.listen((request) async {
        received.add(request.requestedUri);
        request.response.write('actual relay bytes');
        await request.response.close();
      });
      final previous = HttpOverrides.current;
      try {
        await HttpOverrides.runWithHttpOverrides(() async {
          final client = HttpClient()
            ..connectionTimeout = const Duration(seconds: 2);
          try {
            final target = Uri.parse(
              'http://unresolvable.invalid/user-api-probe',
            );
            final request = await client.getUrl(target);
            request.persistentConnection = false;
            final response = await request.close();
            expect(
              await response.transform(const SystemEncoding().decoder).join(),
              'actual relay bytes',
            );
            expect(received, [target]);
            await relay.close(force: true);
            await expectLater(
              client.getUrl(target),
              throwsA(isA<SocketException>()),
            );
          } finally {
            client.close(force: true);
          }
        }, NativeDualHttpOverrides(_environment(relay.port)));
        expect(HttpOverrides.current, same(previous));
      } finally {
        await relay.close(force: true);
      }
    },
  );
}
