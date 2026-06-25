import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

String authFailureMessage(AppLocalizations l10n, AuthFailure failure) {
  final message = failure.message.toLowerCase();
  if (message.contains('offline tokens not allowed') ||
      message.contains('offline_access')) {
    return l10n.signInOfflineSessionNotAllowed;
  }

  return switch (failure.type) {
    AuthFailureType.cancelled => l10n.signInCancelled,
    AuthFailureType.configuration => l10n.signInConfigurationFailure,
    AuthFailureType.protocol => l10n.signInProtocolFailure,
    AuthFailureType.storage => l10n.signInStorageFailure,
    AuthFailureType.unsupportedPlatform => l10n.signInUnsupportedPlatform,
    AuthFailureType.unknown => l10n.signInUnknownFailure,
  };
}
