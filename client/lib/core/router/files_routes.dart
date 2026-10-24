import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/files/presentation/files_folder_route.dart';
import 'package:weave/features/files/presentation/files_screen.dart';

/// The Files branch uses ordinary platform pages for folder history and Back.
GoRoute filesRoute() => GoRoute(
  path: AppRoutes.files,
  builder: (context, state) => FilesScreen(
    initialPath: state.uri.path == AppRoutes.files
        ? state.uri.queryParameters['path'] ?? '/'
        : '/',
    initialRootSpaceRef: state.uri.queryParameters['space'],
  ),
  routes: [
    GoRoute(
      path: AppRoutes.filesFolderRelative,
      builder: (context, state) => FilesFolderRoute(
        folderId: state.pathParameters['folderId']!,
        path: state.uri.queryParameters['folderPath'] ?? '/',
        spaceRef: state.uri.queryParameters['space'],
        ownerKey: state.extra is String ? state.extra as String : null,
      ),
    ),
  ],
);
