import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';

class ChatSecurityBanner extends StatelessWidget {
  const ChatSecurityBanner({super.key, required this.security});

  final ChatSecurityState security;

  @override
  Widget build(BuildContext context) {
    final message = switch (security.bootstrapState) {
      ChatSecurityBootstrapState.notInitialized ||
      ChatSecurityBootstrapState.partiallyInitialized =>
        'Encrypted Matrix rooms are available, but this account still needs initial security setup.',
      ChatSecurityBootstrapState.recoveryRequired =>
        'This device needs your Matrix recovery key before older encrypted messages can be trusted again.',
      _ when security.deviceVerificationState !=
          ChatDeviceVerificationState.verified =>
        'This device is not verified yet. Compare security emoji with another signed-in Matrix device.',
      _ when security.keyBackupState == ChatKeyBackupState.missing =>
        'Matrix key backup is still missing. Set it up before relying on encrypted chat recovery.',
      _ => null,
    };

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
                'Matrix security needs attention',
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
                child: const Text('Open security settings'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
