import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:shared_preferences/shared_preferences.dart';

part 'setup_state_provider.g.dart';

/// Key used to persist setup-complete state in [SharedPreferences].
const _kSetupComplete = 'setup_complete';

/// Tracks whether the user has completed the onboarding setup flow.
///
/// On first read, the value is loaded from [SharedPreferences].
/// Call [completeSetup] to write `true` and mark setup as done.
@Riverpod(keepAlive: true)
class SetupState extends _$SetupState {
  @override
  bool build() {
    // Initialise synchronously as `false` — the actual persisted value
    // is loaded on the first navigation redirect evaluation.
    // We cannot await here because GoRouter's redirect is synchronous.
    _loadFromPrefs();
    return false;
  }

  Future<void> _loadFromPrefs() async {
    final prefs = await SharedPreferences.getInstance();
    final done = prefs.getBool(_kSetupComplete) ?? false;
    if (done) {
      state = true;
    }
  }

  /// Marks setup as complete, persists the flag, and updates state.
  Future<void> completeSetup() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_kSetupComplete, true);
    state = true;
  }
}

/// Read-only provider for the current setup step index.
///
/// The [SetupFlow] widget manages this locally, but the provider allows
/// other widgets (e.g. a step indicator) to react to step changes.
@riverpod
class SetupStep extends _$SetupStep {
  @override
  int build() => 0;

  /// Advance to the next step.
  void next() => state = state + 1;

  /// Go back to the previous step.
  void previous() {
    if (state > 0) state = state - 1;
  }
}
