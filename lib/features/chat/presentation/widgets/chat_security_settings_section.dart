import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
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
            title: l10n.chatSecuritySetupCardTitle,
            value: _bootstrapLabel(l10n, security.bootstrapState),
            body: _bootstrapDescription(l10n, security),
          ),
          const SizedBox(height: 12),
          _StatusCard(
            title: l10n.chatSecurityCurrentDeviceCardTitle,
            value: _deviceLabel(l10n, security.deviceVerificationState),
            body: _deviceDescription(
              l10n,
              security.deviceVerificationState,
            ),
          ),
          const SizedBox(height: 12),
          _StatusCard(
            title: l10n.chatSecurityRecoveryCardTitle,
            value: _backupLabel(l10n, security.keyBackupState),
            body: l10n.chatSecurityRecoveryCardBody,
          ),
          const SizedBox(height: 12),
          _StatusCard(
            title: l10n.chatSecurityEncryptedRoomsCardTitle,
            value: _roomReadinessLabel(l10n, security.roomEncryptionReadiness),
            body: security.hasEncryptedConversations
                ? l10n.chatSecurityEncryptedRoomsCardBodyExisting
                : l10n.chatSecurityEncryptedRoomsCardBodyNone,
          ),
          const SizedBox(height: 16),
          _ActionArea(state: state, l10n: l10n),
        ],
        if (state.failure != null) ...[
          const SizedBox(height: 16),
          Text(
            _failureMessage(l10n, state.failure!),
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.error,
            ),
          ),
        ],
      ],
    );
  }

  static String _bootstrapLabel(
    AppLocalizations l10n,
    ChatSecurityBootstrapState state,
  ) {
    return switch (state) {
      ChatSecurityBootstrapState.signedOut => l10n.chatSecurityStatusSignedOut,
      ChatSecurityBootstrapState.notInitialized =>
        l10n.chatSecurityStatusSetupRequired,
      ChatSecurityBootstrapState.partiallyInitialized =>
        l10n.chatSecurityStatusSetupIncomplete,
      ChatSecurityBootstrapState.recoveryRequired =>
        l10n.chatSecurityStatusRecoveryRequired,
      ChatSecurityBootstrapState.ready => l10n.chatSecurityStatusHealthy,
      ChatSecurityBootstrapState.unavailable =>
        l10n.chatSecurityStatusUnavailable,
    };
  }

  static String _bootstrapDescription(
    AppLocalizations l10n,
    ChatSecurityState security,
  ) {
    return switch (security.bootstrapState) {
      ChatSecurityBootstrapState.signedOut =>
        l10n.chatSecuritySetupDescriptionSignedOut,
      ChatSecurityBootstrapState.notInitialized =>
        l10n.chatSecuritySetupDescriptionNotInitialized,
      ChatSecurityBootstrapState.partiallyInitialized =>
        l10n.chatSecuritySetupDescriptionPartiallyInitialized,
      ChatSecurityBootstrapState.recoveryRequired =>
        l10n.chatSecuritySetupDescriptionRecoveryRequired,
      ChatSecurityBootstrapState.ready =>
        l10n.chatSecuritySetupDescriptionReady,
      ChatSecurityBootstrapState.unavailable =>
        l10n.chatSecuritySetupDescriptionUnavailable,
    };
  }

  static String _deviceLabel(
    AppLocalizations l10n,
    ChatDeviceVerificationState state,
  ) {
    return switch (state) {
      ChatDeviceVerificationState.verified => l10n.chatSecurityStatusVerified,
      ChatDeviceVerificationState.unverified =>
        l10n.chatSecurityStatusUnverified,
      ChatDeviceVerificationState.blocked => l10n.chatSecurityStatusBlocked,
      ChatDeviceVerificationState.unavailable =>
        l10n.chatSecurityStatusUnavailable,
    };
  }

  static String _deviceDescription(
    AppLocalizations l10n,
    ChatDeviceVerificationState state,
  ) {
    return switch (state) {
      ChatDeviceVerificationState.verified =>
        l10n.chatSecurityCurrentDeviceDescriptionVerified,
      ChatDeviceVerificationState.unverified =>
        l10n.chatSecurityCurrentDeviceDescriptionUnverified,
      ChatDeviceVerificationState.blocked =>
        l10n.chatSecurityCurrentDeviceDescriptionBlocked,
      ChatDeviceVerificationState.unavailable =>
        l10n.chatSecurityCurrentDeviceDescriptionUnavailable,
    };
  }

  static String _backupLabel(
    AppLocalizations l10n,
    ChatKeyBackupState state,
  ) {
    return switch (state) {
      ChatKeyBackupState.unavailable => l10n.chatSecurityStatusUnavailable,
      ChatKeyBackupState.missing => l10n.chatSecurityStatusMissing,
      ChatKeyBackupState.recoveryRequired =>
        l10n.chatSecurityStatusNeedsReconnect,
      ChatKeyBackupState.ready => l10n.chatSecurityStatusReady,
    };
  }

  static String _roomReadinessLabel(
    AppLocalizations l10n,
    ChatRoomEncryptionReadiness state,
  ) {
    return switch (state) {
      ChatRoomEncryptionReadiness.unavailable =>
        l10n.chatSecurityStatusUnavailable,
      ChatRoomEncryptionReadiness.noEncryptedRooms =>
        l10n.chatSecurityEncryptedRoomsStatusNone,
      ChatRoomEncryptionReadiness.encryptedRoomsNeedAttention =>
        l10n.chatSecurityEncryptedRoomsStatusAttention,
      ChatRoomEncryptionReadiness.ready => l10n.chatSecurityStatusReady,
    };
  }

  static String _failureMessage(
    AppLocalizations l10n,
    ChatFailure failure,
  ) {
    if (failure.message.trim().isNotEmpty) {
      return failure.message;
    }

    return l10n.chatSecurityGenericFailure;
  }
}

