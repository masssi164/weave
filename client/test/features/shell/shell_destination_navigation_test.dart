import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/features/shell/domain/entities/shell_destination.dart';
import 'package:weave/features/shell/presentation/app_shell.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

void main() {
  for (final scale in [1.0, 2.0]) {
    testWidgets(
      'reordered destinations retain routes and semantics at $scale',
      (tester) async {
        const destinations = [
          ShellDestination.settings,
          ShellDestination.files,
          ShellDestination.home,
        ];
        final router = GoRouter(
          initialLocation: '/home',
          routes: [
            StatefulShellRoute.indexedStack(
              builder: (context, state, shell) =>
                  AppShell(navigationShell: shell, destinations: destinations),
              branches: [
                for (final destination in destinations)
                  StatefulShellBranch(
                    routes: [
                      GoRoute(
                        path: '/${destination.id}',
                        builder: (context, state) =>
                            Text('body-${destination.id}'),
                      ),
                    ],
                  ),
              ],
            ),
          ],
        );
        addTearDown(router.dispose);
        await tester.pumpWidget(
          ProviderScope(
            child: MaterialApp.router(
              routerConfig: router,
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              locale: const Locale('en'),
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: TextScaler.linear(scale)),
                child: child!,
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        Finder destination(String id) => find.byWidgetPredicate(
          (widget) =>
              widget is Semantics && widget.properties.identifier == 'nav-$id',
        );
        expect(destination('chat'), findsNothing);
        expect(destination('calendar'), findsNothing);
        expect(find.text('body-home'), findsOneWidget);
        await tester.tap(destination('files'));
        await tester.pumpAndSettle();
        expect(router.routeInformationProvider.value.uri.path, '/files');
        expect(find.text('body-files'), findsOneWidget);
        await tester.tap(destination('settings'));
        await tester.pumpAndSettle();
        expect(router.routeInformationProvider.value.uri.path, '/settings');
        expect(find.text('body-settings'), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  }
}
