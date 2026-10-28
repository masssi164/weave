import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/features/shell/presentation/app_shell.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../integration_test/dual_ios_collaboration_test.dart'
    show navigateNativeDualDestination;

void main() {
  final binding = LiveTestWidgetsFlutterBinding();
  binding.framePolicy = LiveTestWidgetsFlutterBindingFramePolicy.onlyPumps;
  binding.shouldPropagateDevicePointerEvents = true;

  for (final locale in ['en', 'de']) {
    for (final scale in [1.0, 2.0]) {
      testWidgets('live shell navigation after workspace entry: $locale $scale', (
        tester,
      ) async {
        tester.view.physicalSize = const Size(1206, 2622);
        tester.view.devicePixelRatio = 3;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final semantics = tester.ensureSemantics();
        addTearDown(semantics.dispose);
        final router = GoRouter(
          initialLocation: '/home',
          routes: [
            StatefulShellRoute.indexedStack(
              builder: (context, state, shell) =>
                  AppShell(navigationShell: shell),
              branches: [
                for (final id in ['home', 'chat', 'files', 'calendar', 'settings'])
                  StatefulShellBranch(
                    routes: [
                      GoRoute(
                        path: '/$id',
                        builder: (context, state) => Text('body-$id'),
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
              locale: Locale(locale),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(context).copyWith(
                  textScaler: TextScaler.linear(scale),
                ),
                child: child!,
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('body-home'), findsOneWidget);
        await expectLater(tester, meetsGuideline(iOSTapTargetGuideline));
        await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
        var pointerDowns = 0;
        void countPointer(PointerEvent event) {
          if (event is PointerDownEvent) pointerDowns++;
        }

        binding.pointerRouter.addGlobalRoute(countPointer);
        try {
          await navigateNativeDualDestination(tester, Icons.chat_bubble_outline);
        } finally {
          binding.pointerRouter.removeGlobalRoute(countPointer);
        }
        await tester.pumpAndSettle();
        expect(pointerDowns, 1);
        expect(router.routeInformationProvider.value.uri.path, '/chat');
        expect(find.text('body-chat'), findsOneWidget);
        expect(tester.takeException(), isNull);
      });
    }
  }
}
