import 'dart:async';
import 'dart:io';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/theme/app_theme.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/app/domain/entities/member_space_access_snapshot.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_participant.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/chat/presentation/chat_screen.dart';
import 'package:weave/features/chat/presentation/providers/chat_provider.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/shell/presentation/app_shell.dart';
import 'package:weave/l10n/generated/app_localizations.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

import '../helpers/fake_chat_repository.dart';
import '../helpers/in_memory_stores.dart';
import '../helpers/test_app.dart';

import '../../integration_test/dual_ios_collaboration_test.dart'
    show
        navigateNativeDualDestination,
        tapNativeDualBack,
        nativeDualChatSurfaceDiagnostic,
        nativeDualChatListDiagnostic,
        waitNativeDualChatConversation,
        openNativeDualChatCreationDialog,
        selectNativeDualChatSpace,
        sendNativeDualChatMessage,
        requireNativeDualPeerSelected,
        tapNativeDualPeer,
        tapNativeDualChatConversation,
        openNativeDualChatConversation,
        readNativeDualChatHistory,
        writeNativeDualPrivateJson,
        requireNativeDualRuntimeFixture,
        nativeDualSessionIdentityDigests,
        nativeDualChatReceiveDiagnostic;

const _readyChatSnapshot = WorkspaceCapabilitySnapshot(
  shellAccess: WorkspaceCapabilityState(
    capability: WorkspaceCapability.shellAccess,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  chat: WorkspaceCapabilityState(
    capability: WorkspaceCapability.chat,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  files: WorkspaceCapabilityState(
    capability: WorkspaceCapability.files,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  calendar: WorkspaceCapabilityState(
    capability: WorkspaceCapability.calendar,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  boards: WorkspaceCapabilityState(
    capability: WorkspaceCapability.boards,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
);

const _chatSpace = MemberSpaceAccessSnapshot(
  visibleSpaceRefs: {'workspace-default'},
  defaultSpaceReadable: true,
  displayNames: {'workspace-default': 'Product team'},
);

class _PeerRepository extends FakeChatRepository
    implements ChatParticipantRepository {
  List<ChatParticipant>? submitted;
  String? searchedSpace;
  String? submittedSpace;
  @override
  Future<ChatConversation> createConversation({
    required String title,
    String? spaceRef,
  }) async {
    submitted = [];
    submittedSpace = spaceRef;
    throw const ChatFailure.protocol('Controlled creation refusal');
  }

  @override
  Future<List<ChatParticipant>> searchMembers(
    String query, {
    String? spaceRef,
  }) async {
    searchedSpace = spaceRef;
    return const [
      ChatParticipant(userId: '@peer:weave.test', displayName: 'Private peer'),
    ];
  }

  @override
  Future<ChatConversation> createConversationWithParticipants({
    required String title,
    required List<ChatParticipant> participants,
    bool isDirect = false,
    String? spaceRef,
  }) async {
    submitted = participants;
    submittedSpace = spaceRef;
    throw const ChatFailure.protocol('Controlled creation refusal');
  }
}

class _ReceiveDiagnosticBridge extends RustMatrixCoreBridge {
  int calls = 0;
  Future<void> Function()? duringRead;
  @override
  Future<RustMatrixDecryptionDiagnostics> loadReceiveDiagnostics({
    required String profileKey,
  }) async {
    calls++;
    expect(profileKey, List.filled(64, 'a').join());
    await duringRead?.call();
    return const RustMatrixDecryptionDiagnostics(
      eventCount: 5,
      decryptedCount: 4,
      unableToDecryptCount: 0,
      plaintextCount: 1,
      reasonCounts: {'private untrusted reason': 7},
    );
  }
}

void main() {
  Map<String, dynamic> runtimeFixture(String runId) {
    final compiler = Process.runSync('python3', [
      '../tools/member_test_scenario.py',
      '--run-id',
      runId,
    ]);
    expect(compiler.exitCode, 0);
    final scenario =
        jsonDecode(compiler.stdout as String) as Map<String, dynamic>;
    final content = scenario['content'] as Map<String, dynamic>;
    return {
      'runId': runId,
      'role': 'A',
      'organizationRole': 'member',
      'sharedSpaceRef': scenario['sharedSpaceRef'],
      'memberScenario': scenario,
      for (final key in [
        'initialFileName',
        'replacementFileName',
        'initialContent',
        'replacementContent',
      ])
        key: content[key],
    };
  }

  test('one native binary accepts distinct current scenario executions', () {
    for (final runId in ['native-runtime-one', 'native-runtime-two']) {
      expect(requireNativeDualRuntimeFixture(runtimeFixture(runId)), runId);
    }
  });

  test('native runtime fixture rejects elevated actors and recipe drift', () {
    for (final mutation in <void Function(Map<String, dynamic>)>[
      (fixture) => fixture['organizationRole'] = 'owner',
      (fixture) => fixture['runId'] = 'another-run',
      (fixture) => fixture['initialContent'] = 'unrelated private content',
      (fixture) =>
          ((fixture['memberScenario'] as Map)['actors'] as List)
              .first['spacePermissions'] = [
            'VIEW',
            'EDIT',
            'ADMIN',
          ],
      (fixture) => (fixture['memberScenario'] as Map)['requiredDomains'] = [
        'chat',
        'files',
      ],
    ]) {
      final fixture = runtimeFixture('native-runtime-one');
      mutation(fixture);
      expect(
        () => requireNativeDualRuntimeFixture(fixture),
        throwsA(isA<TestFailure>()),
      );
    }
  });

  test(
    'actual native actor must hold only the prepared organization member role',
    () {
      for (final roles in [
        ['member'],
        ['owner'],
        ['admin'],
        ['member', 'owner'],
        ['member', 'agent-runtime.entitled'],
        <String>[],
      ]) {
        final claims = <String, dynamic>{
          'sub': 'prepared-member',
          'iss': 'https://issuer.test',
          'organization': {
            'owned-alias': {
              'id': 'owned-org',
              'resource_access': {
                'weave-app': {'roles': roles},
              },
            },
          },
        };
        Map<String, String> readIdentity() => nativeDualSessionIdentityDigests(
          claims,
          expectedSubjectDigest: sha256
              .convert(utf8.encode('prepared-member'))
              .toString(),
          expectedIssuer: 'https://issuer.test',
          expectedNativeOrganizationId: 'owned-org',
          expectedOrganizationRole: 'member',
        );
        if (roles.length == 1 && roles.single == 'member') {
          expect(readIdentity()['identityDigest'], isNotEmpty);
        } else {
          expect(readIdentity, throwsA(isA<TestFailure>()));
        }
      }
    },
  );

  for (final changed in [false, true]) {
    test(
      'cached receive diagnostic rejects changed binding=$changed',
      () async {
        final key =
            '$matrixOAuthBindingKeyPrefix${List.filled(64, 'b').join()}';
        final raw = jsonEncode({
          'profileKey': List.filled(64, 'a').join(),
          'private': 'member and endpoint must not be emitted',
        });
        final store = InMemorySecureStore({
          matrixOAuthCurrentBindingKey: key,
          key: raw,
        });
        final bridge = _ReceiveDiagnosticBridge();
        if (changed) {
          bridge.duringRead = () => store.delete(matrixOAuthCurrentBindingKey);
          await expectLater(
            nativeDualChatReceiveDiagnostic(store: store, bridge: bridge),
            throwsStateError,
          );
        } else {
          expect(
            await nativeDualChatReceiveDiagnostic(store: store, bridge: bridge),
            'NATIVE_PRODUCT_STAGE phase=chat-cached-receive '
            'events=5 decrypted=4 unableToDecrypt=0 plaintext=1',
          );
          expect(store.rawValue(matrixOAuthCurrentBindingKey), key);
          expect(store.rawValue(key), raw);
        }
        expect(bridge.calls, 1);
      },
    );
  }
  test(
    'cached receive diagnostic rejects invalid profile before native IO',
    () async {
      final key = '$matrixOAuthBindingKeyPrefix${List.filled(64, 'b').join()}';
      final bridge = _ReceiveDiagnosticBridge();
      await expectLater(
        nativeDualChatReceiveDiagnostic(
          store: InMemorySecureStore({
            matrixOAuthCurrentBindingKey: key,
            key: jsonEncode({'profileKey': 'private invalid profile'}),
          }),
          bridge: bridge,
        ),
        throwsStateError,
      );
      expect(bridge.calls, 0);
    },
  );
  test(
    'actual native claim digests require one organization and redact claims',
    () {
      final claims = <String, dynamic>{
        'sub': 'owned-subject',
        'iss': 'https://issuer.test',
        'organization': {
          'owned-alias': {'id': 'owned-org'},
        },
        'private': 'private-token-must-not-print',
      };
      final digests = nativeDualSessionIdentityDigests(
        claims,
        expectedSubjectDigest: sha256
            .convert(utf8.encode('owned-subject'))
            .toString(),
        expectedIssuer: 'https://issuer.test',
        expectedNativeOrganizationId: 'owned-org',
      );
      expect(
        digests['identityDigest'],
        sha256.convert(utf8.encode('owned-subject')).toString(),
      );
      expect(
        digests['identityIssuerDigest'],
        sha256.convert(utf8.encode('https://issuer.test')).toString(),
      );
      expect(
        digests['nativeOrganizationDigest'],
        sha256.convert(utf8.encode('owned-org')).toString(),
      );
      expect(digests.toString(), isNot(contains('private-token')));
      // The canonical tenant is not the native provider organization coordinate.
      expect(
        () => nativeDualSessionIdentityDigests(
          claims,
          expectedSubjectDigest: digests['identityDigest']!,
          expectedIssuer: 'https://issuer.test',
          expectedNativeOrganizationId: 'canonical-tenant',
        ),
        throwsA(
          isA<TestFailure>().having(
            (e) => e.message,
            'fixed native coordinate mismatch',
            'Actual native session native organization does not match its owned fixture.',
          ),
        ),
      );

      for (final expected in ['subject', 'issuer', 'organization']) {
        expect(
          () => nativeDualSessionIdentityDigests(
            claims,
            expectedSubjectDigest: expected == 'subject'
                ? 'a' * 64
                : digests['identityDigest']!,
            expectedIssuer: expected == 'issuer'
                ? 'https://other.invalid'
                : 'https://issuer.test',
            expectedNativeOrganizationId: expected == 'organization'
                ? 'other-org'
                : 'owned-org',
          ),
          throwsA(
            isA<TestFailure>().having(
              (e) => e.toString(),
              'safe mismatch',
              isNot(contains('private-token')),
            ),
          ),
        );
      }
      for (final key in ['sub', 'iss']) {
        for (final missing in [null, '']) {
          expect(
            () => nativeDualSessionIdentityDigests(
              {...claims, key: missing},
              expectedSubjectDigest: sha256
                  .convert(utf8.encode('owned-subject'))
                  .toString(),
              expectedIssuer: 'https://issuer.test',
              expectedNativeOrganizationId: 'owned-org',
            ),
            throwsA(isA<TestFailure>()),
          );
        }
      }
      for (final organization in [
        null,
        {},
        {'one': {}},
        {
          'one': {'id': 'owned-org'},
          'two': {'id': 'other-org'},
        },
      ]) {
        expect(
          () => nativeDualSessionIdentityDigests(
            {...claims, 'organization': organization},
            expectedSubjectDigest: sha256
                .convert(utf8.encode('owned-subject'))
                .toString(),
            expectedIssuer: 'https://issuer.test',
            expectedNativeOrganizationId: 'owned-org',
          ),
          throwsA(
            isA<TestFailure>().having(
              (e) => e.toString(),
              'safe failure',
              isNot(contains('private-token')),
            ),
          ),
        );
      }
    },
  );
  test(
    'actual actor writer preserves owner-only result across atomic rewrites',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'weave-owned-writer-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final result = File('${directory.path}/result.json');
      await result.writeAsString('{}');
      final permissions = await Process.run('chmod', ['600', result.path]);
      expect(permissions.exitCode, 0);
      expect((await result.stat()).mode & 0x1ff, 0x180);
      await writeNativeDualPrivateJson(result, {'role': 'A', 'resources': {}});
      expect((await result.stat()).mode & 0x1ff, 0x180);
      await File('${result.path}.tmp').writeAsString('owned-stale-staging');
      await writeNativeDualPrivateJson(result, {
        'role': 'A',
        'resources': {'roomId': '!owned:example.invalid'},
      });
      expect((await result.stat()).mode & 0x1ff, 0x180);
      expect(jsonDecode(await result.readAsString()), {
        'role': 'A',
        'resources': {'roomId': '!owned:example.invalid'},
      });
      expect(File('${result.path}.tmp').existsSync(), isFalse);
    },
  );

  test(
    'actual actor writer rejects destination and staging symlinks',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'weave-owned-writer-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final protected = File('${directory.path}/protected.json');
      await protected.writeAsString('owned-original');
      final result = File('${directory.path}/result.json');
      await Link(result.path).create(protected.path);
      await expectLater(
        writeNativeDualPrivateJson(result, {}),
        throwsA(isA<TestFailure>()),
      );
      expect(await protected.readAsString(), 'owned-original');
      await Link(result.path).delete();
      await result.writeAsString('owned-result');
      await Link('${result.path}.tmp').create(protected.path);
      await expectLater(
        writeNativeDualPrivateJson(result, {}),
        throwsA(isA<TestFailure>()),
      );
      expect(await protected.readAsString(), 'owned-original');
      expect(await result.readAsString(), 'owned-result');
    },
  );

  for (final locale in ['en', 'de']) {
    testWidgets(
      'phone invitation row opens and localized back exits room; $locale',
      (tester) async {
        tester.view.physicalSize = const Size(1206, 2622);
        tester.view.devicePixelRatio = 3;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        await tester.runAsync(() async {
          final packages = File('.dart_tool/package_config.json');
          final config =
              jsonDecode(await packages.readAsString()) as Map<String, dynamic>;
          final flutter = (config['packages'] as List)
              .cast<Map<String, dynamic>>()
              .singleWhere((p) => p['name'] == 'flutter');
          final font = File.fromUri(
            Directory.fromUri(
              packages.absolute.uri.resolve(flutter['rootUri'] as String),
            ).uri.resolve(
              '../../bin/cache/artifacts/material_fonts/Roboto-Regular.ttf',
            ),
          );
          final loader = FontLoader('HarnessPhoneRoboto')
            ..addFont(
              Future.value(ByteData.sublistView(await font.readAsBytes())),
            );
          await loader.load();
        });
        const target = ChatConversation(
          id: '!owned-invite:example.invalid',
          title: 'Weave UI focus-dual-ios-controlled-phone',
          previewType: ChatConversationPreviewType.none,
          unreadCount: 0,
          isInvite: true,
          isDirectMessage: false,
        );
        final repository = FakeChatRepository(
          loadConversationsHandler: () async => [
            // Keep this scroll-preparation regression below a populated list;
            // a single conversation is now deliberately visible immediately.
            for (var index = 0; index < 7; index++)
              ChatConversation(
                id: '!earlier-$index:example.invalid',
                title: 'Earlier conversation $index',
                previewType: ChatConversationPreviewType.none,
                unreadCount: 0,
                isInvite: false,
                isDirectMessage: true,
              ),
            target,
          ],
          loadRoomTimelineHandler: (_) async => ChatRoomTimeline(
            roomId: target.id,
            roomTitle: target.title,
            isInvite: true,
            canSendMessages: false,
            messages: [],
          ),
        );
        final router = GoRouter(
          initialLocation: '/chat',
          routes: [
            StatefulShellRoute.indexedStack(
              builder: (context, state, shell) =>
                  AppShell(navigationShell: shell),
              branches: [
                StatefulShellBranch(
                  routes: [
                    GoRoute(path: '/', builder: (_, _) => const SizedBox()),
                  ],
                ),
                StatefulShellBranch(
                  routes: [
                    GoRoute(
                      path: '/chat',
                      builder: (_, _) => const ChatScreen(),
                      routes: [
                        GoRoute(
                          path: AppRoutes.chatRoomRelative,
                          builder: (_, state) => ChatRoomScreen(
                            conversation: state.extra! as ChatConversation,
                          ),
                        ),
                      ],
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
            overrides: [
              chatRepositoryProvider.overrideWithValue(repository),
              workspaceCapabilitySnapshotProvider.overrideWith(
                (ref) => const AsyncData(_readyChatSnapshot),
              ),
            ],
            child: MaterialApp.router(
              theme: AppTheme.light.copyWith(
                platform: TargetPlatform.iOS,
                textTheme: AppTheme.light.textTheme.apply(
                  fontFamily: 'HarnessPhoneRoboto',
                ),
              ),
              locale: Locale(locale),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              routerConfig: router,
            ),
          ),
        );
        await tester.pumpAndSettle();
        final container = ProviderScope.containerOf(
          tester.element(find.byType(ChatScreen)),
        );
        await waitNativeDualChatConversation(
          tester,
          container,
          target.title,
          deadline: DateTime.now().add(const Duration(seconds: 2)),
        );
        final title = find.text(target.title);
        expect(title.hitTestable(), findsNothing);
        final scroll = Scrollable.of(tester.element(title)).position;
        final initialOffset = scroll.pixels;
        final initialCenter = tester.getCenter(title);
        await tester.ensureVisible(title);
        expect(scroll.pixels, greaterThan(initialOffset));
        expect(tester.getCenter(title), initialCenter);
        expect(title.hitTestable(), findsNothing);
        await tester.pump();
        expect(tester.getCenter(title), isNot(initialCenter));
        expect(title.hitTestable(), findsOneWidget);
        scroll.jumpTo(initialOffset);
        await tester.pump();
        expect(title.hitTestable(), findsNothing);
        var pointerDowns = 0;
        void observePointer(PointerEvent event) {
          if (event is PointerDownEvent) pointerDowns++;
        }

        tester.binding.pointerRouter.addGlobalRoute(observePointer);
        try {
          await tapNativeDualChatConversation(tester, target.title);
        } finally {
          tester.binding.pointerRouter.removeGlobalRoute(observePointer);
        }
        expect(pointerDowns, 1);
        await tester.pumpAndSettle();
        expect(find.byType(ChatRoomScreen), findsOneWidget);
        expect(
          find.byKey(const Key('chat-accept-invitation-button')),
          findsOneWidget,
        );
        expect(
          tester
              .widget<ChatRoomScreen>(find.byType(ChatRoomScreen))
              .conversation
              .id,
          target.id,
        );
        pointerDowns = 0;
        tester.binding.pointerRouter.addGlobalRoute(observePointer);
        try {
          await tapNativeDualBack(
            tester,
            deadline: DateTime.now().add(const Duration(seconds: 2)),
          );
        } finally {
          tester.binding.pointerRouter.removeGlobalRoute(observePointer);
        }
        await tester.pumpAndSettle();
        expect(pointerDowns, 1);
        expect(find.byType(ChatRoomScreen), findsNothing);
        expect(find.byType(ChatScreen), findsOneWidget);
        expect(router.routeInformationProvider.value.uri.path, '/chat');
        Object? missingBack;
        pointerDowns = 0;
        tester.binding.pointerRouter.addGlobalRoute(observePointer);
        try {
          await tapNativeDualBack(
            tester,
            deadline: DateTime.now().add(const Duration(seconds: 2)),
          );
        } catch (error) {
          missingBack = error;
        } finally {
          tester.binding.pointerRouter.removeGlobalRoute(observePointer);
        }
        expect(missingBack, isA<TestFailure>());
        expect(pointerDowns, 0);
        expect(router.routeInformationProvider.value.uri.path, '/chat');
      },
    );
  }

  testWidgets(
    'native Chat selects the intended shared Space before search and create',
    (tester) async {
      final repository = _PeerRepository();
      final spaceNames = {
        'workspace-default': 'General team',
        for (var index = 0; index < 20; index++)
          'space:$index': 'Project ${index.toString().padLeft(2, '0')}',
        'space:shared': 'Shared project',
      };
      await tester.pumpWidget(
        createTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            workspaceCapabilitySnapshotProvider.overrideWith(
              (ref) => const AsyncData(_readyChatSnapshot),
            ),
            weaveApiMemberSpacesProvider.overrideWith(
              (ref) async => MemberSpaceAccessSnapshot(
                visibleSpaceRefs: spaceNames.keys.toSet(),
                defaultSpaceReadable: true,
                displayNames: spaceNames,
              ),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      final deadline = DateTime.now().add(const Duration(seconds: 10));
      final name = await openNativeDualChatCreationDialog(
        tester,
        deadline: deadline,
      );
      final selector = find.byKey(const Key('chat-create-space-selector'));
      expect(tester.widget<DropdownButton<String>>(selector).value, isNull);
      await selectNativeDualChatSpace(
        tester,
        'space:shared',
        deadline: deadline,
      );
      expect(
        tester.widget<DropdownButton<String>>(selector).value,
        'space:shared',
      );
      await tester.enterText(name, 'Shared room');
      await tester.enterText(
        find.byKey(const Key('chat-create-conversation-participant-field')),
        'Private peer',
      );
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      expect(repository.searchedSpace, 'space:shared');
      final peer = find.widgetWithText(CheckboxListTile, 'Private peer');
      var pointerDowns = 0;
      void observePointer(PointerEvent event) {
        if (event is PointerDownEvent) pointerDowns++;
      }

      tester.binding.pointerRouter.addGlobalRoute(observePointer);
      try {
        await tapNativeDualPeer(tester, peer, deadline: deadline);
      } finally {
        tester.binding.pointerRouter.removeGlobalRoute(observePointer);
      }
      expect(pointerDowns, 1);
      expect(tester.widget<CheckboxListTile>(peer).value, isTrue);
      expect(peer.hitTestable(), findsOneWidget);
      final create = find.byKey(
        const Key('chat-create-conversation-submit-button'),
      );
      await tester.ensureVisible(create);
      await tester.tap(create);
      await tester.pumpAndSettle();
      expect(repository.submittedSpace, 'space:shared');
      expect(repository.submitted?.single.userId, '@peer:weave.test');
    },
  );

  testWidgets(
    'native Chat refuses an absent intended Space without choosing another',
    (tester) async {
      final repository = _PeerRepository();
      await tester.pumpWidget(
        createTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            workspaceCapabilitySnapshotProvider.overrideWith(
              (ref) => const AsyncData(_readyChatSnapshot),
            ),
            weaveApiMemberSpacesProvider.overrideWith(
              (ref) async => _chatSpace,
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      final deadline = DateTime.now().add(const Duration(seconds: 5));
      await openNativeDualChatCreationDialog(tester, deadline: deadline);
      var pointerDowns = 0;
      void observePointer(PointerEvent event) {
        if (event is PointerDownEvent) pointerDowns++;
      }

      tester.binding.pointerRouter.addGlobalRoute(observePointer);
      try {
        await expectLater(
          selectNativeDualChatSpace(tester, 'space:absent', deadline: deadline),
          throwsA(isA<TestFailure>()),
        );
      } finally {
        tester.binding.pointerRouter.removeGlobalRoute(observePointer);
      }
      expect(pointerDowns, 0);
      expect(repository.searchedSpace, isNull);
      expect(repository.submitted, isNull);
    },
  );

  testWidgets(
    'phone keyboard peer uses actual visibility preparation before one selection',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      tester.view.viewInsets = const FakeViewPadding(bottom: 336);
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetViewInsets);
      final repository = _PeerRepository();
      await tester.pumpWidget(
        createTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            weaveApiMemberSpacesProvider.overrideWith(
              (ref) async => _chatSpace,
            ),
            workspaceCapabilitySnapshotProvider.overrideWith(
              (ref) => const AsyncData(_readyChatSnapshot),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const Key('chat-create-conversation-button')),
      );
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('chat-create-conversation-name-field')),
        'Private room',
      );
      await tester.enterText(
        find.byKey(const Key('chat-create-conversation-participant-field')),
        'Private peer',
      );
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      final peer = find.widgetWithText(CheckboxListTile, 'Private peer');
      expect(peer, findsOneWidget);
      expect(peer.hitTestable(), findsNothing);
      var pointerDowns = 0;
      void observePointer(PointerEvent event) {
        if (event is PointerDownEvent) pointerDowns++;
      }

      tester.binding.pointerRouter.addGlobalRoute(observePointer);
      try {
        await tapNativeDualPeer(
          tester,
          peer,
          deadline: DateTime.now().add(const Duration(seconds: 2)),
        );
      } finally {
        tester.binding.pointerRouter.removeGlobalRoute(observePointer);
      }
      expect(pointerDowns, 1);
      expect(tester.widget<CheckboxListTile>(peer).value, isTrue);
      expect(peer.hitTestable(), findsOneWidget);
      final create = find.byKey(
        const Key('chat-create-conversation-submit-button'),
      );
      await tester.ensureVisible(create);
      await tester.tap(create);
      await tester.pumpAndSettle();
      expect(repository.submitted?.single.userId, '@peer:weave.test');
    },
  );

  for (final activate in [true, false]) {
    testWidgets('actual dialog requires checked peer; activation=$activate', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      tester.view.viewInsets = const FakeViewPadding(bottom: 336);
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetViewInsets);
      final repository = _PeerRepository();
      await tester.pumpWidget(
        createTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            weaveApiMemberSpacesProvider.overrideWith(
              (ref) async => _chatSpace,
            ),
            workspaceCapabilitySnapshotProvider.overrideWith(
              (ref) => const AsyncData(_readyChatSnapshot),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const Key('chat-create-conversation-button')),
      );
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('chat-create-conversation-name-field')),
        'Private room',
      );
      await tester.enterText(
        find.byKey(const Key('chat-create-conversation-participant-field')),
        'Private peer',
      );
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      final peer = find.widgetWithText(CheckboxListTile, 'Private peer');
      expect(peer, findsOneWidget);
      if (activate) {
        await tapNativeDualPeer(
          tester,
          peer,
          deadline: DateTime.now().add(const Duration(seconds: 2)),
        );
        await requireNativeDualPeerSelected(
          tester,
          peer,
          deadline: DateTime.now().add(const Duration(seconds: 1)),
        );
      } else {
        // A cancelled real pointer gesture never reaches the selection callback.
        // The former continuation still reaches Create; checked admission cannot.
        final gesture = await tester.startGesture(tester.getCenter(peer));
        await gesture.cancel();
        await tester.pumpAndSettle();
        await expectLater(
          requireNativeDualPeerSelected(
            tester,
            peer,
            deadline: DateTime.now().add(const Duration(seconds: 1)),
          ),
          throwsA(isA<TestFailure>()),
        );
        expect(repository.submitted, isNull);
      }
      // Direct creation independently rejects an unselected recipient.
      final create = find.byKey(
        const Key('chat-create-conversation-submit-button'),
      );
      await tester.ensureVisible(create);
      await tester.pump();
      await tester.tap(create);
      await tester.pumpAndSettle();
      expect(repository.submitted?.length, activate ? 1 : null);
      if (activate) {
        expect(repository.submitted?.single.userId, '@peer:weave.test');
      }
    });
  }

  testWidgets(
    'cold list timeout diagnostics never initialize product providers',
    (tester) async {
      final repository = FakeChatRepository();
      await tester.pumpWidget(
        createTestApp(
          const SizedBox(),
          overrides: [chatRepositoryProvider.overrideWithValue(repository)],
        ),
      );
      final container = ProviderScope.containerOf(
        tester.element(find.byType(Scaffold)),
      );
      expect(container.exists(chatProvider), isFalse);
      expect(container.exists(appBootstrapProvider), isFalse);
      expect(container.exists(workspaceCapabilitySnapshotProvider), isFalse);
      Object? failure;
      try {
        await waitNativeDualChatConversation(
          tester,
          container,
          'Private missing room',
          deadline: DateTime.now().add(const Duration(milliseconds: 30)),
        );
      } catch (error) {
        failure = error;
      }
      expect(failure, isA<TestFailure>());
      await tester.pump();
      final diagnostic = nativeDualChatListDiagnostic(
        tester,
        null,
        null,
        null,
        'Private missing room',
      );
      expect(diagnostic, contains('listPhase=unavailable'));
      expect(diagnostic, contains('listInitialized=false'));
      expect(
        diagnostic,
        contains('bootstrapInitialized=false capabilitiesInitialized=false'),
      );
      expect(diagnostic, isNot(contains('Private missing room')));
      expect(container.exists(chatProvider), isFalse);
      expect(container.exists(appBootstrapProvider), isFalse);
      expect(container.exists(workspaceCapabilitySnapshotProvider), isFalse);
      expect(repository.loadConversationsCalls, 0);
    },
  );

  for (final locale in ['en', 'de']) {
    testWidgets(
      'actual wrapped shell navigation uses unique hittable identifiers at large $locale text',
      (tester) async {
        tester.view.physicalSize = const Size(390, 844);
        tester.view.devicePixelRatio = 1;
        tester.platformDispatcher.textScaleFactorTestValue = 2;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
        final router = GoRouter(
          routes: [
            StatefulShellRoute.indexedStack(
              builder: (context, state, shell) =>
                  AppShell(navigationShell: shell),
              branches: [
                for (var i = 0; i < 5; i++)
                  StatefulShellBranch(
                    routes: [
                      GoRoute(
                        path: const [
                          '/',
                          '/chat',
                          '/files',
                          '/calendar',
                          '/settings',
                        ][i],
                        builder: (_, _) => Text('actual-page-$i'),
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
            overrides: [
              preferencesStoreProvider.overrideWith(
                (ref) => InMemoryPreferencesStore(),
              ),
            ],
            child: MaterialApp.router(
              routerConfig: router,
              locale: Locale(locale),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.byType(NavigationBar), findsNothing);
        const icons = [
          Icons.home_outlined,
          Icons.chat_bubble_outline,
          Icons.folder_outlined,
          Icons.calendar_month_outlined,
          Icons.settings_outlined,
        ];
        for (var i = 0; i < icons.length; i++) {
          await navigateNativeDualDestination(tester, icons[i]);
          await tester.pumpAndSettle();
          expect(find.text('actual-page-$i'), findsOneWidget);
          expect(
            nativeDualChatListDiagnostic(tester, null, null, null, 'unused'),
            contains('navigationSelected=$i'),
          );
        }
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets(
    'duplicate ordinary navigation identifiers fail before any activation',
    (tester) async {
      var taps = 0;
      await tester.pumpWidget(
        createTestApp(
          Row(
            children: [
              for (var i = 0; i < 2; i++)
                Semantics(
                  identifier: 'nav-chat',
                  child: TextButton(
                    onPressed: () => taps++,
                    child: const Text('Chat'),
                  ),
                ),
            ],
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        () => navigateNativeDualDestination(tester, Icons.chat_bubble_outline),
        throwsA(isA<TestFailure>()),
      );
      expect(taps, 0);
    },
  );

  for (final locale in ['en', 'de']) {
    testWidgets('actual localized Home/Chat navigation; locale=$locale', (
      tester,
    ) async {
      var selected = 0;
      await tester.pumpWidget(
        MaterialApp(
          locale: Locale(locale),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: StatefulBuilder(
            builder: (context, setState) {
              final copy = AppLocalizations.of(context);
              return Scaffold(
                body: Text('selected=$selected'),
                bottomNavigationBar: NavigationBar(
                  selectedIndex: selected,
                  onDestinationSelected: (value) =>
                      setState(() => selected = value),
                  destinations: [
                    NavigationDestination(
                      icon: const Icon(Icons.home_outlined),
                      label: copy.navHome,
                    ),
                    NavigationDestination(
                      icon: const Icon(Icons.chat_bubble_outline),
                      label: copy.navChat,
                    ),
                  ],
                ),
              );
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      await navigateNativeDualDestination(tester, Icons.chat_bubble_outline);
      expect(
        tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex,
        1,
      );
      expect(find.text('selected=1'), findsOneWidget);
      await navigateNativeDualDestination(tester, Icons.home_outlined);
      expect(
        tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex,
        0,
      );
      expect(find.text('selected=0'), findsOneWidget);
    });
  }

  for (final locale in ['en', 'de']) {
    testWidgets(
      'actual localized Chat refresh discovers invitation once; $locale',
      (tester) async {
        const target = ChatConversation(
          id: '!owned-invite:example.invalid',
          title: 'Owned new invitation',
          previewType: ChatConversationPreviewType.none,
          unreadCount: 0,
          isInvite: true,
          isDirectMessage: false,
        );
        var calls = 0;
        final repository = FakeChatRepository(
          loadConversationsHandler: () async => ++calls == 1 ? [] : [target],
        );
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              chatRepositoryProvider.overrideWithValue(repository),
              workspaceCapabilitySnapshotProvider.overrideWith(
                (ref) => const AsyncData(_readyChatSnapshot),
              ),
            ],
            child: MaterialApp(
              locale: Locale(locale),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: const Scaffold(body: ChatScreen()),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text(target.title), findsNothing);
        final container = ProviderScope.containerOf(
          tester.element(find.byType(ChatScreen)),
        );
        await waitNativeDualChatConversation(
          tester,
          container,
          target.title,
          deadline: DateTime.now().add(const Duration(seconds: 2)),
        );
        expect(find.text(target.title), findsOneWidget);
        expect(repository.loadConversationsCalls, 2);
        await waitNativeDualChatConversation(
          tester,
          container,
          target.title,
          deadline: DateTime.now().add(const Duration(seconds: 2)),
        );
        expect(repository.loadConversationsCalls, 2);
        expect(
          container.read(chatProvider).conversations.single.isInvite,
          isTrue,
        );
      },
    );
  }

  for (final locale in ['en', 'de']) {
    testWidgets('room re-entry waits for the real Chat list; $locale', (
      tester,
    ) async {
      const target = ChatConversation(
        id: '!owned-reentry:example.invalid',
        title: 'Owned delayed room',
        previewType: ChatConversationPreviewType.none,
        unreadCount: 0,
        isInvite: false,
        isDirectMessage: true,
      );
      final pending = Completer<List<ChatConversation>>();
      final repository = FakeChatRepository(
        loadConversationsHandler: () => pending.future,
        loadRoomTimelineHandler: (_) async => ChatRoomTimeline(
          roomId: target.id,
          roomTitle: target.title,
          isInvite: false,
          canSendMessages: true,
          messages: [],
        ),
      );
      final router = GoRouter(
        initialLocation: '/',
        routes: [
          StatefulShellRoute.indexedStack(
            builder: (context, state, shell) =>
                AppShell(navigationShell: shell),
            branches: [
              StatefulShellBranch(
                routes: [
                  GoRoute(path: '/', builder: (_, _) => const SizedBox()),
                ],
              ),
              StatefulShellBranch(
                routes: [
                  GoRoute(
                    path: '/chat',
                    builder: (_, _) => const ChatScreen(),
                    routes: [
                      GoRoute(
                        path: AppRoutes.chatRoomRelative,
                        builder: (_, state) => ChatRoomScreen(
                          conversation: state.extra! as ChatConversation,
                        ),
                      ),
                    ],
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
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            preferencesStoreProvider.overrideWith(
              (ref) => InMemoryPreferencesStore(),
            ),
            workspaceCapabilitySnapshotProvider.overrideWith(
              (ref) => const AsyncData(_readyChatSnapshot),
            ),
          ],
          child: MaterialApp.router(
            locale: Locale(locale),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            routerConfig: router,
          ),
        ),
      );
      await tester.pumpAndSettle();
      final container = ProviderScope.containerOf(
        tester.element(find.byType(AppShell)),
      );
      final ready = Timer(const Duration(milliseconds: 750), () {
        pending.complete(const [target]);
      });
      addTearDown(ready.cancel);
      await openNativeDualChatConversation(
        tester,
        container,
        target.title,
        roomId: target.id,
        deadline: DateTime.now().add(const Duration(seconds: 5)),
      );
      expect(find.byType(ChatRoomScreen), findsOneWidget);
      expect(
        tester
            .widget<ChatRoomScreen>(find.byType(ChatRoomScreen))
            .conversation
            .id,
        target.id,
      );
      expect(repository.loadConversationsCalls, 1);
      await openNativeDualChatConversation(
        tester,
        container,
        target.title,
        roomId: target.id,
        deadline: DateTime.now().add(const Duration(seconds: 5)),
      );
      expect(repository.loadConversationsCalls, 1);
      await expectLater(
        openNativeDualChatConversation(
          tester,
          container,
          target.title,
          roomId: '!another-room:example.invalid',
          deadline: DateTime.now().add(const Duration(seconds: 5)),
        ),
        throwsA(isA<TestFailure>()),
      );
      await expectLater(
        openNativeDualChatConversation(
          tester,
          container,
          target.title,
          roomId: target.id,
          deadline: DateTime.now().subtract(const Duration(seconds: 1)),
        ),
        throwsA(isA<TestFailure>()),
      );
    });
  }

  for (final blocked in ['non-hittable', 'ambiguous', 'no-result']) {
    testWidgets('room lookup never bypasses actual UI recovery: $blocked', (
      tester,
    ) async {
      final repository = FakeChatRepository();
      final child = blocked == 'ambiguous'
          ? const Column(
              children: [
                Expanded(child: ChatScreen()),
                Expanded(child: ChatScreen()),
              ],
            )
          : IgnorePointer(
              ignoring: blocked == 'non-hittable',
              child: const ChatScreen(),
            );
      await tester.pumpWidget(
        createTestApp(
          child,
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            workspaceCapabilitySnapshotProvider.overrideWith(
              (ref) => const AsyncData(_readyChatSnapshot),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      final container = ProviderScope.containerOf(
        tester.element(find.byType(ChatScreen).first),
      );
      final before = repository.loadConversationsCalls;
      Object? failure;
      try {
        await waitNativeDualChatConversation(
          tester,
          container,
          'Private missing room',
          deadline: DateTime.now().add(const Duration(milliseconds: 50)),
        );
      } catch (error) {
        failure = error;
      }
      expect(failure, isA<TestFailure>());
      expect(
        repository.loadConversationsCalls,
        before + (blocked == 'no-result' ? 1 : 0),
      );
      expect(find.text('Private missing room'), findsNothing);
    });
  }

  testWidgets(
    'private actual Chat list snapshots loading, invite and hidden error',
    (tester) async {
      const privateTitle =
          'private-user@example.invalid token=https://secret.invalid';
      const invitation = ChatConversation(
        id: '!private-id:secret.invalid',
        title: privateTitle,
        previewType: ChatConversationPreviewType.none,
        unreadCount: 0,
        isInvite: true,
        isDirectMessage: false,
      );
      final pending = Completer<List<ChatConversation>>();
      final repository = FakeChatRepository(
        loadConversationsHandler: () => pending.future,
      );
      Future<void> render(bool hidden) async {
        await tester.pumpWidget(
          createTestApp(
            Offstage(offstage: hidden, child: const ChatScreen()),
            overrides: [
              chatRepositoryProvider.overrideWithValue(repository),
              workspaceCapabilitySnapshotProvider.overrideWith(
                (ref) => const AsyncData(_readyChatSnapshot),
              ),
            ],
          ),
        );
        await tester.pump();
      }

      await render(false);
      final container = ProviderScope.containerOf(
        tester.element(find.byType(ChatScreen)),
      );
      String snapshot() => nativeDualChatListDiagnostic(
        tester,
        container.read(chatProvider),
        const AsyncData(BootstrapState.ready()),
        const AsyncData(_readyChatSnapshot),
        privateTitle,
      );
      final loading = snapshot();
      expect(loading, contains('listPhase=loading'));
      expect(
        loading,
        contains('conversationCount=0 invitedCount=0 matchingCount=0'),
      );
      pending.complete(const [invitation]);
      await tester.pumpAndSettle();
      final content = snapshot();
      expect(
        content,
        contains('listVisible=1 listMounted=1 listPhase=content'),
      );
      expect(
        content,
        contains(
          'conversationCount=1 invitedCount=1 matchingCount=1 visibleMatchingCount=1',
        ),
      );
      await render(true);
      final hidden = snapshot();
      expect(hidden, contains('listVisible=0 listMounted=1'));
      expect(hidden, contains('visibleMatchingCount=0 mountedMatchingCount=1'));
      repository.loadConversationsHandler = () async =>
          throw const ChatFailure.sessionRequired(privateTitle);
      await container.read(chatProvider.notifier).retry();
      await tester.pump();
      final failed = snapshot();
      expect(failed, contains('listPhase=error listFailure=sessionRequired'));
      expect(
        failed,
        contains('conversationCount=0 invitedCount=0 matchingCount=0'),
      );
      for (final diagnostic in [loading, content, hidden, failed]) {
        expect(diagnostic, isNot(contains('private-user')));
        expect(diagnostic, isNot(contains('secret.invalid')));
        expect(diagnostic, isNot(contains('private-id')));
        expect(diagnostic, isNot(contains('token=')));
        expect(diagnostic, isNot(contains('https:')));
      }
    },
  );

  const conversation = ChatConversation(
    id: '!owned-room:example.invalid',
    title: 'Owned room',
    previewType: ChatConversationPreviewType.none,
    unreadCount: 0,
    isInvite: false,
    isDirectMessage: false,
  );
  testWidgets('private Chat snapshot distinguishes offstage and redacts errors', (
    tester,
  ) async {
    final repository = FakeChatRepository();
    Future<void> render(bool hidden) async {
      await tester.pumpWidget(
        createTestApp(
          Offstage(
            offstage: hidden,
            child: const ChatRoomScreen(conversation: conversation),
          ),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            preferencesStoreProvider.overrideWith(
              (ref) => InMemoryPreferencesStore(),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
    }

    await render(true);
    final hidden = nativeDualChatSurfaceDiagnostic(
      tester,
      const AsyncLoading(),
      const AsyncLoading(),
    );
    expect(hidden, contains('roomVisible=0 roomMounted=1'));
    expect(hidden, contains('bootstrapLoading=true bootstrapError=false'));
    await render(false);
    const privateError = AppFailure.storage(
      'private-account@example.invalid secret=https://example.invalid/token',
    );
    final visible = nativeDualChatSurfaceDiagnostic(
      tester,
      AsyncError(privateError, StackTrace.current),
      AsyncError(privateError, StackTrace.current),
    );
    expect(visible, contains('roomVisible=1 roomMounted=1'));
    expect(visible, contains('invitedRoomVisible=0 nonInvitedRoomVisible=1'));
    expect(visible, contains('bootstrapError=true'));
    expect(
      visible,
      contains(
        'bootstrapErrorClass=AppFailure capabilitiesLoading=false capabilitiesError=true',
      ),
    );
    expect(visible, isNot(contains('private-account')));
    expect(visible, isNot(contains('secret')));
    expect(visible, isNot(contains('https:')));
    final resolved = nativeDualChatSurfaceDiagnostic(
      tester,
      const AsyncData(BootstrapState.error(AppFailure.validation('private'))),
      const AsyncLoading(),
    );
    expect(
      resolved,
      contains('bootstrapPhase=error bootstrapFailure=validation'),
    );
    expect(resolved, isNot(contains('private')));
    final unknown = nativeDualChatSurfaceDiagnostic(
      tester,
      AsyncError(Object(), StackTrace.current),
      const AsyncLoading(),
    );
    expect(unknown, contains('bootstrapErrorClass=unavailable'));
  });

  for (final locale in ['en', 'de']) {
    for (final branch in ['session', 'invitation', 'archive']) {
      testWidgets('private actual room branch; locale=$locale branch=$branch', (
        tester,
      ) async {
        final repository = FakeChatRepository(
          loadRoomTimelineHandler: (roomId) async {
            if (branch == 'session') {
              throw const ChatFailure.sessionRequired(
                'private provider details',
              );
            }
            return ChatRoomTimeline(
              roomId: roomId,
              roomTitle: 'Owned room',
              isInvite: false,
              canSendMessages: true,
              messages: [],
            );
          },
        );
        final room = ChatConversation(
          id: conversation.id,
          title: conversation.title,
          previewType: conversation.previewType,
          unreadCount: 0,
          isInvite: branch == 'invitation',
          isDirectMessage: false,
        );
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              chatRepositoryProvider.overrideWithValue(repository),
              preferencesStoreProvider.overrideWith(
                (ref) => InMemoryPreferencesStore(),
              ),
            ],
            child: MaterialApp(
              locale: Locale(locale),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: ChatRoomScreen(conversation: room),
            ),
          ),
        );
        await tester.pumpAndSettle();
        if (branch == 'archive') {
          final copy = AppLocalizations.of(
            tester.element(find.byType(ChatRoomScreen)),
          );
          await tester.tap(find.byTooltip(copy.chatRoomArchivedMessagesAction));
          await tester.pumpAndSettle();
        }
        final diagnostic = nativeDualChatSurfaceDiagnostic(
          tester,
          const AsyncData(BootstrapState.ready()),
          const AsyncLoading(),
        );
        expect(diagnostic, contains('roomVisible=1 roomMounted=1'));
        expect(
          diagnostic,
          contains(
            'sessionRequiredCount=${branch == 'session' ? 1 : 0} invitationCount=${branch == 'invitation' ? 1 : 0} archiveReturnCount=${branch == 'archive' ? 1 : 0}',
          ),
        );
        expect(find.byType(TextField), findsNothing);
        expect(diagnostic, isNot(contains('private')));
        expect(diagnostic, isNot(contains(conversation.id)));
      });
    }
  }

  for (final locale in ['en', 'de']) {
    for (final condition in [
      'complete',
      'missing',
      'reordered',
      'wrong-room',
      'expired',
    ]) {
      testWidgets(
        'restored phone history scroll/readback; $locale $condition',
        (tester) async {
          tester.view.physicalSize = const Size(1206, 2622);
          tester.view.devicePixelRatio = 3;
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          const run = 'local-ios-focus-20261020-2308';
          final bodies = [
            'A UI $run',
            'B UI $run',
            'B remains online $run',
            'A recoverable $run',
            'B background catchup $run',
            // Long retained history must remain lazy even with compact room UI.
            for (var index = 0; index < 8; index++)
              'A retained follow-up $index $run',
          ];
          final messages = [
            for (var index = 0; index < bodies.length; index++)
              ChatMessage(
                id: 'owned-event-$index',
                senderId: bodies[index].startsWith('A ')
                    ? 'owned-a'
                    : 'owned-b',
                senderDisplayName: bodies[index].startsWith('A ')
                    ? 'Owned actor A'
                    : 'Owned actor B',
                sentAt: DateTime(2026).add(Duration(seconds: index)),
                isMine: bodies[index].startsWith('A '),
                deliveryState: ChatMessageDeliveryState.sent,
                contentType: ChatMessageContentType.text,
                text: bodies[index],
              ),
          ];
          if (condition == 'missing') messages.removeAt(0);
          if (condition == 'reordered') {
            final first = messages[0];
            messages[0] = messages[1];
            messages[1] = first;
          }
          final repository = FakeChatRepository(
            loadRoomTimelineHandler: (_) async => ChatRoomTimeline(
              roomId: conversation.id,
              roomTitle: conversation.title,
              isInvite: false,
              canSendMessages: true,
              messages: messages,
            ),
          );
          await tester.pumpWidget(
            ProviderScope(
              overrides: [
                chatRepositoryProvider.overrideWithValue(repository),
                preferencesStoreProvider.overrideWith(
                  (ref) => InMemoryPreferencesStore(),
                ),
              ],
              child: MaterialApp(
                theme: AppTheme.light,
                locale: Locale(locale),
                localizationsDelegates: AppLocalizations.localizationsDelegates,
                supportedLocales: AppLocalizations.supportedLocales,
                home: const ChatRoomScreen(conversation: conversation),
              ),
            ),
          );
          await tester.pumpAndSettle();
          expect(find.text(bodies[0]), findsNothing);
          final readback = readNativeDualChatHistory(
            tester,
            bodies,
            roomId: condition == 'wrong-room'
                ? '!another-owned-room'
                : conversation.id,
            deadline: DateTime.now().add(
              Duration(seconds: condition == 'expired' ? -1 : 5),
            ),
          );
          if (condition == 'complete') {
            await readback;
            expect(find.text(bodies.last).hitTestable(), findsOneWidget);
          } else {
            await expectLater(readback, throwsA(isA<TestFailure>()));
          }
          expect(repository.sendMessageCalls, 0);
          await tester.pumpWidget(const SizedBox.shrink());
          await tester.pump(const Duration(seconds: 1));
        },
      );
    }
  }

  for (final inset in [0.0, 350.0, 380.0]) {
    testWidgets(
      'phone Chat send retains selected tab with keyboard inset=$inset',
      (tester) async {
        tester.view.physicalSize = const Size(1206, 2622);
        tester.view.devicePixelRatio = 3;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        addTearDown(tester.view.resetViewInsets);
        final messages = <ChatMessage>[];
        final repository = FakeChatRepository(
          loadRoomTimelineHandler: (_) async => ChatRoomTimeline(
            roomId: conversation.id,
            roomTitle: conversation.title,
            isInvite: false,
            canSendMessages: true,
            messages: List.of(messages),
          ),
          sendMessageHandler: ({required roomId, required message}) async {
            await Future<void>.delayed(const Duration(milliseconds: 500));
            messages.add(
              ChatMessage(
                id: 'owned-event',
                senderId: 'owned-author',
                senderDisplayName: 'Owned author',
                sentAt: DateTime(2026),
                isMine: true,
                deliveryState: ChatMessageDeliveryState.sent,
                contentType: ChatMessageContentType.text,
                text: message,
              ),
            );
          },
        );
        await tester.pumpWidget(
          createTestApp(
            const ChatRoomScreen(conversation: conversation),
            overrides: [
              chatRepositoryProvider.overrideWithValue(repository),
              preferencesStoreProvider.overrideWith(
                (ref) => InMemoryPreferencesStore(),
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();
        final tabs = DefaultTabController.of(
          tester.element(find.byType(TabBar)),
        );
        expect(tabs.index, 0);
        tester.view.viewInsets = FakeViewPadding(bottom: inset * 3);
        await tester.pumpAndSettle();
        await sendNativeDualChatMessage(
          tester,
          'Owned message',
          deadline: DateTime.now().add(const Duration(seconds: 5)),
        );
        await tester.pumpAndSettle();
        expect(repository.sendMessageCalls, 1);
        expect(tabs.index, 0);
        expect(find.text('Owned message'), findsOneWidget);
        final composer = find.byType(TextField);
        expect(composer, findsOneWidget);
        expect(tester.widget<TextField>(composer).controller!.text, isEmpty);
        expect(
          nativeDualChatSurfaceDiagnostic(
            tester,
            const AsyncData(BootstrapState.ready()),
            const AsyncLoading(),
          ),
          contains('workspaceTabCount=1 workspaceTabIndex=0'),
        );
      },
    );
  }

  testWidgets('offscreen navigation target is scrolled then tapped once', (
    tester,
  ) async {
    var selected = 1;
    var taps = 0;
    await tester.pumpWidget(
      createTestApp(
        StatefulBuilder(
          builder: (context, setState) {
            final copy = AppLocalizations.of(context);
            return SingleChildScrollView(
              child: Column(
                children: [
                  const SizedBox(height: 1200),
                  NavigationBar(
                    selectedIndex: selected,
                    onDestinationSelected: (index) => setState(() {
                      selected = index;
                      taps++;
                    }),
                    destinations: [
                      NavigationDestination(
                        icon: const Icon(Icons.home_outlined),
                        label: copy.navHome,
                      ),
                      NavigationDestination(
                        icon: const Icon(Icons.chat_bubble_outline),
                        label: copy.navChat,
                      ),
                    ],
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    final copy = AppLocalizations.of(
      tester.element(find.byType(NavigationBar)),
    );
    final home = find.text(copy.navHome);
    expect(home.hitTestable(), findsNothing);
    await navigateNativeDualDestination(tester, Icons.home_outlined);
    await tester.pumpAndSettle();
    expect(selected, 0);
    expect(taps, 1);
    expect(home.hitTestable(), findsOneWidget);
  });

  testWidgets('lost native keyboard input never taps an empty Send', (
    tester,
  ) async {
    final pointerDowns = <Offset>[];
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async => ChatRoomTimeline(
        roomId: conversation.id,
        roomTitle: conversation.title,
        isInvite: false,
        canSendMessages: true,
        messages: const [],
      ),
    );
    await tester.pumpWidget(
      createTestApp(
        Listener(
          onPointerDown: (event) => pointerDowns.add(event.position),
          child: const ChatRoomScreen(conversation: conversation),
        ),
        overrides: [
          chatRepositoryProvider.overrideWithValue(repository),
          preferencesStoreProvider.overrideWith(
            (ref) => InMemoryPreferencesStore(),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();
    final controller = tester
        .widget<TextField>(find.byType(TextField))
        .controller!;
    void loseInput() {
      if (controller.text.isNotEmpty) controller.clear();
    }

    controller.addListener(loseInput);
    Object? failure;
    try {
      await sendNativeDualChatMessage(
        tester,
        'Must reach the actual composer',
        deadline: DateTime.now().add(const Duration(seconds: 2)),
      );
    } catch (error) {
      failure = error;
    }
    controller.removeListener(loseInput);
    expect(failure, isA<TestFailure>());
    expect(failure.toString(), contains('keyboard input did not reach'));
    final sendBounds = tester.getRect(
      find.widgetWithText(
        FilledButton,
        AppLocalizations.of(
          tester.element(find.byType(ChatRoomScreen)),
        ).chatRoomSendButton,
      ),
    );
    expect(pointerDowns.where(sendBounds.contains), isEmpty);
    expect(repository.sendMessageCalls, 0);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
  });

  for (final closedInput in [false, true]) {
    for (final denied in [false, true]) {
      testWidgets(
        'real delayed composer sends once and verifies proof; denied=$denied closedInput=$closedInput',
        (tester) async {
          const body = 'Controlled widget message';
          final messages = <ChatMessage>[];
          final repository = FakeChatRepository(
            loadRoomTimelineHandler: (_) async {
              await Future<void>.delayed(const Duration(milliseconds: 500));
              return ChatRoomTimeline(
                roomId: conversation.id,
                roomTitle: conversation.title,
                isInvite: false,
                canSendMessages: true,
                messages: List.of(messages),
              );
            },
            sendMessageHandler: ({required roomId, required message}) async {
              expect(roomId, conversation.id);
              expect(message, body);
              if (denied) {
                throw const ChatFailure.protocol('Controlled send denial');
              }
              messages.add(
                ChatMessage(
                  id: 'owned-event',
                  senderId: 'owned-author',
                  senderDisplayName: 'Owned author',
                  sentAt: DateTime(2026),
                  isMine: true,
                  deliveryState: ChatMessageDeliveryState.sent,
                  contentType: ChatMessageContentType.text,
                  text: message,
                ),
              );
            },
          );
          await tester.pumpWidget(
            createTestApp(
              const ChatRoomScreen(conversation: conversation),
              overrides: [
                chatRepositoryProvider.overrideWithValue(repository),
                preferencesStoreProvider.overrideWith(
                  (ref) => InMemoryPreferencesStore(),
                ),
              ],
            ),
          );
          expect(repository.sendMessageCalls, 0);
          if (closedInput) {
            await tester.pumpAndSettle();
            await tester.showKeyboard(find.byType(TextField));
            tester.testTextInput.closeConnection();
            await tester.pump();
          }
          Object? failure;
          try {
            await sendNativeDualChatMessage(
              tester,
              body,
              deadline: DateTime.now().add(Duration(seconds: denied ? 1 : 5)),
            );
          } catch (error) {
            failure = error;
          }
          expect(repository.sendMessageCalls, 1);
          final composer = find.descendant(
            of: find.byType(ChatRoomScreen),
            matching: find.byType(TextField),
          );
          if (denied) {
            expect(failure, isA<TestFailure>());
            expect(messages, isEmpty);
          } else {
            expect(failure, isNull);
            expect(find.text(body), findsOneWidget);
            expect(
              tester.widget<TextField>(composer).controller?.text,
              isEmpty,
            );
          }
          await tester.pumpWidget(const SizedBox.shrink());
          await tester.pump(const Duration(seconds: 1));
        },
      );
    }
  }

  testWidgets('waits for enabled real action and actual delayed dialog', (
    tester,
  ) async {
    var enabled = false;
    var taps = 0;
    late StateSetter update;
    await tester.pumpWidget(
      MaterialApp(
        home: StatefulBuilder(
          builder: (context, setState) {
            update = setState;
            return Scaffold(
              body: IconButton(
                key: const Key('chat-create-conversation-button'),
                onPressed: enabled
                    ? () {
                        taps++;
                        Future<void>.delayed(
                          const Duration(milliseconds: 500),
                          () {
                            if (!context.mounted) return;
                            showDialog<void>(
                              context: context,
                              builder: (_) => const AlertDialog(
                                content: TextField(
                                  key: Key(
                                    'chat-create-conversation-name-field',
                                  ),
                                ),
                              ),
                            );
                          },
                        );
                      }
                    : null,
                icon: const Icon(Icons.add),
              ),
            );
          },
        ),
      ),
    );
    Timer(
      const Duration(milliseconds: 500),
      () => update(() => enabled = true),
    );
    final name = await openNativeDualChatCreationDialog(
      tester,
      deadline: DateTime.now().add(const Duration(seconds: 5)),
    );
    expect(taps, 1);
    expect(name, findsOneWidget);
    await tester.enterText(name, 'Actual dialog content');
    expect(find.text('Actual dialog content'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('disabled action fails closed without invoking a callback', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: IconButton(
            key: Key('chat-create-conversation-button'),
            onPressed: null,
            icon: Icon(Icons.add),
          ),
        ),
      ),
    );
    Object? failure;
    try {
      await openNativeDualChatCreationDialog(
        tester,
        deadline: DateTime.now().add(const Duration(milliseconds: 50)),
      );
    } catch (error) {
      failure = error;
    }
    expect(failure, isA<TestFailure>());
    expect(
      find.byKey(const Key('chat-create-conversation-name-field')),
      findsNothing,
    );
  });

  testWidgets('absent real dialog fails after one actual action', (
    tester,
  ) async {
    var taps = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: IconButton(
            key: const Key('chat-create-conversation-button'),
            onPressed: () => taps++,
            icon: const Icon(Icons.add),
          ),
        ),
      ),
    );
    Object? failure;
    try {
      await openNativeDualChatCreationDialog(
        tester,
        deadline: DateTime.now().add(const Duration(milliseconds: 50)),
      );
    } catch (error) {
      failure = error;
    }
    expect(failure, isA<TestFailure>());
    expect(taps, 1);
    expect(
      find.byKey(const Key('chat-create-conversation-name-field')),
      findsNothing,
    );
  });
}
