import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/files/presentation/files_folder_route.dart';
import 'package:weave/features/files/presentation/files_locations_screen.dart';

/// Files overview -> selected location -> folder, using native page history.
GoRoute filesRoute() => GoRoute(
  path: AppRoutes.files,
  builder: (context, state) => const FilesLocationsScreen(),
  routes: [
    _locationRoute(AppRoutes.filesPrivateRelative),
    _locationRoute(AppRoutes.filesSpaceRelative),
  ],
);

GoRoute _locationRoute(String path) => GoRoute(
  path: path,
  builder: (context, state) => FilesFolderRoute(
    // A nested folder's folderPath must not change its parent location page.
    path: state.uri.queryParameters['path'] ?? '/',
    spaceRef: state.pathParameters['spaceRef'],
    ownerKey: state.extra is String ? state.extra as String : null,
  ),
  routes: [
    GoRoute(
      path: AppRoutes.filesFolderRelative,
      builder: (context, state) => FilesFolderRoute(
        folderId: state.pathParameters['folderId']!,
        path: state.uri.queryParameters['folderPath'] ?? '/',
        spaceRef: state.pathParameters['spaceRef'],
        ownerKey: state.extra is String ? state.extra as String : null,
      ),
    ),
  ],
);
