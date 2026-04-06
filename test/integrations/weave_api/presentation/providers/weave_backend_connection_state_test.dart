import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

void main() {
  group('weaveBackendConnectionStateProvider', () {
    test('returns unconfigured when snapshot is null', () {
      final container = ProviderContainer.test(
        overrides: [
          weaveApiWorkspaceCapabilitySnapshotProvider.overrideWithValue(
            const AsyncData<WorkspaceCapabilitySnapshot?>(null),
          ),
        ],
      );
      addTearDown(container.dispose);

      expect(
        container.read(weaveBackendConnectionStateProvider),
        WeaveBackendConnectionState.unconfigured,
      );
    });

    test('returns unreachable when backend cannot be reached', () {
      final container = ProviderContainer.test(
        overrides: [
          weaveApiWorkspaceCapabilitySnapshotProvider.overrideWithValue(
            AsyncError<WorkspaceCapabilitySnapshot?>(
              AppFailure.unknown('Unable to reach the Weave backend right now.'),
              StackTrace.empty,
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      expect(
        container.read(weaveBackendConnectionStateProvider),
        WeaveBackendConnectionState.unreachable,
      );
    });

    test('returns unauthorized when backend rejects session token', () {
      final container = ProviderContainer.test(
        overrides: [
          weaveApiWorkspaceCapabilitySnapshotProvider.overrideWithValue(
            AsyncError<WorkspaceCapabilitySnapshot?>(
              AppFailure.unknown(
                'The Weave backend rejected the current session.',
              ),
              StackTrace.empty,
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      expect(
        container.read(weaveBackendConnectionStateProvider),
        WeaveBackendConnectionState.unauthorized,
      );
    });

    test('returns serverError for other AppFailure messages', () {
      final container = ProviderContainer.test(
        overrides: [
          weaveApiWorkspaceCapabilitySnapshotProvider.overrideWithValue(
            AsyncError<WorkspaceCapabilitySnapshot?>(
              AppFailure.unknown('Unexpected server problem.'),
              StackTrace.empty,
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      expect(
        container.read(weaveBackendConnectionStateProvider),
        WeaveBackendConnectionState.serverError,
      );
    });
  });
}
