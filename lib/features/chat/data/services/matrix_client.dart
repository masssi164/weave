export 'package:weave/features/chat/data/services/matrix_client_interface.dart';

import 'package:riverpod/riverpod.dart';
import 'package:weave/features/chat/data/services/matrix_auth_browser.dart';
import 'package:weave/features/chat/data/services/matrix_client_interface.dart';

import 'matrix_client_io.dart'
    if (dart.library.js_interop) 'matrix_client_web.dart'
    as impl;

final matrixClientProvider = Provider<MatrixClient>((ref) {
  final client = impl.createMatrixClient(
    authBrowser: ref.watch(matrixAuthBrowserProvider),
  );
  ref.onDispose(() {
    client.dispose();
  });
  return client;
});
