import 'package:riverpod/riverpod.dart';

import 'matrix_auth_browser_stub.dart'
    if (dart.library.io) 'matrix_auth_browser_io.dart'
    as impl;

abstract interface class MatrixAuthBrowser {
  Future<Uri> authenticate({
    required Uri authorizationUri,
    required Uri redirectUri,
  });
}

final matrixAuthBrowserProvider = Provider<MatrixAuthBrowser>(
  (ref) => impl.createMatrixAuthBrowser(),
);
