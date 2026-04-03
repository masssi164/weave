import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';

class ChatSecurityUiState {
  const ChatSecurityUiState({
    required this.isLoading,
    required this.isBusy,
    this.security,
    this.failure,
    this.generatedRecoveryKey,
    this.lastActionMessage,
  });

  const ChatSecurityUiState.loading()
    : this(isLoading: true, isBusy: false);

  const ChatSecurityUiState.ready(ChatSecurityState security)
    : this(isLoading: false, isBusy: false, security: security);

  final bool isLoading;
  final bool isBusy;
  final ChatSecurityState? security;
  final ChatFailure? failure;
  final String? generatedRecoveryKey;
  final String? lastActionMessage;

  ChatSecurityUiState copyWith({
    bool? isLoading,
    bool? isBusy,
    ChatSecurityState? security,
    ChatFailure? failure,
    String? generatedRecoveryKey,
    String? lastActionMessage,
    bool clearFailure = false,
    bool clearGeneratedRecoveryKey = false,
    bool clearLastActionMessage = false,
  }) {
    return ChatSecurityUiState(
      isLoading: isLoading ?? this.isLoading,
      isBusy: isBusy ?? this.isBusy,
      security: security ?? this.security,
      failure: clearFailure ? null : (failure ?? this.failure),
      generatedRecoveryKey: clearGeneratedRecoveryKey
          ? null
          : (generatedRecoveryKey ?? this.generatedRecoveryKey),
      lastActionMessage: clearLastActionMessage
          ? null
          : (lastActionMessage ?? this.lastActionMessage),
    );
  }
}

class ChatSecurityController extends Notifier<ChatSecurityUiState> {
  Timer? _pollingTimer;

  @override
  ChatSecurityUiState build() {
    ref.onDispose(_stopPolling);
    Future<void>.microtask(() => refresh());
    return const ChatSecurityUiState.loading();
  }

  Future<void> refresh({bool forceSync = true}) async {
    final previousSecurity = state.security;
    state = state.copyWith(
      isLoading: previousSecurity == null,
      clearFailure: true,
      clearLastActionMessage: false,
    );

    try {
      final security = await ref
          .read(chatSecurityRepositoryProvider)
          .loadSecurityState(refresh: forceSync);
      state = state.copyWith(
        isLoading: false,
        isBusy: false,
        security: security,
        clearFailure: true,
      );
      _updatePolling(security);
    } on ChatFailure catch (failure) {
      state = state.copyWith(
        isLoading: false,
        isBusy: false,
        failure: failure,
      );
      _stopPolling();
    } catch (error) {
      state = state.copyWith(
        isLoading: false,
        isBusy: false,
        failure: ChatFailure.unknown(
          'Unable to load Matrix security right now.',
          cause: error,
        ),
      );
      _stopPolling();
    }
  }

  Future<void> bootstrap({String? passphrase}) async {
    await _runAction(
      () async {
        final recoveryKey = await ref
            .read(chatSecurityRepositoryProvider)
            .bootstrapSecurity(passphrase: passphrase);
        state = state.copyWith(
          generatedRecoveryKey: recoveryKey,
          lastActionMessage:
              'Encrypted chat is now set up. Save your recovery key before closing this screen.',
        );
      },
    );
  }

  Future<void> restore({required String recoveryKeyOrPassphrase}) async {
    await _runAction(
      () => ref
          .read(chatSecurityRepositoryProvider)
          .restoreSecurity(
            recoveryKeyOrPassphrase: recoveryKeyOrPassphrase,
          ),
      successMessage:
          'Encrypted chat was reconnected for this device.',
      clearRecoveryKey: true,
    );
  }

  Future<void> startVerification() async {
    await _runAction(
      () => ref.read(chatSecurityRepositoryProvider).startVerification(),
      successMessage:
          'Verification request sent. Continue on your other Matrix device.',
    );
  }

  Future<void> acceptVerification() async {
    await _runAction(
      () => ref.read(chatSecurityRepositoryProvider).acceptVerification(),
    );
  }

  Future<void> startSasVerification() async {
    await _runAction(
      () => ref.read(chatSecurityRepositoryProvider).startSasVerification(),
    );
  }

  Future<void> confirmSas({required bool matches}) async {
    await _runAction(
      () => ref
          .read(chatSecurityRepositoryProvider)
          .confirmSas(matches: matches),
    );
  }

  Future<void> cancelVerification() async {
    await _runAction(
      () => ref.read(chatSecurityRepositoryProvider).cancelVerification(),
      successMessage: 'Verification cancelled.',
    );
  }

  Future<void> dismissVerificationResult() async {
    await _runAction(
      () => ref.read(chatSecurityRepositoryProvider).dismissVerificationResult(),
      clearRecoveryKey: false,
      silent: true,
    );
  }

  void clearRecoveryKeyNotice() {
    state = state.copyWith(
      clearGeneratedRecoveryKey: true,
      clearLastActionMessage: true,
    );
  }

  Future<void> _runAction(
    Future<void> Function() action, {
    String? successMessage,
    bool clearRecoveryKey = false,
    bool silent = false,
  }) async {
    state = state.copyWith(isBusy: true, clearFailure: true);

    try {
      await action();
      await refresh(forceSync: true);
      state = state.copyWith(
        isBusy: false,
        clearFailure: true,
        clearGeneratedRecoveryKey: clearRecoveryKey,
        lastActionMessage: silent ? state.lastActionMessage : successMessage,
      );
    } on ChatFailure catch (failure) {
      state = state.copyWith(isBusy: false, failure: failure);
    } catch (error) {
      state = state.copyWith(
        isBusy: false,
        failure: ChatFailure.unknown(
          'Unable to update Matrix security right now.',
          cause: error,
        ),
      );
    }
  }

  void _updatePolling(ChatSecurityState security) {
    if (!security.verificationSession.isOngoing) {
      _stopPolling();
      return;
    }

    _pollingTimer ??= Timer.periodic(const Duration(seconds: 3), (_) {
      unawaited(refresh());
    });
  }

  void _stopPolling() {
    _pollingTimer?.cancel();
    _pollingTimer = null;
  }
}

final chatSecurityProvider =
    NotifierProvider<ChatSecurityController, ChatSecurityUiState>(
      ChatSecurityController.new,
    );
