import 'package:code_assets/code_assets.dart';
import 'package:flutter_rust_bridge_hooks/flutter_rust_bridge_hooks.dart';

void main(List<String> args) async {
  await build(args, (input, output) async {
    final cargoEnvironment = rustCargoEnvironment(
      targetOS: input.config.code.targetOS,
      iOSTargetVersion: input.config.code.targetOS == OS.iOS
          ? input.config.code.iOS.targetVersion
          : null,
    );
    await FlutterRustBridgeNativeAssetsBuilder(
      assetName:
          'integrations/rust_matrix_core/generated/frb_generated.io.dart',
      cratePath: '../rust/matrix-core',
      features: ['flutter'],
      extraCargoEnvironmentVariables: cargoEnvironment,
    ).run(input: input, output: output);
  });
}

Map<String, String> rustCargoEnvironment({
  required OS targetOS,
  required int? iOSTargetVersion,
}) {
  if (targetOS != OS.iOS) {
    return const {};
  }
  if (iOSTargetVersion == null) {
    throw ArgumentError.notNull('iOSTargetVersion');
  }
  return {'IPHONEOS_DEPLOYMENT_TARGET': '$iOSTargetVersion.0'};
}
