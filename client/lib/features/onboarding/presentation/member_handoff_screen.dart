import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/onboarding/domain/use_cases/consume_member_handoff.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

final consumeMemberHandoffProvider = Provider<ConsumeMemberHandoff>((ref) {
  return ConsumeMemberHandoff(
    repository: ref.watch(serverConfigurationRepositoryProvider),
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
    final failure = _failure;
    if (failure != null) {
      return Scaffold(
        body: SafeArea(
          child: Center(
            child: ErrorState(
              message: 'We could not open this Weave invite',
              guidance: '$failure',
              retryLabel: 'Try again',
              onRetry: () {
                setState(() => _failure = null);
                _consume();
              },
            ),
          ),
        ),
      );
    }
    return const Scaffold(
      body: SafeArea(
        child: Center(
          child: LoadingState(
            message: 'Opening Weave invite',
            hint: 'We are preparing sign-in for this workspace.',
          ),
        ),
      ),
    );
  }
}
