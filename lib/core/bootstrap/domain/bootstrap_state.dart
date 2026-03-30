import 'package:weave/core/failures/app_failure.dart';

enum BootstrapPhase { loading, needsSetup, ready, error }

class BootstrapState {
  const BootstrapState._({required this.phase, this.failure});

  const BootstrapState.loading() : this._(phase: BootstrapPhase.loading);

  const BootstrapState.needsSetup() : this._(phase: BootstrapPhase.needsSetup);

  const BootstrapState.ready() : this._(phase: BootstrapPhase.ready);

  const BootstrapState.error(AppFailure failure)
    : this._(phase: BootstrapPhase.error, failure: failure);

  final BootstrapPhase phase;
  final AppFailure? failure;

  bool get isResolved =>
      phase == BootstrapPhase.needsSetup || phase == BootstrapPhase.ready;
}
