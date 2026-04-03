import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class ChatSecuritySettingsSection extends ConsumerWidget {
  const ChatSecuritySettingsSection({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final state = ref.watch(chatSecurityProvider);
    final security = state.security;
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l10n.chatSecuritySectionTitle,
          style: theme.textTheme.headlineSmall,
        ),
        const SizedBox(height: 12),
        Text(
          l10n.chatSecuritySectionDescription,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 16),
        if (state.isLoading)
          const LinearProgressIndicator()
        else if (security != null) ...[
          _StatusCard(
            title: 'Setup',
            value: _bootstrapLabel(security.bootstrapState),
            body: _bootstrapDescription(security),
          ),
          const SizedBox(height: 12),
          _StatusCard(
            title: 'Current device',
            value: _deviceLabel(security.deviceVerificationState),
            body: _deviceDescription(security.deviceVerificationState),
          ),
          const SizedBox(height: 12),
          _StatusCard(
            title: 'Recovery and key backup',
            value: _backupLabel(security.keyBackupState),
            body:
                'The recovery key is needed when this device is replaced, reinstalled, or loses local crypto secrets.',
          ),
          const SizedBox(height: 12),
          _StatusCard(
            title: 'Encrypted rooms',
            value: _roomReadinessLabel(security.roomEncryptionReadiness),
            body: security.hasEncryptedConversations
                ? 'Encrypted rooms already exist on this account. Warnings stay visible until trust and recovery are healthy.'
                : 'No encrypted rooms are known yet, but the account security state is still tracked here.',
          ),
          const SizedBox(height: 16),
          _ActionArea(state: state),
        ],
        if (state.failure != null) ...[
          const SizedBox(height: 16),
          Text(
            state.failure!.message,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.error,
            ),
          ),
        ],
      ],
    );
  }

  static String _bootstrapLabel(ChatSecurityBootstrapState state) {
    return switch (state) {
      ChatSecurityBootstrapState.signedOut => 'Matrix not connected',
      ChatSecurityBootstrapState.notInitialized => 'Setup required',
      ChatSecurityBootstrapState.partiallyInitialized => 'Setup incomplete',
      ChatSecurityBootstrapState.recoveryRequired => 'Recovery required',
      ChatSecurityBootstrapState.ready => 'Healthy',
      ChatSecurityBootstrapState.unavailable => 'Unavailable',
    };
  }

  static String _bootstrapDescription(ChatSecurityState security) {
    return switch (security.bootstrapState) {
      ChatSecurityBootstrapState.signedOut =>
        'Open Chat and connect Matrix before managing encryption.',
      ChatSecurityBootstrapState.notInitialized =>
        'Set up secret storage, cross-signing, and online key backup before trusting encrypted rooms.',
      ChatSecurityBootstrapState.partiallyInitialized =>
        'Some encryption parts exist, but recovery or cross-signing is still incomplete.',
      ChatSecurityBootstrapState.recoveryRequired =>
        'This account was set up before, but this device needs the recovery key or passphrase to reconnect safely.',
      ChatSecurityBootstrapState.ready =>
        'This device can use the current Matrix crypto identity and recovery setup.',
      ChatSecurityBootstrapState.unavailable =>
        'Matrix encryption is not available on this platform.',
    };
  }

  static String _deviceLabel(ChatDeviceVerificationState state) {
    return switch (state) {
      ChatDeviceVerificationState.verified => 'Verified',
      ChatDeviceVerificationState.unverified => 'Unverified',
      ChatDeviceVerificationState.blocked => 'Blocked',
      ChatDeviceVerificationState.unavailable => 'Unavailable',
    };
  }

  static String _deviceDescription(ChatDeviceVerificationState state) {
    return switch (state) {
      ChatDeviceVerificationState.verified =>
        'Another trusted Matrix device has verified this session.',
      ChatDeviceVerificationState.unverified =>
        'Compare security emoji or numbers with another signed-in Matrix device.',
      ChatDeviceVerificationState.blocked =>
        'This device is blocked or its trust chain is broken.',
      ChatDeviceVerificationState.unavailable =>
        'The current device key is not available yet.',
    };
  }

  static String _backupLabel(ChatKeyBackupState state) {
    return switch (state) {
      ChatKeyBackupState.unavailable => 'Unavailable',
      ChatKeyBackupState.missing => 'Missing',
      ChatKeyBackupState.recoveryRequired => 'Needs reconnect',
      ChatKeyBackupState.ready => 'Ready',
    };
  }

  static String _roomReadinessLabel(ChatRoomEncryptionReadiness state) {
    return switch (state) {
      ChatRoomEncryptionReadiness.unavailable => 'Unavailable',
      ChatRoomEncryptionReadiness.noEncryptedRooms => 'No encrypted rooms yet',
      ChatRoomEncryptionReadiness.encryptedRoomsNeedAttention =>
        'Encrypted rooms need attention',
      ChatRoomEncryptionReadiness.ready => 'Ready',
    };
  }
}

