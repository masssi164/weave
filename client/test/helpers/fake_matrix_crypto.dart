import 'package:weave/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

class FakeMatrixCryptoSessionPort implements MatrixCryptoSessionPort {
  FakeMatrixCryptoSessionPort({
    this.session = const MatrixCryptoSession(
      profileKey: 'profile-key',
      userId: '@user:api.weave.test',
      deviceId: 'WEAVE0123456789abcdef0123456789abcdef0123',
    ),
  });

  final MatrixCryptoSession session;
  final List<bool> synchronizeValues = <bool>[];
  final List<bool> interactiveValues = <bool>[];
  int disposeCalls = 0;
  int endCalls = 0;
  int removeCalls = 0;

  @override
  Future<MatrixCryptoSession> open({
    bool synchronize = true,
    bool allowInteractiveSignIn = true,
  }) async {
    synchronizeValues.add(synchronize);
    interactiveValues.add(allowInteractiveSignIn);
    return session;
  }

  @override
  Future<void> disposePreservingCryptoState() async {
    disposeCalls += 1;
  }

  @override
  Future<void> endSession() async {
    endCalls += 1;
  }

  @override
  Future<void> removeForExplicitAccountRemoval() async {
    removeCalls += 1;
  }
}

class FakeRustMatrixCoreBridge extends RustMatrixCoreBridge {
  List<RustMatrixEncryptedRoom> rooms = const <RustMatrixEncryptedRoom>[];
  final Map<String, List<RustMatrixMessageProjection>> messages =
      <String, List<RustMatrixMessageProjection>>{};
  RustMatrixSecurityProjection security = const RustMatrixSecurityProjection(
    signedIn: true,
    recoveryState: 'enabled',
    crossSigningReady: true,
    deviceVerified: true,
    accountVerified: true,
    encryptedRoomCount: 1,
    verification: RustMatrixVerificationProjection(
      phase: 'none',
      sasNumbers: <int>[],
      sasEmojis: <Map<String, dynamic>>[],
    ),
  );
  RustMatrixVerificationProjection verification =
      const RustMatrixVerificationProjection(
        phase: 'none',
        sasNumbers: <int>[],
        sasEmojis: <Map<String, dynamic>>[],
      );
  final List<Map<String, String>> oauthStarts = <Map<String, String>>[];
  final List<Map<String, String>> oauthActivations = <Map<String, String>>[];
  final List<Map<String, String>> oauthRestores = <Map<String, String>>[];
  final List<Map<String, String>> oauthEnds = <Map<String, String>>[];
  bool remoteRevocationConfirmed = true;
  bool failOAuthEnd = false;
  final List<String> oauthAborts = <String>[];
  String oauthUserId = '@user:matrix.test';
  String? oauthDeviceIdOverride;
  final List<String> syncProfiles = <String>[];
  int expiredSyncsRemaining = 0;
  final List<Map<String, String>> sentMessages = <Map<String, String>>[];
  final List<Map<String, String>> createdRooms = <Map<String, String>>[];
  final List<Map<String, String>> receipts = <Map<String, String>>[];
  final List<String> disposedProfiles = <String>[];
  String recoveryKey = 'recovery-key';

  @override
  Future<RustMatrixOAuthAuthorization> startOAuth({
    required String loginKey,
    required String homeserverUrl,
    required String expectedIssuer,
    required String clientId,
    required String deviceId,
    required String redirectUri,
  }) async {
    oauthStarts.add(<String, String>{
      'loginKey': loginKey,
      'homeserverUrl': homeserverUrl,
      'expectedIssuer': expectedIssuer,
      'clientId': clientId,
      'deviceId': deviceId,
      'redirectUri': redirectUri,
    });
    return RustMatrixOAuthAuthorization(
      authorizationUrl: Uri.parse(
        'https://mas.matrix.test/authorize?state=matrix-state',
      ),
      state: 'matrix-state',
    );
  }

  @override
  Future<RustMatrixOAuthIdentity> finishOAuth({
    required String loginKey,
    required Uri callbackUrl,
  }) async => RustMatrixOAuthIdentity(
    userId: oauthUserId,
    deviceId: oauthDeviceIdOverride ?? oauthStarts.last['deviceId']!,
  );

  @override
  Future<void> abortOAuth({required String loginKey}) async {
    oauthAborts.add(loginKey);
  }

  @override
  Future<void> activateOAuth({
    required String loginKey,
    required String profileKey,
    required String storePath,
    required String storePassphrase,
  }) async {
    oauthActivations.add(<String, String>{
      'loginKey': loginKey,
      'profileKey': profileKey,
      'storePath': storePath,
      'storePassphrase': storePassphrase,
    });
  }

