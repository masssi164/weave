import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/files/presentation/files_folder_route.dart';
import 'package:weave/features/files/presentation/files_locations_screen.dart';

/// Files overview -> selected location -> folder, using native page history.
GoRoute filesRoute() => GoRoute(
  path: AppRoutes.files,
  pageBuilder: (context, state) => MaterialPage<void>(
    key: state.pageKey,
    name: AppRoutes.files,
    child: const FilesLocationsScreen(),
  ),
  routes: [
    _locationRoute(AppRoutes.filesPrivateRelative),
    _locationRoute(AppRoutes.filesSpaceRelative),
  ],
);

GoRoute _locationRoute(String path) => GoRoute(
  path: path,
  pageBuilder: (context, state) => _directoryPage(state),
  routes: [
    GoRoute(
      path: AppRoutes.filesFolderRelative,
      pageBuilder: (context, state) =>
          _directoryPage(state, folderId: state.pathParameters['folderId']!),
    ),
  ],
);

MaterialPage<void> _directoryPage(GoRouterState state, {String? folderId}) {
  // Child query/extra data must not rebuild or retarget the retained root page.
  final path = state.uri.queryParameters[folderId == null ? 'path' : 'folderPath'] ?? '/';
  final spaceRef = state.pathParameters['spaceRef'];
  return MaterialPage<void>(
    key: state.pageKey,
    name: folderId == null ? 'files-location' : 'files-folder',
    arguments: (path: path, spaceRef: spaceRef),
    child: FilesFolderRoute(
      folderId: folderId,
      path: path,
      spaceRef: spaceRef,
      ownerKey: folderId != null && state.extra is String ? state.extra as String : null,
    ),
  );
}