class _ActionArea extends ConsumerWidget {
  const _ActionArea({required this.state, required this.l10n});

  final ChatSecurityUiState state;
  final AppLocalizations l10n;

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
        l10n.chatSecurityActionsUnavailableSignedOut,
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
          semanticLabel: l10n.chatSecuritySetupButton,
          child: Text(
            state.isBusy
                ? l10n.chatSecurityWorkingButton
                : l10n.chatSecuritySetupButton,
          ),
        ),
      );
    }

    if (security.bootstrapState ==
            ChatSecurityBootstrapState.recoveryRequired ||
        security.keyBackupState == ChatKeyBackupState.recoveryRequired) {
      buttons.add(
        AccessibleButton(
          outlined: true,
          onPressed: state.isBusy
              ? null
              : () => _showRestoreDialog(context, notifier),
          semanticLabel: l10n.chatSecurityReconnectButton,
          child: Text(l10n.chatSecurityReconnectButton),
        ),
      );
    }

    if (security.deviceVerificationState !=
            ChatDeviceVerificationState.verified &&
        security.bootstrapState == ChatSecurityBootstrapState.ready) {
      buttons.add(
        AccessibleButton(
          outlined: true,
          onPressed: state.isBusy ? null : () => notifier.startVerification(),
          semanticLabel: l10n.chatSecurityVerifyDeviceButton,
          child: Text(l10n.chatSecurityVerifyDeviceButton),
        ),
      );
    }

    final verification = security.verificationSession;
    if (verification.phase == ChatVerificationPhase.incomingRequest) {
      buttons.addAll([
        AccessibleButton(
          onPressed: state.isBusy
              ? null
              : () => notifier.acceptVerification(),
          semanticLabel: l10n.chatSecurityAcceptVerificationButton,
          child: Text(l10n.chatSecurityAcceptVerificationButton),
        ),
        AccessibleButton(
          outlined: true,
          onPressed: state.isBusy
              ? null
              : () => notifier.cancelVerification(),
          semanticLabel: l10n.chatSecurityDeclineVerificationButton,
          child: Text(l10n.chatSecurityDeclineVerificationButton),
        ),
      ]);
    }

    if (verification.phase == ChatVerificationPhase.chooseMethod) {
      buttons.add(
        AccessibleButton(
          onPressed: state.isBusy
              ? null
              : () => notifier.startSasVerification(),
          semanticLabel: l10n.chatSecurityCompareEmojiButton,
          child: Text(l10n.chatSecurityCompareEmojiButton),
        ),
      );
    }

    if (verification.phase == ChatVerificationPhase.compareSas) {
      buttons.addAll([
        AccessibleButton(
          onPressed: state.isBusy
              ? null
              : () => notifier.confirmSas(matches: true),
          semanticLabel: l10n.chatSecurityEmojiMatchButton,
          child: Text(l10n.chatSecurityEmojiMatchButton),
        ),
        AccessibleButton(
          outlined: true,
          onPressed: state.isBusy
              ? null
              : () => notifier.confirmSas(matches: false),
          semanticLabel: l10n.chatSecurityEmojiMismatchButton,
          child: Text(l10n.chatSecurityEmojiMismatchButton),
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
          semanticLabel: l10n.chatSecurityDismissButton,
          child: Text(l10n.chatSecurityDismissButton),
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
        if (state.lastActionNotice != null) ...[
          Text(_actionNoticeMessage(l10n, state.lastActionNotice!)),
          const SizedBox(height: 12),
        ],
        if (_verificationMessage(l10n, verification) case final message?) ...[
          Text(message),
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
        if (buttons.isEmpty &&
            verification.phase == ChatVerificationPhase.none)
          Text(
            l10n.chatSecurityNoActionNeeded,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
      ],
    );
  }

  String _actionNoticeMessage(
    AppLocalizations l10n,
    ChatSecurityActionNotice notice,
  ) {
    return switch (notice) {
      ChatSecurityActionNotice.setupComplete =>
        l10n.chatSecurityNoticeSetupComplete,
      ChatSecurityActionNotice.recoveryRestored =>
        l10n.chatSecurityNoticeRecoveryRestored,
      ChatSecurityActionNotice.verificationRequestSent =>
        l10n.chatSecurityNoticeVerificationRequestSent,
      ChatSecurityActionNotice.verificationCancelled =>
        l10n.chatSecurityNoticeVerificationCancelled,
    };
  }

  String? _verificationMessage(
    AppLocalizations l10n,
    ChatVerificationSession verification,
  ) {
    return switch (verification.phase) {
      ChatVerificationPhase.none => null,
      ChatVerificationPhase.incomingRequest =>
        l10n.chatSecurityVerificationIncomingMessage,
      ChatVerificationPhase.chooseMethod =>
        l10n.chatSecurityVerificationChooseMethodMessage,
      ChatVerificationPhase.waitingForOtherDevice =>
        l10n.chatSecurityVerificationWaitingMessage,
      ChatVerificationPhase.compareSas =>
        l10n.chatSecurityVerificationCompareMessage,
      ChatVerificationPhase.done =>
        l10n.chatSecurityVerificationDoneMessage,
      ChatVerificationPhase.cancelled =>
        l10n.chatSecurityVerificationCancelledMessage,
      ChatVerificationPhase.failed =>
        l10n.chatSecurityVerificationFailedMessage,
    };
  }

  Future<void> _showRecoveryKeySetupDialog(
    BuildContext context,
    ChatSecurityController notifier,
  ) async {
    final controller = TextEditingController();
    final passphrase = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.chatSecuritySetupDialogTitle),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.chatSecuritySetupDialogDescription,
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              obscureText: true,
              decoration: InputDecoration(
                labelText: l10n.chatSecurityOptionalPassphraseLabel,
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(l10n.chatSecurityDialogCancelButton),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(controller.text.trim()),
            child: Text(l10n.chatSecurityDialogContinueButton),
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
        title: Text(l10n.chatSecurityRestoreDialogTitle),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.chatSecurityRestoreDialogDescription,
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              decoration: InputDecoration(
                labelText: l10n.chatSecurityRecoveryKeyFieldLabel,
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(l10n.chatSecurityDialogCancelButton),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(controller.text.trim()),
            child: Text(l10n.chatSecurityReconnectButton),
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
              child: Text(l10n.chatSecurityRecoveryKeyDismissButton),
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
    final l10n = AppLocalizations.of(context);
    return Semantics(
      container: true,
      label: [
        l10n.chatSecurityEmojiSummaryLabel,
        ...verification.sasEmojis.map(
          (emoji) => '${emoji.label} ${emoji.symbol}',
        ),
        if (verification.sasNumbers.isNotEmpty)
          l10n.chatSecurityNumbersSummaryLabel(
            verification.sasNumbers.join(', '),
          ),
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
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 12,
        ),
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
