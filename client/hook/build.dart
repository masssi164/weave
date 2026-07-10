import 'package:flutter_rust_bridge_hooks/flutter_rust_bridge_hooks.dart';

void main(List<String> args) async {
  await build(args, (input, output) async {
    await const FlutterRustBridgeNativeAssetsBuilder(
      assetName:
          'integrations/rust_matrix_core/generated/frb_generated.io.dart',
      cratePath: '../rust/matrix-core',
      features: ['flutter'],
    ).run(input: input, output: output);
  });
}
