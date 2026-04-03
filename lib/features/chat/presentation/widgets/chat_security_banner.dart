import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class ChatSecurityBanner extends StatelessWidget {
  const ChatSecurityBanner({super.key, required this.security});

  final ChatSecurityState security;

  static String? messageForSecurity(
    AppLocalizations l10n,
    ChatSecurityState security,
  ) {
    return switch (security.bootstrapState) {
      ChatSecurityBootstrapState.notInitialized ||
      ChatSecurityBootstrapState.partiallyInitialized =>
        l10n.chatSecurityBannerSetupMessage,
      ChatSecurityBootstrapState.recoveryRequired =>
        l10n.chatSecurityBannerRecoveryMessage,
      _ when security.accountVerificationState ==
              ChatAccountVerificationState.verificationRequired ||
          security.deviceVerificationState !=
              ChatDeviceVerificationState.verified =>
        l10n.chatSecurityBannerVerificationMessage,
      _ when security.keyBackupState == ChatKeyBackupState.missing =>
        l10n.chatSecurityBannerMissingBackupMessage,
      _ => null,
    };
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final message = messageForSecurity(l10n, security);

    if (message == null) {
      return const SizedBox.shrink();
    }

    final theme = Theme.of(context);

    return Semantics(
      container: true,
      liveRegion: true,
      child: Card(
        color: theme.colorScheme.errorContainer,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                l10n.chatSecurityBannerTitle,
                style: theme.textTheme.titleMedium?.copyWith(
                  color: theme.colorScheme.onErrorContainer,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                message,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onErrorContainer,
                ),
              ),
              const SizedBox(height: 12),
              TextButton(
                onPressed: () => context.go(AppRoutes.settings),
                child: Text(l10n.chatSecurityOpenSettingsButton),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
