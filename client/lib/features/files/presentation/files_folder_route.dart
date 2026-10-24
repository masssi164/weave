import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/files/presentation/files_screen.dart';
import 'package:weave/features/files/presentation/providers/files_provider.dart';

/// Each native folder page retains its own listing and scroll/focus context.
/// Authorization and repository invalidation still come from the parent scope.
class FilesFolderRoute extends StatelessWidget {
  const FilesFolderRoute({
    super.key,
    required this.folderId,
    required this.path,
    this.spaceRef,
    this.ownerKey,
  });

  final String folderId;
  final String path;
  final String? spaceRef;
  final String? ownerKey;

  @override
  Widget build(BuildContext context) => ProviderScope(
    key: ValueKey((folderId, path, spaceRef, ownerKey)),
    overrides: [
      filesProvider.overrideWith(
        () => FilesController(
          initialPath: path,
          initialRootSpaceRef: spaceRef,
          expectedFolderId: folderId,
          expectedOwnerKey: ownerKey,
        ),
      ),
    ],
    child: Scaffold(body: FilesScreen(initialPath: path, isFolderRoute: true)),
  );
}
