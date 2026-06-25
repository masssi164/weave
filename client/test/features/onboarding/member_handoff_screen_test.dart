import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';
import 'package:weave/features/onboarding/domain/use_cases/consume_member_handoff.dart';
import 'package:weave/features/onboarding/presentation/member_handoff_screen.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../helpers/in_memory_stores.dart';

class _ThrowingConsumeMemberHandoff implements ConsumeMemberHandoff {
  const _ThrowingConsumeMemberHandoff(this.error);

  final Object error;

  @override
  Future<MemberHandoff> call(Uri uri) async {
    throw error;
  }
}

void main() {
  group('MemberHandoffScreen', () {
    testWidgets('shows and records a support-safe handoff failure code', (
      tester,
    ) async {
      final preferencesStore = InMemoryPreferencesStore();
      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith((ref) => preferencesStore),
          consumeMemberHandoffProvider.overrideWithValue(
            const _ThrowingConsumeMemberHandoff(
              AppFailure.bootstrap(
                'WEAVE-APP-START-TLS-FAILED: The workspace start configuration could not be reached.',
              ),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: MemberHandoffScreen(
              uri: Uri.parse(
                'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fapi.weave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('We could not open this Weave invite'), findsOneWidget);
      expect(find.textContaining('WEAVE-APP-START-TLS-FAILED'), findsOneWidget);

      final rawEvidence = preferencesStore.rawString(
        lastHandoffConsumedStorageKey,
      );
      expect(rawEvidence, isNotNull);
      final evidence = jsonDecode(rawEvidence!) as Map<String, dynamic>;
      expect(
        evidence['schemaVersion'],
        'weave.client.last_handoff_consumed.v1',
      );
      expect(evidence['result'], 'failed');
      expect(evidence['phase'], 'app_start_discovery');
      expect(evidence['errorCode'], 'WEAVE-APP-START-TLS-FAILED');
      expect(evidence['handoffRef'], 'handoff-s32-massimo-dogfood-home');
      expect(evidence['supportSafe'], isTrue);
    });
  });
}
