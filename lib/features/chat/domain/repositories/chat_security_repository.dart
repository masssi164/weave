import 'package:weave/features/chat/domain/entities/chat_security_state.dart';

abstract interface class ChatSecurityRepository {
  Stream<ChatVerificationSession> watchVerificationUpdates();

  Future<ChatSecurityState> loadSecurityState({bool refresh = false});

  Future<String> bootstrapSecurity({String? passphrase});

  Future<void> restoreSecurity({required String recoveryKeyOrPassphrase});

  Future<void> startVerification();

  Future<void> acceptVerification();

  Future<void> startSasVerification();

  Future<void> confirmSas({required bool matches});

  Future<void> cancelVerification();

  Future<void> dismissVerificationResult();
}
