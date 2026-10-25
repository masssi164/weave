import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/files/presentation/files_screen.dart';
import 'package:weave/features/files/presentation/providers/files_provider.dart';

/// Each location/folder page owns its listing, scroll and focus context.
/// Authorization and repository invalidation still come from the parent scope.
class FilesFolderRoute extends StatelessWidget {
  const FilesFolderRoute({
    super.key,
    this.folderId,
    required this.path,
    this.spaceRef,
    this.ownerKey,
  });

  /// Null only for the selected private or shared location's root directory.
  final String? folderId;
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