class _ActionArea extends ConsumerWidget {
  const _ActionArea({required this.state});

  final ChatSecurityUiState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final security = state.security;
    if (security == null) {
      return const SizedBox.shrink();
    }

    final notifier = ref.read(chatSecurityProvider.notifier);
    final buttons = <Widget>[];

    if (!security.isMatrixSignedIn) {
      return Text(
        'Matrix security actions unlock after the Matrix session is connected.',
        style: Theme.of(context).textTheme.bodyMedium,
      );
    }

    if (security.bootstrapState == ChatSecurityBootstrapState.notInitialized ||
        security.bootstrapState ==
            ChatSecurityBootstrapState.partiallyInitialized) {
      buttons.add(
        AccessibleButton(
          onPressed: state.isBusy
              ? null
              : () => _showRecoveryKeySetupDialog(context, notifier),
          semanticLabel: 'Set up Matrix encrypted chat',
          child: Text(state.isBusy ? 'Working…' : 'Set up encrypted chat'),
        ),
      );
    }

    if (security.bootstrapState == ChatSecurityBootstrapState.recoveryRequired ||
        security.keyBackupState == ChatKeyBackupState.recoveryRequired) {
      buttons.add(
        AccessibleButton(
          outlined: true,
          onPressed: state.isBusy
              ? null
              : () => _showRestoreDialog(context, notifier),
          semanticLabel: 'Reconnect encrypted chat with a recovery key',
          child: const Text('Reconnect with recovery key'),
        ),
      );
    }

    if (security.deviceVerificationState != ChatDeviceVerificationState.verified &&
        security.bootstrapState == ChatSecurityBootstrapState.ready) {
      buttons.add(
        AccessibleButton(
          outlined: true,
          onPressed: state.isBusy ? null : () => notifier.startVerification(),
          semanticLabel: 'Verify this Matrix device',
          child: const Text('Verify this device'),
        ),
      );
    }

    final verification = security.verificationSession;
    if (verification.phase == ChatVerificationPhase.incomingRequest) {
      buttons.addAll([
        AccessibleButton(
          onPressed: state.isBusy ? null : () => notifier.acceptVerification(),
          semanticLabel: 'Accept Matrix verification request',
          child: const Text('Accept verification'),
        ),
        AccessibleButton(
          outlined: true,
          onPressed: state.isBusy ? null : () => notifier.cancelVerification(),
          semanticLabel: 'Decline Matrix verification request',
          child: const Text('Decline'),
        ),
      ]);
    }

    if (verification.phase == ChatVerificationPhase.chooseMethod) {
      buttons.add(
        AccessibleButton(
          onPressed: state.isBusy ? null : () => notifier.startSasVerification(),
          semanticLabel: 'Compare Matrix security emoji',
          child: const Text('Compare security emoji'),
        ),
      );
    }

    if (verification.phase == ChatVerificationPhase.compareSas) {
      buttons.addAll([
        AccessibleButton(
          onPressed: state.isBusy
              ? null
              : () => notifier.confirmSas(matches: true),
          semanticLabel: 'Confirm that the Matrix security emoji match',
          child: const Text('Emoji match'),
        ),
        AccessibleButton(
          outlined: true,
          onPressed: state.isBusy
              ? null
              : () => notifier.confirmSas(matches: false),
          semanticLabel: 'Cancel because the Matrix security emoji do not match',
          child: const Text('They do not match'),
        ),
      ]);
    }

