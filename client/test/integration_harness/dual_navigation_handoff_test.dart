import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:integration_test/integration_test.dart';
import 'package:weave/core/theme/app_theme.dart';
import 'package:weave/features/shell/presentation/app_shell.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../integration_test/dual_ios_collaboration_test.dart'
    show navigateNativeDualDestination;

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  binding.platformDispatcher.semanticsEnabledTestValue = true;
  ui.PlatformDispatcher.instance.setSemanticsTreeEnabled(true);
  binding.shouldPropagateDevicePointerEvents = true;

  for (final locale in ['en', 'de']) {
    for (final scale in [1.0, 2.0]) {
      testWidgets(
        'live shell navigation after workspace entry: $locale $scale',
        (tester) async {
          tester.view.physicalSize = const Size(1206, 2622);
          tester.view.devicePixelRatio = 3;
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          await tester.runAsync(() async {
            final packages = File('.dart_tool/package_config.json');
            final config = jsonDecode(await packages.readAsString())
                as Map<String, dynamic>;
            final flutter = (config['packages'] as List)
                .cast<Map<String, dynamic>>()
                .singleWhere((package) => package['name'] == 'flutter');
            final font = File.fromUri(
              packages.absolute.uri.resolve(flutter['rootUri'] as String).resolve(
                '../../bin/cache/artifacts/material_fonts/Roboto-Regular.ttf',
              ),
            );
            final loader = FontLoader('HarnessPhoneRoboto')
              ..addFont(Future.value(ByteData.sublistView(await font.readAsBytes())));
            await loader.load();
          });
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
                theme: AppTheme.light.copyWith(
                  platform: TargetPlatform.iOS,
                  textTheme: AppTheme.light.textTheme.apply(
                    fontFamily: 'HarnessPhoneRoboto',
                  ),
                ),
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
          expect(tester.getSize(find.byType(AppShell)), const Size(402, 874));
          expect(find.text('body-home'), findsOneWidget);
          if (locale == 'en' && scale == 1) {
            expect(find.byType(NavigationBar), findsOneWidget);
          }
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
        },
        timeout: const Timeout(Duration(minutes: 2)),
      );
    }
  }
}
