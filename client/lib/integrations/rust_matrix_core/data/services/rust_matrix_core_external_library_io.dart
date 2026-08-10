import 'dart:io';

import 'package:flutter_rust_bridge/flutter_rust_bridge_for_generated.dart';

Future<ExternalLibrary?> loadRustMatrixCoreDevelopmentLibrary() async {
  final environmentDirectory =
      Platform.environment['FRB_DART_LOAD_EXTERNAL_LIBRARY_NATIVE_LIB_DIR'];
  final libraryName = switch (Platform.operatingSystem) {
    'macos' => 'libweave_matrix_client.dylib',
    'linux' || 'android' => 'libweave_matrix_client.so',
    'windows' => 'weave_matrix_client.dll',
    _ => '',
  };
  if (libraryName.isEmpty) {
    return null;
  }

  final candidates = <String>[
    if (environmentDirectory != null && environmentDirectory.trim().isNotEmpty)
      '${environmentDirectory.trim()}/$libraryName',
    'build/native_assets/${Platform.operatingSystem}/$libraryName',
    '../target/release/$libraryName',
  ];
  for (final candidate in candidates) {
    if (File(candidate).existsSync()) {
      return ExternalLibrary.open(File(candidate).absolute.path);
    }
  }
  return null;
}
