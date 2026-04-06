import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

const _connectedSnapshot = WorkspaceCapabilitySnapshot(
  shellAccess: WorkspaceCapabilityState(
    capability: WorkspaceCapability.shellAccess,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  chat: WorkspaceCapabilityState(
    capability: WorkspaceCapability.chat,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  files: WorkspaceCapabilityState(
    capability: WorkspaceCapability.files,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  calendar: WorkspaceCapabilityState(
    capability: WorkspaceCapability.calendar,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  boards: WorkspaceCapabilityState(
    capability: WorkspaceCapability.boards,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
);

void main() {
  group('weaveBackendConnectionStateProvider', () {
    test('returns loading when snapshot is loading', () {
      final container = ProviderContainer.test(
        overrides: [
          weaveApiWorkspaceCapabilitySnapshotProvider.overrideWithValue(
            const AsyncLoading<WorkspaceCapabilitySnapshot?>(),
          ),
        ],
      );
      addTearDown(container.dispose);

      expect(
        container.read(weaveBackendConnectionStateProvider),
        WeaveBackendConnectionState.loading,
      );
    });

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

    test('returns connected when snapshot is non-null', () {
      final container = ProviderContainer.test(
        overrides: [
          weaveApiWorkspaceCapabilitySnapshotProvider.overrideWithValue(
            const AsyncData<WorkspaceCapabilitySnapshot?>(_connectedSnapshot),
          ),
        ],
      );
      addTearDown(container.dispose);

      expect(
        container.read(weaveBackendConnectionStateProvider),
        WeaveBackendConnectionState.connected,
      );
    });

    test('returns unreachable when backend cannot be reached', () {
      final container = ProviderContainer.test(
        overrides: [
          weaveApiWorkspaceCapabilitySnapshotProvider.overrideWithValue(
            const AsyncError<WorkspaceCapabilitySnapshot?>(
              AppFailure.unknown(
                'Unable to reach the Weave backend right now.',
              ),
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
            const AsyncError<WorkspaceCapabilitySnapshot?>(
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
            const AsyncError<WorkspaceCapabilitySnapshot?>(
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
