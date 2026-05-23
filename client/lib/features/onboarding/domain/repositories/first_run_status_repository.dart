import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';

abstract interface class FirstRunStatusRepository {
  Future<FirstRunStatus?> loadStatus();
}
