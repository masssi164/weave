import 'dart:math';

import 'package:weave/core/persistence/secure_store.dart';

const matrixDeviceIdentityStorageKey = 'matrix_device_identity_v1';
const _matrixDeviceIdPrefix = 'WEAVE';

class MatrixDeviceIdentityRepository {
  MatrixDeviceIdentityRepository({
    required SecureStore secureStore,
    Random? random,
  }) : _secureStore = secureStore,
       _random = random ?? Random.secure();

  final SecureStore _secureStore;
  final Random _random;

  Future<String> loadOrCreate() async {
    final stored = await _secureStore.read(matrixDeviceIdentityStorageKey);
    if (stored != null && _isValid(stored)) {
      return stored;
    }

    final generated = _generate();
    await _secureStore.write(matrixDeviceIdentityStorageKey, generated);
    return generated;
  }

  Future<void> removeForExplicitAccountRemoval() {
    return _secureStore.delete(matrixDeviceIdentityStorageKey);
  }

  String _generate() {
    final bytes = List<int>.generate(18, (_) => _random.nextInt(256));
    final suffix = bytes
        .map((value) => value.toRadixString(16).padLeft(2, '0'))
        .join();
    return '$_matrixDeviceIdPrefix$suffix';
  }

  bool _isValid(String value) {
    return RegExp('^$_matrixDeviceIdPrefix[0-9a-f]{36}\$').hasMatch(value);
  }
}
