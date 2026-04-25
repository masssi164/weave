import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:http/io_client.dart';

class TestHttpOverrides extends HttpOverrides {
  TestHttpOverrides();

  static bool isTrustedTestHost(String host) {
    final normalized = host.toLowerCase();
    return normalized == '127.0.0.1' ||
        normalized == 'localhost' ||
        normalized.endsWith('.localhost') ||
        normalized.endsWith('.weave.local');
  }

  @override
  HttpClient createHttpClient(SecurityContext? context) {
    final client = super.createHttpClient(context);
    client.badCertificateCallback = (cert, host, port) {
      return isTrustedTestHost(host);
    };
    return client;
  }
}

http.Client createTrustedTestHttpClient() {
  final ioClient = HttpClient()
    ..badCertificateCallback = (cert, host, port) {
      return TestHttpOverrides.isTrustedTestHost(host);
    };
  return IOClient(ioClient);
}
