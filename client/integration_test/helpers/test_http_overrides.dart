import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:http/io_client.dart';

import 'live_test_tls.dart';

class TestHttpOverrides extends HttpOverrides {
  TestHttpOverrides({LiveTestTlsTrust? trust})
    : _trust = trust ?? compileTimeLiveTestTlsTrust;

  final LiveTestTlsTrust _trust;

  @override
  HttpClient createHttpClient(SecurityContext? context) {
    return super.createHttpClient(
      _trust.createSecurityContext(baseContext: context),
    );
  }
}

http.Client createTrustedTestHttpClient({LiveTestTlsTrust? trust}) {
  return IOClient(createLiveTestHttpClient(trust: trust));
}
