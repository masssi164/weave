import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  test('native Keychain stores and restores a disposable value', () async {
    final storage = FlutterSecureStorage(
      mOptions: MacOsOptions(
        accountName: 'weave-native-keychain-probe-$pid',
        accessibility: KeychainAccessibility.first_unlock_this_device,
      ),
    );
    const key = 'native_probe';
    try {
      await storage.write(key: key, value: 'disposable-value');
      expect(await storage.read(key: key), 'disposable-value');
      debugPrint('NATIVE_KEYCHAIN_PROBE_RESULT status=passed');
    } on PlatformException catch (error) {
      final status = error.details is int ? error.details as int : null;
      debugPrint(
        'NATIVE_KEYCHAIN_PROBE_RESULT status=failed '
        'osStatus=${status ?? 'unavailable'}',
      );
      rethrow;
    } finally {
      try {
        await storage.delete(key: key);
      } on PlatformException {
        // The failing operation's status is the relevant diagnostic.
      }
    }
  });
}
