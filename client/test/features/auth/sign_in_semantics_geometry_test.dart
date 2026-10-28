import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/app/domain/use_cases/sign_in_with_oidc.dart';
import 'package:weave/features/app/presentation/providers/app_application_providers.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/presentation/sign_in_screen.dart';
import 'package:weave/features/server_config/data/dtos/server_configuration_dto.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../helpers/in_memory_stores.dart';
import '../../helpers/server_config_test_data.dart';

class _ObservedSignIn implements SignInWithOidc {
  int calls = 0;

  @override
  Future<void> call({required bool isInteractiveSignInSupported}) async {
    calls++;
    throw const AuthFailure.cancelled('Owned test completion.');
  }
}

Offset _semanticCenterInView(SemanticsNode node) {
  var point = node.rect.center;
  for (SemanticsNode? current = node; current != null; current = current.parent) {
    final transform = current.transform;
    if (transform != null) {
      point = MatrixUtils.transformPoint(transform, point);
    }
  }
  return point;
}

void main() {
  for (final language in ['en', 'de']) {
    testWidgets('native Sign In geometry activates the actual button $language', (
      tester,
    ) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(402, 874);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      final semantics = tester.ensureSemantics();
      final signIn = _ObservedSignIn();
      final container = ProviderContainer.test(
        overrides: [
          appStartDiscoveryClientProvider.overrideWith(
            (ref) => buildTestDiscoveryClient(
              ref.watch(serverConfigurationRepositoryProvider),
            ),
          ),
          preferencesStoreProvider.overrideWith(
            (ref) => InMemoryPreferencesStore({
              serverConfigurationStorageKey: ServerConfigurationDto.fromConfiguration(
                buildTestConfiguration(),
              ).encode(),
            }),
          ),
          signInWithOidcProvider.overrideWithValue(signIn),
        ],
      );
      addTearDown(container.dispose);
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            locale: Locale(language),
            theme: ThemeData(platform: TargetPlatform.iOS),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: const SignInScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final action = find.byKey(const ValueKey('weave.auth.sign-in'));
      final button = find.descendant(of: action, matching: find.byType(FilledButton));
      await tester.ensureVisible(button);
      await tester.pumpAndSettle();
      final node = tester.getSemantics(action);
      final actual = tester.getRect(button);
      expect(node.rect.size, actual.size,
          reason: 'Native Sign In must not inherit ancestor geometry.');
      final nativePoint = _semanticCenterInView(node);
      expect(actual.contains(nativePoint), isTrue);
      await tester.tapAt(nativePoint);
      await tester.pumpAndSettle();
      expect(signIn.calls, 1, reason: 'A native geometry-based tap must reach Sign In.');
      final current = tester.getSemantics(action);
      expect(current.getSemanticsData().hasAction(ui.SemanticsAction.tap), isTrue);
      current.owner!.performAction(current.id, ui.SemanticsAction.tap);
      await tester.pumpAndSettle();
      expect(signIn.calls, 2, reason: 'Screen-reader activation remains equivalent.');
      semantics.dispose();
    });
  }
}
