import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/presentation/providers/files_provider.dart';

import '../../integration_test/dual_ios_collaboration_test.dart'
    show
        nativeDualFilesUploadDiagnostic,
        nativeDualFilesUploadProbe,
        safelyReportDualOnboardingFailure;

class _FilesController extends FilesController {
  int loads = 0;

  @override
  Future<FilesViewState> build() async {
    loads++;
    return const FilesViewState(
      connectionState: FilesConnectionState.disconnected(),
      uploadStatus: FilesUploadStatus(
        phase: FilesUploadPhase.failed,
        fileName: 'private-name',
        failure: FilesFailure.permissionDenied('private upload payload'),
      ),
    );
  }
}

void main() {
  test('cold upload failure observation never initializes Files', () async {
    final controller = _FilesController();
    final container = ProviderContainer(
      overrides: [filesProvider.overrideWith(() => controller)],
    );
    addTearDown(container.dispose);
    final records = <String>[];
    final failure = TestFailure('original upload timeout');
    final stack = StackTrace.fromString('original upload stack');
    try {
      await nativeDualFilesUploadProbe(
        container,
        () => Future.error(failure, stack),
        diagnostic: records.add,
      );
      fail('Original failure was swallowed.');
    } catch (error, observedStack) {
      expect(error, same(failure));
      expect(observedStack.toString(), stack.toString());
    }
    await Future<void>.delayed(Duration.zero);
    expect(container.exists(filesProvider), isFalse);
    expect(controller.loads, 0);
    expect(records.single, contains('initialized=false'));
    expect(records.single, contains('uploadPhase=unavailable'));
  });

  test('initialized failed upload emits enums without payloads', () async {
    final container = ProviderContainer(
      overrides: [filesProvider.overrideWith(_FilesController.new)],
    );
    addTearDown(container.dispose);
    await container.read(filesProvider.future);
    final records = <String>[];
    final failure = TestFailure('original');
    await expectLater(
      nativeDualFilesUploadProbe(
        container,
        () => Future.error(failure),
        diagnostic: records.add,
      ),
      throwsA(same(failure)),
    );
    expect(records.single, contains('uploadPhase=failed'));
    expect(records.single, contains('uploadFailure=permissionDenied'));
    expect(records.single, isNot(contains('private')));
  });

  test('loading error and data snapshots use only fixed states', () {
    final snapshots = <AsyncValue<FilesViewState>>[
      const AsyncLoading(),
      AsyncError(StateError('private token and path'), StackTrace.current),
      const AsyncData(
        FilesViewState(
          connectionState: FilesConnectionState.unavailable(
            message: 'private URL',
          ),
          directoryFailure: FilesFailure.protocol('private directory'),
          uploadStatus: FilesUploadStatus(phase: FilesUploadPhase.picking),
        ),
      ),
    ];
    final records = snapshots.map(nativeDualFilesUploadDiagnostic).toList();
    expect(records[0], contains('loading=true error=false data=false'));
    expect(records[1], contains('loading=false error=true data=false'));
    expect(records[2], contains('uploadPhase=picking'));
    expect(records[2], contains('directoryFailure=protocol'));
    expect(records.join(), isNot(contains('private')));
    expect(records.join(), isNot(contains('StateError')));
  });

  test(
    'disposed snapshot and throwing logger preserve failure and stack',
    () async {
      for (final dispose in [false, true]) {
        final container = ProviderContainer();
        if (dispose) container.dispose();
        final failure = TestFailure('original');
        final stack = StackTrace.fromString('original stack');
        var attempts = 0;
        try {
          await nativeDualFilesUploadProbe(
            container,
            () => Future.error(failure, stack),
            diagnostic: (_) {
              attempts++;
              throw StateError('private logger');
            },
          );
          fail('Original failure was swallowed.');
        } catch (error, observedStack) {
          expect(error, same(failure));
          expect(observedStack.toString(), stack.toString());
        }
        expect(attempts, 2);
        if (!dispose) container.dispose();
      }
    },
  );

  test(
    'existing safe reporter contains an actual disposed-provider read',
    () async {
      final container = ProviderContainer();
      container.dispose();
      var unavailable = false;
      await safelyReportDualOnboardingFailure(
        report: () async {
          container.read(filesProvider);
        },
        unavailable: () => unavailable = true,
      );
      expect(unavailable, isTrue);
    },
  );

  test(
    'successful upload wait is silent and does not read providers',
    () async {
      final container = ProviderContainer();
      addTearDown(container.dispose);
      final records = <String>[];
      await nativeDualFilesUploadProbe(
        container,
        () async {},
        diagnostic: records.add,
      );
      expect(records, isEmpty);
      expect(container.exists(filesProvider), isFalse);
    },
  );
}