    if (verification.phase == ChatVerificationPhase.done ||
        verification.phase == ChatVerificationPhase.cancelled ||
        verification.phase == ChatVerificationPhase.failed) {
      buttons.add(
        AccessibleButton(
          outlined: true,
          onPressed: state.isBusy
              ? null
              : () => notifier.dismissVerificationResult(),
          semanticLabel: 'Dismiss the Matrix verification result',
          child: const Text('Dismiss'),
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (state.generatedRecoveryKey != null)
          _RecoveryKeyNotice(
            recoveryKey: state.generatedRecoveryKey!,
            onDismiss: notifier.clearRecoveryKeyNotice,
          ),
        if (state.lastActionMessage != null) ...[
          Text(state.lastActionMessage!),
          const SizedBox(height: 12),
        ],
        if (verification.message != null) ...[
          Text(verification.message!),
          const SizedBox(height: 12),
        ],
        if (verification.phase == ChatVerificationPhase.compareSas) ...[
          _SasSummary(verification: verification),
          const SizedBox(height: 12),
        ],
        if (buttons.isNotEmpty)
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: buttons,
          ),
        if (buttons.isEmpty && verification.phase == ChatVerificationPhase.none)
          Text(
            'No action is needed right now.',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
      ],
    );
  }

  Future<void> _showRecoveryKeySetupDialog(
    BuildContext context,
    ChatSecurityController notifier,
  ) async {
    final controller = TextEditingController();
    final passphrase = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Set up encrypted chat'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'You can optionally protect the Matrix recovery key with a memorable passphrase. Leave this blank to use a generated recovery key instead.',
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              obscureText: true,
              decoration: const InputDecoration(
                labelText: 'Optional passphrase',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(controller.text.trim()),
            child: const Text('Continue'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (!context.mounted || passphrase == null) {
      return;
    }
    await notifier.bootstrap(
      passphrase: passphrase.isEmpty ? null : passphrase,
    );
  }

  Future<void> _showRestoreDialog(
    BuildContext context,
    ChatSecurityController notifier,
  ) async {
    final controller = TextEditingController();
    final value = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Reconnect encrypted chat'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Enter the Matrix recovery key or recovery passphrase that was created when encrypted chat was first set up.',
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              decoration: const InputDecoration(
                labelText: 'Recovery key or passphrase',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(controller.text.trim()),
            child: const Text('Reconnect'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (!context.mounted || value == null || value.isEmpty) {
      return;
    }
    await notifier.restore(recoveryKeyOrPassphrase: value);
  }
}

class _RecoveryKeyNotice extends StatelessWidget {
  const _RecoveryKeyNotice({
    required this.recoveryKey,
    required this.onDismiss,
  });

  final String recoveryKey;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Card(
      color: theme.colorScheme.tertiaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.chatSecurityRecoveryKeyTitle,
              style: theme.textTheme.titleMedium?.copyWith(
                color: theme.colorScheme.onTertiaryContainer,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              l10n.chatSecurityRecoveryKeyDescription,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onTertiaryContainer,
              ),
            ),
            const SizedBox(height: 12),
            SelectableText(
              recoveryKey,
              style: theme.textTheme.bodyLarge?.copyWith(
                fontFeatures: <FontFeature>[FontFeature.tabularFigures()],
              ),
            ),
            const SizedBox(height: 12),
            TextButton(
              onPressed: onDismiss,
              child: const Text('I saved it'),
            ),
          ],
        ),
      ),
    );
  }
}

class _SasSummary extends StatelessWidget {
  const _SasSummary({required this.verification});

  final ChatVerificationSession verification;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      label: [
        'Security emoji',
        ...verification.sasEmojis.map((emoji) => '${emoji.label} ${emoji.symbol}'),
        if (verification.sasNumbers.isNotEmpty)
          'Security numbers ${verification.sasNumbers.join(', ')}',
      ].join('. '),
      child: ExcludeSemantics(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (verification.sasEmojis.isNotEmpty)
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: verification.sasEmojis
                    .map(
                      (emoji) => Chip(
                        label: Text('${emoji.symbol} ${emoji.label}'),
                      ),
                    )
                    .toList(growable: false),
              ),
            if (verification.sasNumbers.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(
                verification.sasNumbers.join(' • '),
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _StatusCard extends StatelessWidget {
  const _StatusCard({
    required this.title,
    required this.value,
    required this.body,
  });

  final String title;
  final String value;
  final String body;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        title: Text(title),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Text(body),
        ),
        trailing: Text(value),
      ),
    );
  }
}
