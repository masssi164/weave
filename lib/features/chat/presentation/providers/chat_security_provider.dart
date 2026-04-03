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
    this.lastActionNotice,
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
  final ChatSecurityActionNotice? lastActionNotice;

  ChatSecurityUiState copyWith({
    bool? isLoading,
    bool? isBusy,
    ChatSecurityState? security,
    ChatFailure? failure,
    String? generatedRecoveryKey,
    ChatSecurityActionNotice? lastActionNotice,
    bool clearFailure = false,
    bool clearGeneratedRecoveryKey = false,
    bool clearLastActionNotice = false,
  }) {
    return ChatSecurityUiState(
      isLoading: isLoading ?? this.isLoading,
      isBusy: isBusy ?? this.isBusy,
      security: security ?? this.security,
      failure: clearFailure ? null : (failure ?? this.failure),
      generatedRecoveryKey: clearGeneratedRecoveryKey
          ? null
          : (generatedRecoveryKey ?? this.generatedRecoveryKey),
      lastActionNotice: clearLastActionNotice
          ? null
          : (lastActionNotice ?? this.lastActionNotice),
    );
  }
}

class ChatSecurityController extends Notifier<ChatSecurityUiState> {
  @override
  ChatSecurityUiState build() {
    ref.listen<AsyncValue<ChatVerificationSession>>(
      chatVerificationUpdatesProvider,
      (previous, next) {
        if (next.hasValue) {
          Future<void>.microtask(() => refresh(forceSync: false));
        }
      },
    );
    Future<void>.microtask(() => refresh());
    return const ChatSecurityUiState.loading();
  }

  Future<void> refresh({bool forceSync = true}) async {
    final previousSecurity = state.security;
    state = state.copyWith(
      isLoading: previousSecurity == null,
      clearFailure: true,
      clearLastActionNotice: false,
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
    } on ChatFailure catch (failure) {
      state = state.copyWith(
        isLoading: false,
        isBusy: false,
        failure: failure,
      );
    } catch (error) {
      state = state.copyWith(
        isLoading: false,
        isBusy: false,
        failure: ChatFailure.unknown('', cause: error),
      );
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
          lastActionNotice: ChatSecurityActionNotice.setupComplete,
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
      successNotice: ChatSecurityActionNotice.recoveryRestored,
      clearRecoveryKey: true,
    );
  }

  Future<void> startVerification() async {
    await _runAction(
      () => ref.read(chatSecurityRepositoryProvider).startVerification(),
      successNotice: ChatSecurityActionNotice.verificationRequestSent,
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
      successNotice: ChatSecurityActionNotice.verificationCancelled,
    );
  }

  Future<void> dismissVerificationResult() async {
    await _runAction(
      () =>
          ref.read(chatSecurityRepositoryProvider).dismissVerificationResult(),
      clearRecoveryKey: false,
      silent: true,
    );
  }

  void clearRecoveryKeyNotice() {
    state = state.copyWith(
      clearGeneratedRecoveryKey: true,
      clearLastActionNotice: true,
    );
  }

  Future<void> _runAction(
    Future<void> Function() action, {
    ChatSecurityActionNotice? successNotice,
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
        lastActionNotice: silent ? state.lastActionNotice : successNotice,
      );
    } on ChatFailure catch (failure) {
      state = state.copyWith(isBusy: false, failure: failure);
    } catch (error) {
      state = state.copyWith(
        isBusy: false,
        failure: ChatFailure.unknown('', cause: error),
      );
    }
  }

}

final chatVerificationUpdatesProvider =
    StreamProvider<ChatVerificationSession>((ref) {
      return ref
          .watch(chatSecurityRepositoryProvider)
          .watchVerificationUpdates();
    });

final chatSecurityProvider =
    NotifierProvider<ChatSecurityController, ChatSecurityUiState>(
      ChatSecurityController.new,
    );