  @override
  Future<void> restoreOAuth({
    required String profileKey,
    required String homeserverUrl,
    required String userId,
    required String deviceId,
    required String storePath,
    required String storePassphrase,
  }) async {
    oauthRestores.add(<String, String>{
      'profileKey': profileKey,
      'homeserverUrl': homeserverUrl,
      'userId': userId,
      'deviceId': deviceId,
      'storePath': storePath,
      'storePassphrase': storePassphrase,
    });
  }

  @override
  Future<bool> endOAuth({
    required String profileKey,
    required String storePath,
  }) async {
    oauthEnds.add(<String, String>{
      'profileKey': profileKey,
      'storePath': storePath,
    });
    if (failOAuthEnd) {
      throw const RustMatrixCoreBridgeException('M_WEAVE_MATRIX_OAUTH_LOGOUT');
    }
    return remoteRevocationConfirmed;
  }

  @override
  Future<RustMatrixCoreBridgeDescriptor> descriptor({
    String serverName = 'api.weave.test',
  }) async {
    return RustMatrixCoreBridgeDescriptor(
      protocolSurface: 'matrix-client-server-facade',
      oidcGatekeeper: 'spring-boot-resource-server',
      northboundHomeserverDependency: false,
      rustProtocolCore: 'ruma-matrix-sdk',
      serverJniBoundary: 'server-jni-wrapper',
      flutterBridgeBoundary: 'flutter-rust-bridge',
      nativeLinked: true,
      serverName: serverName,
      supportedMatrixVersions: const <String>['v1.18'],
      supportedEndpoints: const <String>[],
    );
  }

  @override
  Future<void> syncClient({required String profileKey}) async {
    syncProfiles.add(profileKey);
    if (expiredSyncsRemaining > 0) {
      expiredSyncsRemaining -= 1;
      throw const RustMatrixCoreBridgeException(
        'M_WEAVE_MATRIX_SESSION_EXPIRED',
      );
    }
  }

  @override
  Future<List<RustMatrixEncryptedRoom>> loadEncryptedRooms({
    required String profileKey,
  }) async => rooms;

  @override
  Future<RustMatrixEncryptedRoom> createEncryptedRoom({
    required String profileKey,
    required String title,
  }) async {
    createdRooms.add(<String, String>{
      'profileKey': profileKey,
      'title': title,
    });
    return RustMatrixEncryptedRoom(
      roomId: '!created:api.weave.test',
      title: title,
      unreadCount: 0,
      encrypted: true,
    );
  }

  @override
  Future<List<RustMatrixMessageProjection>> loadEncryptedRoomMessages({
    required String profileKey,
    required String roomId,
    int limit = 100,
  }) async => messages[roomId] ?? const <RustMatrixMessageProjection>[];

  @override
  Future<String> sendEncryptedText({
    required String profileKey,
    required String roomId,
    required String body,
  }) async {
    sentMessages.add(<String, String>{
      'profileKey': profileKey,
      'roomId': roomId,
      'body': body,
    });
    return r'$sent:api.weave.test';
  }

  @override
  Future<void> markRead({
    required String profileKey,
    required String roomId,
    required String eventId,
  }) async {
    receipts.add(<String, String>{
      'profileKey': profileKey,
      'roomId': roomId,
      'eventId': eventId,
    });
  }

  @override
  Future<RustMatrixSecurityProjection> loadSecurityState({
    required String profileKey,
  }) async => security;

  @override
  Future<String> bootstrapRecovery({
    required String profileKey,
    String passphrase = '',
  }) async => recoveryKey;

  @override
  Future<void> recover({
    required String profileKey,
    required String recoveryKeyOrPassphrase,
  }) async {}

  @override
  Future<RustMatrixVerificationProjection> startVerification({
    required String profileKey,
  }) async => verification;

  @override
  Future<RustMatrixVerificationProjection> acceptVerification({
    required String profileKey,
  }) async => verification;

  @override
  Future<RustMatrixVerificationProjection> startSas({
    required String profileKey,
  }) async => verification;

  @override
  Future<RustMatrixVerificationProjection> confirmSas({
    required String profileKey,
    required bool matches,
  }) async => verification;

  @override
  Future<RustMatrixVerificationProjection> cancelVerification({
    required String profileKey,
  }) async => verification;

  @override
  Future<void> dismissVerification({required String profileKey}) async {}

  @override
  Future<void> disposeClient({required String profileKey}) async {
    disposedProfiles.add(profileKey);
  }
}
