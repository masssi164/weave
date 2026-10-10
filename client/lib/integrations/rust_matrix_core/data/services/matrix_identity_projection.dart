import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

/// Matches the server's issuer+subject account reference and Matrix authority.
String matrixUserIdForMember(Uri issuer, String subject, Uri homeserver) {
  final issuerText = issuer.toString();
  if (issuerText.isEmpty ||
      issuerText.contains('#') ||
      subject.isEmpty ||
      subject.contains('#') ||
      subject.runes.any((rune) => rune <= 32)) {
    throw const ChatFailure.sessionRequired('M_WEAVE_MATRIX_IDENTITY_INVALID');
  }
  final key = 'issuer+subject:$issuerText#$subject';
  final digest = sha256.convert(utf8.encode(key)).bytes;
  final localpart = digest
      .take(16)
      .map((byte) => byte.toRadixString(16).padLeft(2, '0'))
      .join();
  return '@acct_$localpart:${homeserver.host}';
}
