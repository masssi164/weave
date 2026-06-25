import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:go_router/go_router.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/onboarding/domain/use_cases/consume_member_handoff.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

final consumeMemberHandoffProvider = Provider<ConsumeMemberHandoff>((ref) {
  final httpClient = http.Client();
  ref.onDispose(httpClient.close);
  return ConsumeMemberHandoff(
    repository: ref.watch(serverConfigurationRepositoryProvider),
    discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
    evidenceStore: ref.watch(preferencesStoreProvider),
  );
});

class MemberHandoffScreen extends ConsumerStatefulWidget {
  const MemberHandoffScreen({super.key, required this.uri});

  final Uri uri;

  @override
  ConsumerState<MemberHandoffScreen> createState() =>
      _MemberHandoffScreenState();
}

class _MemberHandoffScreenState extends ConsumerState<MemberHandoffScreen> {
  Object? _failure;

  @override
  void initState() {
    super.initState();
    Future.microtask(_consume);
  }

  Future<void> _consume() async {
    try {
      await ref.read(consumeMemberHandoffProvider).call(widget.uri);
      ref.invalidate(appBootstrapProvider);
      if (mounted) {
        context.go(AppRoutes.signIn);
      }
    } catch (error) {
      if (mounted) {
        setState(() => _failure = error);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final failure = _failure;
    if (failure != null) {
      return Scaffold(
        body: SafeArea(
          child: Center(
            child: ErrorState(
              message: l10n.memberHandoffErrorTitle,
              guidance: l10n.memberHandoffErrorGuidance,
              retryLabel: l10n.retryButton,
              onRetry: () {
                setState(() => _failure = null);
                _consume();
              },
            ),
          ),
        ),
      );
    }
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: LoadingState(
            message: l10n.memberHandoffLoadingTitle,
            hint: l10n.memberHandoffLoadingHint,
          ),
        ),
      ),
    );
  }
}
