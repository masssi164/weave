import 'package:code_assets/code_assets.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../../hook/build.dart' as native_assets_hook;

void main() {
  test('iOS Cargo environment uses the native-assets target version', () {
    expect(
      native_assets_hook.rustCargoEnvironment(
        targetOS: OS.iOS,
        iOSTargetVersion: 13,
      ),
      const {'IPHONEOS_DEPLOYMENT_TARGET': '13.0'},
    );
  });

  test('non-iOS Cargo environment has no iOS deployment target', () {
    expect(
      native_assets_hook.rustCargoEnvironment(
        targetOS: OS.macOS,
        iOSTargetVersion: null,
      ),
      isEmpty,
    );
  });
}
