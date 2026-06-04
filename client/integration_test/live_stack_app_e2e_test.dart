import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:integration_test/integration_test.dart';
import 'package:matrix/matrix.dart' as sdk;
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as sqflite_ffi;
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/core/persistence/secure_store.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/features/chat/data/repositories/matrix_chat_repository.dart';
import 'package:weave/features/chat/data/services/matrix_auth_browser.dart';
import 'package:weave/features/chat/data/services/matrix_client_factory.dart';
import 'package:weave/features/chat/data/services/matrix_client_factory_io.dart';
import 'package:weave/features/chat/data/services/matrix_conversation_service.dart';
import 'package:weave/features/chat/data/services/matrix_room_service.dart';
import 'package:weave/features/chat/data/services/matrix_session_service.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/presentation/providers/files_provider.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';
import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

import 'package:weave/main.dart';

import 'helpers/auth_helper.dart';
import 'helpers/live_oidc_test_driver.dart';
import 'helpers/test_config.dart';
import 'helpers/test_http_overrides.dart';

void main() {
  final previousPlatformErrorHandler = ui.PlatformDispatcher.instance.onError;
  ui.PlatformDispatcher.instance.onError = (Object error, StackTrace stack) {
    if (_isStrayKeyboardKeyUpAssertion(error, stack)) {
      // ignore: avoid_print
      print('IGNORED_STRAY_KEYBOARD_KEYUP_ASSERTION $error');
      _resetKeyboardTestState();
      return true;
    }
    return previousPlatformErrorHandler?.call(error, stack) ?? false;
  };

  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  // The self-hosted macOS runner can expose host accessibility state to the
  // launched Flutter app. Keep the live E2E deterministic and prevent a
  // platform-requested semantics handle from leaking past test teardown.
  binding.platformDispatcher.semanticsEnabledTestValue = false;
  HttpOverrides.global = TestHttpOverrides();

  late TestConfig config;
  late LiveOidcTestDriver liveOidcDriver;
  Directory? matrixSupportDirectory;
  SdkMatrixClientFactory? liveMatrixClientFactory;

  setUp(() async {
    config = TestConfig.fromEnvironment();
    config.requireCredentials();
    liveOidcDriver = LiveOidcTestDriver(config: config);
    final supportDirectory = await Directory.systemTemp.createTemp(
      'weave-live-e2e-matrix-',
    );
    matrixSupportDirectory = supportDirectory;
    sqflite_ffi.sqfliteFfiInit();
    liveMatrixClientFactory = SdkMatrixClientFactory(
      appSupportDirectoryProvider: () async => supportDirectory,
      databaseOpener: sqflite_ffi.databaseFactoryFfi.openDatabase,
      allowUnsupportedPlatformForTesting: true,
    );
  });

  tearDown(() async {
    await liveMatrixClientFactory?.dispose();
    final supportDirectory = matrixSupportDirectory;
    if (supportDirectory != null && await supportDirectory.exists()) {
      await supportDirectory.delete(recursive: true);
    }
  });

  testWidgets(
    'real live-stack sign-in, Matrix connect, profile, files, and calendar facades',
    (tester) async {
      final serverConfig = ServerConfiguration(
        providerType: OidcProviderType.keycloak,
        oidcIssuerUrl: config.issuerUrl,
        oidcClientRegistration: OidcClientRegistration.manual(
          clientId: config.clientId,
        ),
        serviceEndpoints: ServiceEndpoints(
          matrixHomeserverUrl: config.matrixHomeserverUrl,
          nextcloudBaseUrl: config.nextcloudBaseUrl,
          backendApiBaseUrl: config.backendApiBaseUrl,
        ),
      );

      _resetKeyboardTestState();
      final secureStore = _MemorySecureStore();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            secureStoreProvider.overrideWithValue(secureStore),
            serverConfigurationRepositoryProvider.overrideWithValue(
              _MemoryServerConfigurationRepository(serverConfig),
            ),
            oidcClientProvider.overrideWithValue(liveOidcDriver),
            matrixAuthBrowserProvider.overrideWithValue(liveOidcDriver),
            matrixClientFactoryProvider.overrideWithValue(
              liveMatrixClientFactory!,
            ),
          ],
          child: const WeaveApp(),
        ),
      );

      await _pumpUntilSettled(tester);
      _resetKeyboardTestState();

      final container = ProviderScope.containerOf(
        tester.element(find.byType(WeaveApp)),
      );

      await _waitFor(
        tester,
        () =>
            find.text('Anmelden').evaluate().isNotEmpty ||
            find.text('Sign In').evaluate().isNotEmpty,
        reason: 'App should reach the sign-in screen with the live config.',
        diagnostics: () {
          final bootstrap = container.read(appBootstrapProvider);
          final texts = find
              .byType(Text)
              .evaluate()
              .map((element) => (element.widget as Text).data)
              .whereType<String>()
              .join(' | ');
          return 'bootstrap=$bootstrap\ntexts=$texts';
        },
      );

      final appSession = await AuthHelper().signInForAppSession(config);
      await secureStore.write(
        authSessionStorageKey,
        AuthSessionDto.fromSession(appSession).encode(),
      );
      await container.read(appBootstrapProvider.notifier).retry();
      _resetKeyboardTestState();
      await tester.pump();

      await _waitFor(
        tester,
        () {
          final session = container
              .read(weaveAuthenticatedSessionProvider)
              .asData
              ?.value;
          return session != null && session.accessToken.isNotEmpty;
        },
        reason: 'The backend session should be restorable after live sign-in.',
        timeout: const Duration(seconds: 30),
        diagnostics: () {
          final session = container.read(weaveAuthenticatedSessionProvider);
          return 'weaveAuthenticatedSession=$session';
        },
      );
      final restoredSession = container
          .read(weaveAuthenticatedSessionProvider)
          .asData
          ?.value;
      // ignore: avoid_print
      print(
        'AUTH_RESULT signedIn=${restoredSession != null} '
        'accessTokenPresent=${restoredSession?.accessToken.isNotEmpty ?? false}',
      );

      final providerHttpClient = createTrustedTestHttpClient();
      addTearDown(providerHttpClient.close);
      final providerRegistryResponse = await providerHttpClient.get(
        config.apiUri('/api/providers/status'),
        headers: <String, String>{
          'Accept': 'application/json',
          'Authorization': 'Bearer ${appSession.accessToken}',
        },
      );
      final providerRegistryMemberForbidden =
          providerRegistryResponse.statusCode == 403;
      final providerRegistryBody = providerRegistryResponse.body;
      final providerRegistryBodySupportSafe = !RegExp(
        r'(Authorization|api[_-]?token|/api/v3/|/work_packages/|/projects/|SecretRef|secretref://)',
        caseSensitive: false,
      ).hasMatch(providerRegistryBody);
      final profileReadiness = _decodeHttpJson(
        await providerHttpClient.get(
          config.apiUri('/api/profile/readiness'),
          headers: <String, String>{
            'Accept': 'application/json',
            'Authorization': 'Bearer ${appSession.accessToken}',
          },
        ),
        operation: 'read profile readiness',
      );
      final profileReadinessOk =
          profileReadiness['contractId'] == 'CEFACADE' &&
          profileReadiness['endpoint'] == '/profile/readiness' &&
          profileReadiness['backendOwnedFacade'] == true &&
          profileReadiness['directProviderCallsAllowed'] == false &&
          profileReadiness['supportSafe'] == true;
      // ignore: avoid_print
      print(
        'PROVIDER_STACK_RESULT '
        'memberRegistryForbidden=$providerRegistryMemberForbidden '
        'memberRegistryStatus=${providerRegistryResponse.statusCode} '
        'registryBodySupportSafe=$providerRegistryBodySupportSafe '
        'profileReadinessContract=${profileReadiness['contractId']} '
        'profileReadinessEndpoint=${profileReadiness['endpoint']} '
        'profileReadinessOk=$profileReadinessOk',
      );

      final profileRepository = container.read(userProfileRepositoryProvider);
      final originalProfile = await profileRepository.loadProfile();
      if (originalProfile == null) {
        fail(
          'live_e2e_result authSignedIn=true profileLoaded=false '
          'profileUpdated=false reason=backend-profile-facade-returned-null',
        );
      }
      final liveE2eSuffix = DateTime.now().millisecondsSinceEpoch;
      final liveDisplayName = 'Weave Live E2E $liveE2eSuffix';
      var profileRestored = false;
      addTearDown(() async {
        if (!profileRestored) {
          try {
            await profileRepository.updateProfile(
              UserProfileUpdate(
                displayName: originalProfile.displayName,
                locale: originalProfile.locale,
                timezone: originalProfile.timezone,
              ),
            );
          } catch (_) {
            // The main assertion prints profile evidence; teardown should not
            // mask the original live-stack failure signal.
          }
        }
      });
      final updatedProfile = await profileRepository.updateProfile(
        UserProfileUpdate(
          displayName: liveDisplayName,
          locale: originalProfile.locale,
          timezone: originalProfile.timezone,
        ),
      );
      final reloadedProfile = await profileRepository.loadProfile();
      final profileUpdated =
          updatedProfile.displayName == liveDisplayName &&
          reloadedProfile?.displayName == liveDisplayName;
      // ignore: avoid_print
      print(
        'PROFILE_RESULT userId=${updatedProfile.userId} '
        'username=${updatedProfile.username} '
        'updated=$profileUpdated '
        'displayName=${updatedProfile.displayName}',
      );
      await profileRepository.updateProfile(
        UserProfileUpdate(
          displayName: originalProfile.displayName,
          locale: originalProfile.locale,
          timezone: originalProfile.timezone,
        ),
      );
      profileRestored = true;

      final chatRepository = MatrixChatRepository(
        sessionService: container.read(matrixSessionServiceProvider),
        conversationService: container.read(matrixConversationServiceProvider),
        roomService: container.read(matrixRoomServiceProvider),
        serverConfigurationRepository: container.read(
          serverConfigurationRepositoryProvider,
        ),
      );
      var matrixConnected = false;
      Object? matrixConnectError;
      try {
        await chatRepository.connect();
        matrixConnected = true;
      } catch (error) {
        matrixConnectError = error;
      }
      if (!matrixConnected) {
        final connectError = _supportSafeDiagnostic(matrixConnectError);
        // ignore: avoid_print
        print(
          'MATRIX_RESULT connected=false '
          'testHarnessDirectMatrix=true '
          'productDirectProviderCallsAllowed=false '
          'connectError=$connectError',
        );
        fail('matrix_connect_failed error=$connectError');
      }

      final matrixClientFactory = container.read(matrixClientFactoryProvider);
      final matrixClient = await matrixClientFactory.getClientForHomeserver(
        config.matrixHomeserverUrl,
      );
      final roomName =
          'weave-live-e2e-${DateTime.now().millisecondsSinceEpoch}';
      final roomId = await matrixClient.createGroupChat(
        groupName: roomName,
        enableEncryption: false,
        waitForSync: true,
        federated: false,
      );
      final sentMessage =
          'live-e2e message ${DateTime.now().toUtc().toIso8601String()}';
      await chatRepository.sendMessage(roomId: roomId, message: sentMessage);
      final timeline = await chatRepository.loadRoomTimeline(roomId);
      final deliveredMessage = timeline.messages
          .where((message) => message.text == sentMessage)
          .toList(growable: false);
      // ignore: avoid_print
      print(
        'CHAT_RESULT roomId=$roomId roomName=$roomName '
        'timelineMessages=${timeline.messages.length} '
        'matchedMessages=${deliveredMessage.length}',
      );

      // Keep the Matrix outcome visible while still validating the backend files path.
      // ignore: avoid_print
      print(
        'MATRIX_RESULT connected=$matrixConnected '
        'testHarnessDirectMatrix=true '
        'productDirectProviderCallsAllowed=false',
      );

      final chatSecurityRepository = container.read(
        chatSecurityRepositoryProvider,
      );
      var e2eeSecurityState = await chatSecurityRepository.loadSecurityState(
        refresh: true,
      );
      var e2eeBootstrapGeneratedRecoveryKey = false;
      if (e2eeSecurityState.bootstrapState ==
              ChatSecurityBootstrapState.notInitialized ||
          e2eeSecurityState.bootstrapState ==
              ChatSecurityBootstrapState.partiallyInitialized) {
        final recoveryKey = await chatSecurityRepository.bootstrapSecurity();
        e2eeBootstrapGeneratedRecoveryKey = recoveryKey.trim().isNotEmpty;
        e2eeSecurityState = await chatSecurityRepository.loadSecurityState(
          refresh: true,
        );
      }

      final encryptedRoomName =
          'weave-live-e2ee-${DateTime.now().millisecondsSinceEpoch}';
      final encryptedRoomId = await matrixClient.createGroupChat(
        groupName: encryptedRoomName,
        enableEncryption: true,
        waitForSync: true,
        federated: false,
      );
      final encryptedRoom = await _waitForEncryptedMatrixRoom(
        tester,
        matrixClient,
        encryptedRoomId,
      );
      final encryptedWireEventsBefore = await _loadAuthoritativeWireEvents(
        matrixClient,
        encryptedRoomId,
      );
      final encryptedWireEventIdsBefore = encryptedWireEventsBefore
          .map((event) => event.eventId)
          .toSet();
      final encryptedMessage =
          'live-e2ee message ${DateTime.now().toUtc().toIso8601String()}';
      await chatRepository.sendMessage(
        roomId: encryptedRoomId,
        message: encryptedMessage,
      );
      final encryptedWireProof = await _waitForAuthoritativeEncryptedWireEvent(
        tester,
        matrixClient,
        encryptedRoomId,
        previousEventIds: encryptedWireEventIdsBefore,
        plaintext: encryptedMessage,
      );
      final encryptedTimeline = await _waitForDecryptedEncryptedTimeline(
        tester,
        chatRepository,
        encryptedRoomId,
        encryptedMessage,
      );
      final decryptedEncryptedMessages = encryptedTimeline.messages
          .where((message) => message.text == encryptedMessage)
          .toList(growable: false);
      final e2eeCryptoAvailable =
          matrixClient.encryptionEnabled && matrixClient.encryption != null;
      final e2eeRoomEncrypted = encryptedRoom.encrypted;
      final e2eeSecurityReady =
          e2eeSecurityState.bootstrapState ==
              ChatSecurityBootstrapState.ready &&
          e2eeSecurityState.secretStorageReady &&
          e2eeSecurityState.crossSigningReady;
      final e2eeSecurityPostureHonest =
          e2eeSecurityState.bootstrapState !=
              ChatSecurityBootstrapState.signedOut &&
          e2eeSecurityState.bootstrapState !=
              ChatSecurityBootstrapState.unavailable &&
          e2eeSecurityState.secretStorageReady &&
          e2eeSecurityState.crossSigningReady;
      final e2eeEncryptedEventObserved =
          encryptedWireProof.newEncryptedEvents.isNotEmpty &&
          !encryptedWireProof.plaintextLeaked;
      // ignore: avoid_print
      print(
        'E2EE_RESULT roomId=$encryptedRoomId roomName=$encryptedRoomName '
        'cryptoAvailable=$e2eeCryptoAvailable '
        'bootstrapState=${e2eeSecurityState.bootstrapState} '
        'accountVerification=${e2eeSecurityState.accountVerificationState} '
        'deviceVerification=${e2eeSecurityState.deviceVerificationState} '
        'keyBackup=${e2eeSecurityState.keyBackupState} '
        'secretStorageReady=${e2eeSecurityState.secretStorageReady} '
        'crossSigningReady=${e2eeSecurityState.crossSigningReady} '
        'securityPostureHonest=$e2eeSecurityPostureHonest '
        'bootstrapGeneratedRecoveryKey=$e2eeBootstrapGeneratedRecoveryKey '
        'roomEncrypted=$e2eeRoomEncrypted '
        'encryptedWireEvents=${encryptedWireProof.newEncryptedEvents.length} '
        'encryptedWireEventIds=${encryptedWireProof.newEncryptedEvents.map((event) => event.eventId).join(',')} '
        'encryptedWirePlaintextLeaked=${encryptedWireProof.plaintextLeaked} '
        'encryptedTimelineMessages=${decryptedEncryptedMessages.length}',
      );

      await container.read(filesProvider.notifier).connect();
      _resetKeyboardTestState();
      await tester.pump();

      await _waitFor(
        tester,
        () {
          final asyncState = container.read(filesProvider);
          if (asyncState.hasError) {
            return true;
          }
          if (!asyncState.hasValue) {
            return false;
          }
          final state = asyncState.requireValue;
          return state.connectionState.isConnected &&
              state.directoryListing != null;
        },
        reason:
            'Backend files facade should connect and return a real directory listing.',
        timeout: const Duration(minutes: 1),
        diagnostics: () {
          final asyncState = container.read(filesProvider);
          if (asyncState.hasError) {
            return 'filesError=${asyncState.error}';
          }
          if (!asyncState.hasValue) {
            return 'filesState=loading';
          }
          final state = asyncState.requireValue;
          return 'filesConnected=${state.connectionState.isConnected} '
              'filesStatus=${state.connectionState.status} '
              'filesMessage=${state.connectionState.message} '
              'directoryFailure=${state.directoryFailure?.message} '
              'directoryFailureCause=${state.directoryFailure?.cause} '
              'configuredBackendApiBaseUrl=${config.backendApiBaseUrl} '
              'hasListing=${state.directoryListing != null}';
        },
      );

      final filesState = container.read(filesProvider).requireValue;
      final filesFacadeConnected =
          filesState.connectionState.isConnected &&
          filesState.directoryListing != null;

      final seededFileName =
          'weave-live-e2e-${DateTime.now().millisecondsSinceEpoch}.txt';
      final seededFileBody = utf8.encode(
        'weave live e2e ${DateTime.now().toUtc().toIso8601String()}',
      );
      final filesRepository = container.read(filesRepositoryProvider);
      await filesRepository.uploadFile(
        '/',
        FileUploadRequest(
          fileName: seededFileName,
          sizeInBytes: seededFileBody.length,
          byteStream: Stream<List<int>>.value(seededFileBody),
        ),
      );

      await container.read(filesProvider.notifier).refresh();
      await _waitFor(
        tester,
        () {
          final state = container.read(filesProvider);
          if (!state.hasValue) {
            return false;
          }
          final listing = state.requireValue.directoryListing;
          return listing != null &&
              listing.entries.any((entry) => entry.name == seededFileName);
        },
        reason:
            'Files view should show the file uploaded through the backend files facade.',
        timeout: const Duration(minutes: 1),
      );

      final refreshedFilesState = container.read(filesProvider).requireValue;
      final matchedFiles = refreshedFilesState.directoryListing!.entries
          .where((entry) => entry.name == seededFileName)
          .toList(growable: false);
      final uploadedFile = matchedFiles.firstOrNull;
      final fileDownload = uploadedFile == null
          ? null
          : await (filesRepository as FilesExportRepository).downloadFile(
              uploadedFile,
            );
      final fileDownloadMatched =
          fileDownload?.fileName == seededFileName &&
          fileDownload != null &&
          utf8.decode(fileDownload.bytes) == utf8.decode(seededFileBody);
      if (uploadedFile != null) {
        await (filesRepository as FilesEntryMutationRepository).deleteEntry(
          uploadedFile,
        );
      }
      // ignore: avoid_print
      print(
        'FILES_RESULT path=${refreshedFilesState.directoryListing!.path} '
        'entries=${refreshedFilesState.directoryListing!.entries.length} '
        'matchedFiles=${matchedFiles.length} '
        'downloadMatched=$fileDownloadMatched '
        'fileName=$seededFileName',
      );

      final workspaceLoopFileRef = 'file:$seededFileName';
      final workspaceLoopChatMessage = _decodeHttpJson(
        await providerHttpClient.post(
          config.apiUri('/api/chat/conversations/channel-general/messages'),
          headers: <String, String>{
            'Accept': 'application/json',
            'Authorization': 'Bearer ${appSession.accessToken}',
            'Content-Type': 'application/json',
          },
          body: jsonEncode(<String, Object>{
            'text':
                'Workspace loop evidence: file reference is ready for board, calendar, and decision follow-up.',
            'attachmentRefs': <String>[workspaceLoopFileRef],
          }),
        ),
        operation: 'send workspace loop chat message',
      );
      final workspaceLoopChatMessageId = _jsonString(
        workspaceLoopChatMessage['id'],
      );
      final workspaceLoopConversationId = _jsonString(
        workspaceLoopChatMessage['conversationId'],
      );
      final workspaceLoopChatUsesCanonicalIds =
          workspaceLoopConversationId == 'channel-general' &&
          workspaceLoopChatMessageId.startsWith('msg-') &&
          _jsonList(
            workspaceLoopChatMessage['attachmentRefs'],
          ).contains(workspaceLoopFileRef);

      final calendarRepository = container.read(calendarRepositoryProvider);
      final calendarScopes = await calendarRepository.loadScopes();
      final workspaceScopes = calendarScopes.scopes
          .where((scope) => scope.isWorkspace)
          .toList(growable: false);
      final teamScopes = calendarScopes.scopes
          .where((scope) => scope.isTeam && scope.teamId != null)
          .toList(growable: false);
      final channelScopes = calendarScopes.scopes
          .where(
            (scope) =>
                scope.isChannel &&
                scope.teamId != null &&
                scope.channelId != null,
          )
          .toList(growable: false);
      final calendarScopesReady =
          workspaceScopes.isNotEmpty &&
          teamScopes.isNotEmpty &&
          channelScopes.isNotEmpty;
      final channelScope = channelScopes.isNotEmpty
          ? channelScopes.first
          : CalendarScope.workspace;
      final calendarReadSnapshot = await calendarRepository.loadEvents(
        scope: channelScope,
      );
      final calendarReadReady =
          calendarReadSnapshot.scope.type == channelScope.type &&
          calendarReadSnapshot.scope.teamId == channelScope.teamId &&
          calendarReadSnapshot.scope.channelId == channelScope.channelId;

      final calendarTitle = 'Weave live E2E $liveE2eSuffix';
      final calendarStart = DateTime.now().toUtc().add(const Duration(days: 1));
      var calendarEventId = 'policy-blocked';
      var calendarManageEventsAllowed = false;
      var calendarWritePolicyBlocked = false;
      var calendarCreatedAndRead = false;
      var calendarUpdatedAndRead = false;
      var calendarCreatedThreadRefReady = false;
      var calendarUpdatedThreadRefReady = false;
      var calendarMeetingThreadStable = false;
      var calendarDeleted = false;
      String? calendarMeetingThreadId;
      CalendarEvent? createdEventForCleanup;

      try {
        final calendarDraft = CalendarEventDraft(
          title: calendarTitle,
          description:
              'Created by the live-stack shared channel calendar E2E gate.',
          startTime: calendarStart,
          endTime: calendarStart.add(const Duration(minutes: 30)),
          timezone: 'UTC',
          scope: channelScope,
        );
        final createdEvent = await _createCalendarEventWithReadAfterWrite(
          tester,
          calendarRepository,
          calendarDraft,
        );
        createdEventForCleanup = createdEvent;
        calendarEventId = createdEvent.id;
        calendarManageEventsAllowed = true;
        final loadedCalendar = await _waitForCalendarEventInScope(
          tester,
          calendarRepository,
          scope: channelScope,
          eventId: createdEvent.id,
          title: calendarTitle,
        );
        final readCreatedEvent = await calendarRepository.readEvent(
          createdEvent.id,
        );
        calendarCreatedThreadRefReady = _channelMeetingThreadReady(
          readCreatedEvent,
          channelScope,
        );
        calendarCreatedAndRead =
            loadedCalendar.scope.isChannel &&
            loadedCalendar.scope.teamId == channelScope.teamId &&
            loadedCalendar.scope.channelId == channelScope.channelId &&
            loadedCalendar.events.any(
              (event) =>
                  event.id == createdEvent.id &&
                  event.title == calendarTitle &&
                  event.scope.isChannel,
            ) &&
            readCreatedEvent.id == createdEvent.id &&
            readCreatedEvent.title == calendarTitle &&
            readCreatedEvent.scope.isChannel;
        final updatedCalendarTitle = '$calendarTitle updated';
        final updatedEvent = await calendarRepository.updateEvent(
          createdEvent.id,
          CalendarEventDraft(
            title: updatedCalendarTitle,
            description:
                'Updated by the live-stack channel Calendar CRUD E2E gate.',
            startTime: calendarStart.add(const Duration(hours: 1)),
            endTime: calendarStart.add(const Duration(hours: 1, minutes: 45)),
            timezone: 'UTC',
            scope: channelScope,
          ),
        );
        final readUpdatedEvent = await calendarRepository.readEvent(
          createdEvent.id,
        );
        calendarUpdatedThreadRefReady = _channelMeetingThreadReady(
          readUpdatedEvent,
          channelScope,
        );
        calendarMeetingThreadStable =
            readCreatedEvent.threadRef.meetingThreadId != null &&
            readCreatedEvent.threadRef.meetingThreadId ==
                readUpdatedEvent.threadRef.meetingThreadId;
        calendarMeetingThreadId = readCreatedEvent.threadRef.meetingThreadId;
        calendarUpdatedAndRead =
            updatedEvent.id == createdEvent.id &&
            updatedEvent.title == updatedCalendarTitle &&
            updatedEvent.scope.isChannel &&
            readUpdatedEvent.id == createdEvent.id &&
            readUpdatedEvent.title == updatedCalendarTitle &&
            readUpdatedEvent.scope.isChannel;
        await calendarRepository.deleteEvent(createdEvent.id);
        createdEventForCleanup = null;
        final calendarAfterDelete = await _waitForCalendarEventDeleted(
          tester,
          calendarRepository,
          scope: channelScope,
          eventId: createdEvent.id,
        );
        calendarDeleted = calendarAfterDelete.events.every(
          (event) => event.id != createdEvent.id,
        );
      } on AppFailure catch (error) {
        if (!_isCapabilityPolicyBlockedFailure(error)) {
          rethrow;
        }
        calendarWritePolicyBlocked = true;
      } finally {
        final event = createdEventForCleanup;
        if (event != null) {
          try {
            await calendarRepository.deleteEvent(event.id);
          } catch (_) {
            // The main calendar assertion below carries the product evidence;
            // cleanup should not hide the original live-stack signal.
          }
        }
      }

      // ignore: avoid_print
      print(
        'CALENDAR_RESULT eventId=$calendarEventId '
        'scopes=${calendarScopes.scopes.map((scope) => scope.type).join(',')} '
        'workspaceScopes=${workspaceScopes.length} '
        'teamScopes=${teamScopes.length} '
        'channelScopes=${channelScopes.length} '
        'scopesReady=$calendarScopesReady '
        'readReady=$calendarReadReady '
        'readEvents=${calendarReadSnapshot.events.length} '
        'scope=${calendarReadSnapshot.scope.type} '
        'teamId=${calendarReadSnapshot.scope.teamId} '
        'channelId=${calendarReadSnapshot.scope.channelId} '
        'manageEventsAllowed=$calendarManageEventsAllowed '
        'writePolicyBlocked=$calendarWritePolicyBlocked '
        'createdAndRead=$calendarCreatedAndRead '
        'updatedAndRead=$calendarUpdatedAndRead '
        'createdThreadRefReady=$calendarCreatedThreadRefReady '
        'updatedThreadRefReady=$calendarUpdatedThreadRefReady '
        'meetingThreadStable=$calendarMeetingThreadStable '
        'meetingThreadId=${calendarMeetingThreadId ?? 'none'} '
        'deleted=$calendarDeleted',
      );

      final calendarWritePathValid = calendarManageEventsAllowed
          ? calendarCreatedAndRead &&
                calendarUpdatedAndRead &&
                calendarCreatedThreadRefReady &&
                calendarUpdatedThreadRefReady &&
                calendarMeetingThreadStable &&
                calendarDeleted
          : calendarWritePolicyBlocked;

      final liveHttpClient = createTrustedTestHttpClient();
      addTearDown(liveHttpClient.close);
      final boardsWorkspaceResponse = await liveHttpClient.get(
        config.apiUri('/api/boards/workspace'),
        headers: <String, String>{
          'Accept': 'application/json',
          'Authorization': 'Bearer ${appSession.accessToken}',
        },
      );
      final boardsWorkspace = _decodeHttpJson(
        boardsWorkspaceResponse,
        operation: 'read boards workspace',
      );
      final boards = _jsonListOfMaps(boardsWorkspace['boards']);
      final tasksBefore = _jsonListOfMaps(boardsWorkspace['tasks']);
      final board = boards.isNotEmpty ? boards.first : <String, dynamic>{};
      final boardId = _jsonString(board['id']);
      final columns = _jsonListOfMaps(board['columns']);
      final todoColumn = columns.firstWhere(
        (column) => column['semanticStatus'] == 'not_started',
        orElse: () => columns.isNotEmpty ? columns.first : <String, dynamic>{},
      );
      final activeColumn = columns.firstWhere(
        (column) => column['semanticStatus'] == 'in_progress',
        orElse: () => columns.isNotEmpty ? columns.first : <String, dynamic>{},
      );
      final doneColumn = columns.firstWhere(
        (column) => column['semanticStatus'] == 'done',
        orElse: () => columns.isNotEmpty ? columns.last : <String, dynamic>{},
      );
      final createdBoardTaskResponse = await liveHttpClient.post(
        config.apiUri('/api/boards/$boardId/tasks'),
        headers: <String, String>{
          'Accept': 'application/json',
          'Authorization': 'Bearer ${appSession.accessToken}',
          'Content-Type': 'application/json',
        },
        body: jsonEncode(<String, Object>{
          'columnId': _jsonString(todoColumn['id']),
          'title': 'Live E2E non-drag task $liveE2eSuffix',
          'description':
              'Created through the provider-neutral backend Boards facade.',
          'assigneeRefs': <String>['workspace:member'],
          'labelRefs': <String>['e2e', 'a11y'],
        }),
      );
      final createdBoardTask = _decodeHttpJson(
        createdBoardTaskResponse,
        operation: 'create boards task',
      );
      final taskId = _jsonString(createdBoardTask['id']);
      final movedBoardTask = _decodeHttpJson(
        await liveHttpClient.post(
          config.apiUri('/api/boards/tasks/$taskId/move'),
          headers: <String, String>{
            'Accept': 'application/json',
            'Authorization': 'Bearer ${appSession.accessToken}',
            'Content-Type': 'application/json',
          },
          body: jsonEncode(<String, Object>{
            'targetColumnId': _jsonString(activeColumn['id']),
            'targetPosition': 0,
          }),
        ),
        operation: 'move boards task',
      );
      final completedBoardTask = _decodeHttpJson(
        await liveHttpClient.post(
          config.apiUri('/api/boards/tasks/$taskId/complete'),
          headers: <String, String>{
            'Accept': 'application/json',
            'Authorization': 'Bearer ${appSession.accessToken}',
          },
        ),
        operation: 'complete boards task',
      );
      final boardsWorkspaceAfterMutation = _decodeHttpJson(
        await liveHttpClient.get(
          config.apiUri('/api/boards/workspace'),
          headers: <String, String>{
            'Accept': 'application/json',
            'Authorization': 'Bearer ${appSession.accessToken}',
          },
        ),
        operation: 'read boards workspace after mutation',
      );
      final boardsCapabilities = _jsonMap(boardsWorkspace['capabilities']);
      final boardsTasksAfter = _jsonListOfMaps(
        boardsWorkspaceAfterMutation['tasks'],
      );
      final boardsProviderNeutral =
          boardsWorkspace['workspace'] == true &&
          boardsWorkspace['releaseStatus'] == 'active-dogfood-production' &&
          boardsWorkspace['source'] == 'local-workspace-backend-facade' &&
          _jsonString(boardsCapabilities['provider']) == 'in-memory';
      final boardsNonDragMutationWorked =
          boardId == 'local-board-1' &&
          _jsonString(movedBoardTask['columnId']) ==
              _jsonString(activeColumn['id']) &&
          _jsonString(completedBoardTask['columnId']) ==
              _jsonString(doneColumn['id']) &&
          _jsonString(completedBoardTask['status']) == 'completed' &&
          boardsTasksAfter.any((task) => task['id'] == taskId);
      // ignore: avoid_print
      print(
        'BOARDS_RESULT boardId=$boardId taskId=$taskId '
        'provider=${boardsCapabilities['provider']} '
        'releaseStatus=${boardsWorkspace['releaseStatus']} '
        'workspace=${boardsWorkspace['workspace']} '
        'columns=${columns.length} '
        'tasksBefore=${tasksBefore.length} '
        'tasksAfter=${boardsTasksAfter.length} '
        'createdColumn=${createdBoardTask['columnId']} '
        'movedColumn=${movedBoardTask['columnId']} '
        'completedColumn=${completedBoardTask['columnId']} '
        'completedStatus=${completedBoardTask['status']} '
        'nonDragMutationWorked=$boardsNonDragMutationWorked',
      );

      final workspaceLoopCalendarRef = calendarManageEventsAllowed
          ? 'calendar:$calendarEventId'
          : 'calendar:policy-blocked';
      final createdDecision = _decodeHttpJson(
        await liveHttpClient.post(
          config.apiUri('/api/chat/conversations/channel-general/decisions'),
          headers: <String, String>{
            'Accept': 'application/json',
            'Authorization': 'Bearer ${appSession.accessToken}',
            'Content-Type': 'application/json',
          },
          body: jsonEncode(<String, Object>{
            'title': 'Accept workspace loop evidence for Sprint 18',
            'status': 'accepted',
            'risks': <String>[
              'Live provider writes can be unavailable; blocked writes stay explicit.',
            ],
            'openQuestions': <String>[
              calendarManageEventsAllowed
                  ? 'None for the evidenced calendar path.'
                  : 'Calendar write remains blocked by capability policy.',
            ],
            'followUpRefs': <String>[
              workspaceLoopCalendarRef,
              'evidence:workspace-loop-live-stack',
            ],
            'references': <Map<String, String>>[
              <String, String>{
                'type': 'chat-message',
                'ref': 'message:$workspaceLoopChatMessageId',
                'label': 'Workspace loop chat context',
                'excerpt':
                    'File reference is ready for board, calendar, and decision follow-up.',
              },
              <String, String>{
                'type': 'file',
                'ref': workspaceLoopFileRef,
                'label': 'Uploaded workspace loop file',
                'excerpt': seededFileName,
              },
              <String, String>{
                'type': 'task',
                'ref': 'task:$taskId',
                'label': 'Completed non-drag board task',
                'excerpt':
                    'Board task reached completed status through the backend facade.',
              },
            ],
          }),
        ),
        operation: 'create workspace loop decision',
      );
      final decisionsSnapshot = _decodeHttpJson(
        await liveHttpClient.get(
          config.apiUri('/api/chat/conversations/channel-general/decisions'),
          headers: <String, String>{
            'Accept': 'application/json',
            'Authorization': 'Bearer ${appSession.accessToken}',
          },
        ),
        operation: 'read workspace loop decisions',
      );
      final workspaceLoopDecisionId = _jsonString(createdDecision['id']);
      final workspaceLoopDecisionContextId = _jsonString(
        createdDecision['contextId'],
      );
      final workspaceLoopDecisionRefs = _jsonListOfMaps(
        createdDecision['references'],
      );
      final workspaceLoopDecisionStored = _jsonListOfMaps(
        decisionsSnapshot['records'],
      ).any((record) => record['id'] == workspaceLoopDecisionId);
      final workspaceLoopSupportSafe =
          createdDecision['supportSafe'] == true &&
          _supportSafeEvidenceValue(<String, Object?>{
            'contextId': workspaceLoopDecisionContextId,
            'conversationId': workspaceLoopConversationId,
            'chatMessageId': workspaceLoopChatMessageId,
            'fileRef': workspaceLoopFileRef,
            'taskId': taskId,
            'calendarRef': workspaceLoopCalendarRef,
            'decisionId': workspaceLoopDecisionId,
          });
      final workspaceLoopComplete =
          workspaceLoopDecisionContextId == 'workspace-default' &&
          workspaceLoopConversationId == 'channel-general' &&
          workspaceLoopChatUsesCanonicalIds &&
          matchedFiles.isNotEmpty &&
          boardsNonDragMutationWorked &&
          calendarWritePathValid &&
          workspaceLoopDecisionId.startsWith('decision-') &&
          workspaceLoopDecisionRefs.length == 3 &&
          workspaceLoopDecisionStored &&
          workspaceLoopSupportSafe;
      // ignore: avoid_print
      print(
        'WORKSPACE_LOOP_RESULT contextId=$workspaceLoopDecisionContextId '
        'spaceId=$workspaceLoopDecisionContextId '
        'conversationId=$workspaceLoopConversationId '
        'chatMessageId=$workspaceLoopChatMessageId '
        'fileRef=$workspaceLoopFileRef '
        'boardTaskId=$taskId '
        'calendarRef=$workspaceLoopCalendarRef '
        'calendarWrite=${calendarManageEventsAllowed ? 'real-write' : 'blocked-by-capability-policy'} '
        'decisionId=$workspaceLoopDecisionId '
        'realWrites=chat,file,board,decision '
        'canonicalIds=$workspaceLoopChatUsesCanonicalIds '
        'decisionStored=$workspaceLoopDecisionStored '
        'supportSafe=$workspaceLoopSupportSafe '
        'complete=$workspaceLoopComplete',
      );

      final organizationManifest = _decodeHttpJson(
        await liveHttpClient.get(
          config.apiUri('/api/v1/organization/manifest'),
          headers: <String, String>{
            'Accept': 'application/json',
            'Authorization': 'Bearer ${appSession.accessToken}',
          },
        ),
        operation: 'read member organization manifest provider reality',
      );
      final workspaceCapabilities = _jsonMap(
        organizationManifest['capabilities'],
      );
      final memberCapabilityStates = _jsonMap(
        organizationManifest['memberCapabilityStates'],
      );
      final filesCapability = _jsonMap(workspaceCapabilities['files']);
      final calendarCapability = _jsonMap(workspaceCapabilities['calendar']);
      final boardsCapability = _jsonMap(workspaceCapabilities['boards']);
      final callsCapability = _jsonMap(workspaceCapabilities['meetingsCalls']);
      final documentsCapability = _jsonMap(
        workspaceCapabilities['documentsCollaboration'],
      );
      final capabilityRealitySupportSafe =
          organizationManifest['supportSafe'] == true &&
          organizationManifest['providerConfigurationExposed'] != true &&
          organizationManifest['diagnosticsExposed'] != true &&
          _capabilityStateSupportSafe(filesCapability) &&
          _capabilityStateSupportSafe(calendarCapability) &&
          _capabilityStateSupportSafe(boardsCapability) &&
          _capabilityStateSupportSafe(callsCapability) &&
          _capabilityStateSupportSafe(documentsCapability);
      final providerRealityStatesHonest = _memberCapabilityStatesAreHonest(
        memberCapabilityStates,
      );
      final providerRealityBacksLivePaths =
          filesFacadeConnected &&
          calendarReadReady &&
          calendarWritePathValid &&
          boardsProviderNeutral &&
          boardsNonDragMutationWorked;
      final callsAvailableOrHonestUnavailable = _availableOrHonestFallback(
        callsCapability,
      );
      final documentsAvailableOrHonestUnavailable = _availableOrHonestFallback(
        documentsCapability,
      );
      // ignore: avoid_print
      print(
        'PROVIDER_REALITY_RESULT '
        'files=${_capabilityEvidence(filesCapability)} '
        'calendar=${_capabilityEvidence(calendarCapability)} '
        'boards=${_capabilityEvidence(boardsCapability)} '
        'calls=${_capabilityEvidence(callsCapability)} '
        'documents=${_capabilityEvidence(documentsCapability)} '
        'memberStates=${_memberCapabilityEvidence(memberCapabilityStates)} '
        'livePathsBacked=$providerRealityBacksLivePaths '
        'callsAvailableOrHonestUnavailable=$callsAvailableOrHonestUnavailable '
        'documentsAvailableOrHonestUnavailable=$documentsAvailableOrHonestUnavailable '
        'supportSafe=$capabilityRealitySupportSafe '
        'honestStates=$providerRealityStatesHonest',
      );

      if (!matrixConnected ||
          !e2eeCryptoAvailable ||
          !e2eeSecurityPostureHonest ||
          !e2eeRoomEncrypted ||
          !e2eeEncryptedEventObserved ||
          !profileUpdated ||
          !filesFacadeConnected ||
          deliveredMessage.isEmpty ||
          matchedFiles.isEmpty ||
          !fileDownloadMatched ||
          !calendarScopesReady ||
          !calendarReadReady ||
          !calendarWritePathValid ||
          !providerRegistryMemberForbidden ||
          !providerRegistryBodySupportSafe ||
          !profileReadinessOk ||
          !boardsProviderNeutral ||
          !boardsNonDragMutationWorked ||
          !workspaceLoopComplete ||
          !providerRealityBacksLivePaths ||
          !callsAvailableOrHonestUnavailable ||
          !documentsAvailableOrHonestUnavailable ||
          !capabilityRealitySupportSafe ||
          !providerRealityStatesHonest) {
        fail(
          'live_e2e_result '
          'authSignedIn=true '
          'profileLoaded=true '
          'profileUpdated=$profileUpdated '
          'matrixConnected=$matrixConnected '
          'matrixSource=live-matrix-harness '
          'chatRoomId=$roomId '
          'chatMatchedMessages=${deliveredMessage.length} '
          'e2eeCryptoAvailable=$e2eeCryptoAvailable '
          'e2eeSecurityReady=$e2eeSecurityReady '
          'e2eeSecurityPostureHonest=$e2eeSecurityPostureHonest '
          'e2eeBootstrapState=${e2eeSecurityState.bootstrapState} '
          'e2eeRoomEncrypted=$e2eeRoomEncrypted '
          'e2eeEncryptedWireEvents=${encryptedWireProof.newEncryptedEvents.length} '
          'e2eeEncryptedEvents=${decryptedEncryptedMessages.length} '
          'e2eeEncryptedWirePlaintextLeaked=${encryptedWireProof.plaintextLeaked} '
          'filesFacadeConnected=$filesFacadeConnected '
          'filesFacadeStatus=${filesState.connectionState.status} '
          'filesFacadeMessage=${filesState.connectionState.message} '
          'filesFacadeEntries=${refreshedFilesState.directoryListing?.entries.length} '
          'filesFacadeMatchedFiles=${matchedFiles.length} '
          'filesDownloadMatched=$fileDownloadMatched '
          'seededFileName=$seededFileName '
          'calendarScopesReady=$calendarScopesReady '
          'calendarReadReady=$calendarReadReady '
          'calendarScope=${calendarReadSnapshot.scope.type} '
          'calendarTeamId=${calendarReadSnapshot.scope.teamId} '
          'calendarChannelId=${calendarReadSnapshot.scope.channelId} '
          'calendarManageEventsAllowed=$calendarManageEventsAllowed '
          'calendarWritePolicyBlocked=$calendarWritePolicyBlocked '
          'calendarWritePathValid=$calendarWritePathValid '
          'calendarCreatedAndRead=$calendarCreatedAndRead '
          'calendarUpdatedAndRead=$calendarUpdatedAndRead '
          'calendarCreatedThreadRefReady=$calendarCreatedThreadRefReady '
          'calendarUpdatedThreadRefReady=$calendarUpdatedThreadRefReady '
          'calendarMeetingThreadStable=$calendarMeetingThreadStable '
          'calendarMeetingThreadId=${calendarMeetingThreadId ?? 'none'} '
          'calendarDeleted=$calendarDeleted '
          'calendarEventId=$calendarEventId '
          'memberRegistryForbidden=$providerRegistryMemberForbidden '
          'memberRegistryStatus=${providerRegistryResponse.statusCode} '
          'registryBodySupportSafe=$providerRegistryBodySupportSafe '
          'profileReadinessOk=$profileReadinessOk '
          'boardsProviderNeutral=$boardsProviderNeutral '
          'boardsNonDragMutationWorked=$boardsNonDragMutationWorked '
          'boardsTaskId=$taskId '
          'workspaceLoopComplete=$workspaceLoopComplete '
          'workspaceLoopDecisionId=$workspaceLoopDecisionId '
          'workspaceLoopSupportSafe=$workspaceLoopSupportSafe '
          'providerRealityBacksLivePaths=$providerRealityBacksLivePaths '
          'callsAvailableOrHonestUnavailable=$callsAvailableOrHonestUnavailable '
          'documentsAvailableOrHonestUnavailable=$documentsAvailableOrHonestUnavailable '
          'capabilityRealitySupportSafe=$capabilityRealitySupportSafe '
          'providerRealityStatesHonest=$providerRealityStatesHonest',
        );
      }

      _resetKeyboardTestState();
      expect(profileUpdated, isTrue);
      expect(matrixConnected, isTrue);
      expect(deliveredMessage, isNotEmpty);
      expect(e2eeCryptoAvailable, isTrue);
      expect(e2eeSecurityPostureHonest, isTrue);
      expect(e2eeRoomEncrypted, isTrue);
      expect(e2eeEncryptedEventObserved, isTrue);
      expect(decryptedEncryptedMessages, isNotEmpty);
      expect(filesFacadeConnected, isTrue);
      expect(matchedFiles, isNotEmpty);
      expect(fileDownloadMatched, isTrue);
      expect(calendarScopesReady, isTrue);
      expect(calendarReadReady, isTrue);
      expect(calendarWritePathValid, isTrue);
      if (calendarManageEventsAllowed) {
        expect(calendarCreatedAndRead, isTrue);
        expect(calendarUpdatedAndRead, isTrue);
        expect(calendarCreatedThreadRefReady, isTrue);
        expect(calendarUpdatedThreadRefReady, isTrue);
        expect(calendarMeetingThreadStable, isTrue);
        expect(calendarDeleted, isTrue);
      } else {
        expect(calendarWritePolicyBlocked, isTrue);
      }
      expect(providerRegistryMemberForbidden, isTrue);
      expect(providerRegistryBodySupportSafe, isTrue);
      expect(profileReadinessOk, isTrue);
      expect(boardsProviderNeutral, isTrue);
      expect(boardsNonDragMutationWorked, isTrue);
      expect(workspaceLoopComplete, isTrue);
      expect(workspaceLoopSupportSafe, isTrue);
      expect(providerRealityBacksLivePaths, isTrue);
      expect(callsAvailableOrHonestUnavailable, isTrue);
      expect(documentsAvailableOrHonestUnavailable, isTrue);
      expect(capabilityRealitySupportSafe, isTrue);
      expect(providerRealityStatesHonest, isTrue);
    },
    semanticsEnabled: false,
  );
}

bool _channelMeetingThreadReady(CalendarEvent event, CalendarScope scope) {
  final meetingThreadId = event.threadRef.meetingThreadId;
  return event.scope.isChannel &&
      event.scope.teamId == scope.teamId &&
      event.scope.channelId == scope.channelId &&
      event.threadRef.contextId == scope.contextId &&
      event.threadRef.channelId == scope.channelId &&
      meetingThreadId != null &&
      meetingThreadId.startsWith('meeting:') &&
      meetingThreadId.contains(scope.contextId);
}

Future<void> _pumpUntilSettled(WidgetTester tester) async {
  for (var i = 0; i < 20; i++) {
    _resetKeyboardTestState();
    await tester.pump(const Duration(milliseconds: 200));
  }
}

bool _isStrayKeyboardKeyUpAssertion(Object error, StackTrace stack) {
  final message = error.toString();
  final trace = stack.toString();
  return error is AssertionError &&
      message.contains('A KeyUpEvent is dispatched') &&
      message.contains('_pressedKeys.containsKey(event.physicalKey)') &&
      trace.contains('HardwareKeyboard.handleKeyEvent');
}

void _resetKeyboardTestState() {
  // The self-hosted macOS runner can deliver a stray synthesized key-up after a
  // long live-stack run or while the Flutter macOS test app is closing. Keep
  // Flutter's debug keyboard state hermetic so an unrelated host key event
  // cannot fail the product smoke assertions.
  // ignore: invalid_use_of_visible_for_testing_member, deprecated_member_use
  RawKeyboard.instance.clearKeysPressed();
  // ignore: invalid_use_of_visible_for_testing_member
  HardwareKeyboard.instance.clearState();
  // ignore: invalid_use_of_visible_for_testing_member, deprecated_member_use
  ServicesBinding.instance.keyEventManager.clearState();
}

Map<String, dynamic> _decodeHttpJson(
  http.Response response, {
  required String operation,
}) {
  if (response.statusCode < 200 || response.statusCode >= 300) {
    fail(
      'Live stack failed to $operation: '
      'status=${response.statusCode} body=${response.body}',
    );
  }
  final decoded = jsonDecode(response.body);
  if (decoded is! Map<String, dynamic>) {
    fail('Live stack returned non-object JSON while trying to $operation.');
  }
  return decoded;
}

Map<String, dynamic> _jsonMap(Object? value) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  if (value is Map) {
    return value.cast<String, dynamic>();
  }
  return <String, dynamic>{};
}

List<Map<String, dynamic>> _jsonListOfMaps(Object? value) {
  if (value is! List) {
    return const <Map<String, dynamic>>[];
  }
  return value
      .whereType<Map>()
      .map((item) => item.cast<String, dynamic>())
      .toList(growable: false);
}

String _jsonString(Object? value) => value is String ? value : '';

List<Object?> _jsonList(Object? value) =>
    value is List ? value : const <Object?>[];

bool _supportSafeEvidenceValue(Object? value) {
  final encoded = jsonEncode(value);
  return !RegExp(
    r'(Authorization|Bearer|token|secret|password|https?://|/api/v3/|/work_packages/|/projects/|SecretRef|secretref://)',
    caseSensitive: false,
  ).hasMatch(encoded);
}

String _capabilityEvidence(Map<String, dynamic> capability) {
  final enabled = capability['enabled'] == true;
  final readiness = _jsonString(capability['readiness']);
  final policyState = _jsonString(capability['policyState']);
  final impactPresent = _jsonString(capability['memberImpact']).isNotEmpty;
  return 'enabled=$enabled,readiness=$readiness,policy=$policyState,impact=$impactPresent';
}

bool _capabilityStateSupportSafe(Map<String, dynamic> capability) {
  final allowedKeys = <String>{
    'enabled',
    'readiness',
    'policyState',
    'profileKey',
    'memberImpact',
    'grantedCapabilities',
  };
  if (capability.isEmpty ||
      capability.keys.any((key) => !allowedKeys.contains(key))) {
    return false;
  }
  final impact = _jsonString(capability['memberImpact']);
  return !RegExp(
    r'(Authorization|Bearer|token|secret|password|https?://|/api/v3/|SecretRef|secretref://)',
    caseSensitive: false,
  ).hasMatch(impact);
}

bool _availableOrHonestFallback(Map<String, dynamic> capability) {
  final enabled = capability['enabled'] == true;
  final readiness = _jsonString(capability['readiness']);
  final policyState = _jsonString(capability['policyState']);
  final impactPresent = _jsonString(capability['memberImpact']).isNotEmpty;
  if (enabled && readiness == 'ready' && policyState == 'allowed') {
    return impactPresent;
  }
  if (impactPresent &&
      readiness == 'degraded' &&
      <String>{'allowed', 'unavailable'}.contains(policyState)) {
    return true;
  }
  return impactPresent &&
      <String>{'blocked', 'unavailable'}.contains(readiness) &&
      <String>{
        'policyBlocked',
        'policy_blocked',
        'disabled',
        'unavailable',
      }.contains(policyState);
}

const Set<String> _requiredMemberCapabilityStateKeys = <String>{
  'idm-rbac',
  'chat-channels',
  'files-docs',
  'calendar-events',
  'boards-tasks',
  'meetings',
  'forms-contacts',
};

const Set<String> _honestMemberCapabilityStates = <String>{
  'available',
  'disabled_by_policy',
  'not_configured',
  'degraded',
  'unavailable',
  'coming_later',
};

bool _memberCapabilityStatesAreHonest(Map<String, dynamic> states) {
  if (!states.keys.toSet().containsAll(_requiredMemberCapabilityStateKeys)) {
    return false;
  }
  for (final key in _requiredMemberCapabilityStateKeys) {
    final state = _jsonString(states[key]);
    if (!_honestMemberCapabilityStates.contains(state)) {
      return false;
    }
  }
  return true;
}

String _memberCapabilityEvidence(Map<String, dynamic> states) {
  return _requiredMemberCapabilityStateKeys
      .map((key) => '$key=${_jsonString(states[key])}')
      .join(',');
}

String _supportSafeDiagnostic(Object? error) {
  if (error == null) {
    return 'none';
  }
  return error
      .toString()
      .replaceAll(
        RegExp(r'Bearer\s+[^\s,]+', caseSensitive: false),
        'Bearer ***',
      )
      .replaceAllMapped(
        RegExp(
          r'(access[_-]?token|refresh[_-]?token|id[_-]?token|password|secret)=([^\s,]+)',
          caseSensitive: false,
        ),
        (match) => '${match.group(1)}=***',
      )
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();
}

class _EncryptedWireProof {
  const _EncryptedWireProof({
    required this.newEncryptedEvents,
    required this.plaintextLeaked,
  });

  final List<sdk.MatrixEvent> newEncryptedEvents;
  final bool plaintextLeaked;
}

Future<sdk.Room> _waitForEncryptedMatrixRoom(
  WidgetTester tester,
  sdk.Client client,
  String roomId,
) async {
  final end = DateTime.now().add(const Duration(seconds: 45));
  Object? lastError;
  while (DateTime.now().isBefore(end)) {
    try {
      await client.oneShotSync(timeout: const Duration(seconds: 5));
      final room = client.getRoomById(roomId);
      if (room != null && room.encrypted) {
        return room;
      }
    } catch (error) {
      lastError = error;
    }
    _resetKeyboardTestState();
    await tester.pump(const Duration(milliseconds: 500));
  }

  final room = client.getRoomById(roomId);
  fail(
    'matrix_encrypted_room_not_ready roomId=$roomId '
    'roomFound=${room != null} roomEncrypted=${room?.encrypted} '
    'lastError=$lastError',
  );
}

Future<List<sdk.MatrixEvent>> _loadAuthoritativeWireEvents(
  sdk.Client client,
  String roomId,
) async {
  final response = await client.getRoomEvents(
    roomId,
    sdk.Direction.b,
    limit: 50,
    filter: jsonEncode(<String, Object>{
      'types': <String>[sdk.EventTypes.Encrypted],
    }),
  );
  return response.chunk
      .where(
        (event) =>
            event.type == sdk.EventTypes.Encrypted &&
            _hasEncryptedMegolmPayload(event),
      )
      .toList(growable: false);
}

Future<_EncryptedWireProof> _waitForAuthoritativeEncryptedWireEvent(
  WidgetTester tester,
  sdk.Client client,
  String roomId, {
  required Set<String> previousEventIds,
  required String plaintext,
}) async {
  final end = DateTime.now().add(const Duration(minutes: 2));
  Object? lastError;
  var observedEncryptedEvents = const <sdk.MatrixEvent>[];
  while (DateTime.now().isBefore(end)) {
    try {
      await client.oneShotSync(timeout: const Duration(seconds: 5));
      final encryptedEvents = await _loadAuthoritativeWireEvents(
        client,
        roomId,
      );
      observedEncryptedEvents = encryptedEvents;
      final newEncryptedEvents = encryptedEvents
          .where((event) => !previousEventIds.contains(event.eventId))
          .toList(growable: false);
      if (newEncryptedEvents.isNotEmpty) {
        final plaintextLeaked = newEncryptedEvents.any(
          (event) => jsonEncode(event.toJson()).contains(plaintext),
        );
        return _EncryptedWireProof(
          newEncryptedEvents: newEncryptedEvents,
          plaintextLeaked: plaintextLeaked,
        );
      }
    } catch (error) {
      lastError = error;
    }
    _resetKeyboardTestState();
    await tester.pump(const Duration(milliseconds: 500));
  }

  fail(
    'matrix_authoritative_encrypted_wire_event_missing roomId=$roomId '
    'previousWireEvents=${previousEventIds.length} '
    'observedEncryptedWireEvents=${observedEncryptedEvents.length} '
    'observedEncryptedWireEventIds=${observedEncryptedEvents.map((event) => event.eventId).join(',')} '
    'lastError=$lastError',
  );
}

bool _hasEncryptedMegolmPayload(sdk.MatrixEvent event) {
  final algorithm = event.content['algorithm'];
  final ciphertext = event.content['ciphertext'];
  final sessionId = event.content['session_id'];
  final senderKey = event.content['sender_key'];
  return algorithm is String &&
      algorithm.trim().isNotEmpty &&
      ciphertext is String &&
      ciphertext.trim().isNotEmpty &&
      sessionId is String &&
      sessionId.trim().isNotEmpty &&
      senderKey is String &&
      senderKey.trim().isNotEmpty;
}

Future<ChatRoomTimeline> _waitForDecryptedEncryptedTimeline(
  WidgetTester tester,
  ChatRepository chatRepository,
  String roomId,
  String plaintext,
) async {
  final end = DateTime.now().add(const Duration(minutes: 2));
  Object? lastError;
  ChatRoomTimeline? latestTimeline;
  while (DateTime.now().isBefore(end)) {
    try {
      latestTimeline = await chatRepository.loadRoomTimeline(roomId);
      if (latestTimeline.messages.any((message) => message.text == plaintext)) {
        return latestTimeline;
      }
    } catch (error) {
      lastError = error;
    }
    _resetKeyboardTestState();
    await tester.pump(const Duration(milliseconds: 500));
  }

  fail(
    'matrix_decrypted_encrypted_timeline_message_missing roomId=$roomId '
    'timelineMessages=${latestTimeline?.messages.length} '
    'timelineTypes=${latestTimeline?.messages.map((message) => message.contentType).join(',')} '
    'lastError=$lastError',
  );
}

Future<CalendarEvent> _createCalendarEventWithReadAfterWrite(
  WidgetTester tester,
  CalendarRepository calendarRepository,
  CalendarEventDraft draft,
) async {
  final end = DateTime.now().add(const Duration(minutes: 2));
  Object? lastError;
  while (DateTime.now().isBefore(end)) {
    try {
      final createdEvent = await calendarRepository.createEvent(draft);
      final readEvent = await calendarRepository.readEvent(createdEvent.id);
      if (readEvent.id == createdEvent.id && readEvent.title == draft.title) {
        return createdEvent;
      }
      lastError = 'read_after_write_mismatch eventId=${createdEvent.id}';
    } catch (error) {
      lastError = error;
      if (!_isRetryableCalendarConsistencyError(error)) {
        rethrow;
      }
    }
    _resetKeyboardTestState();
    await tester.pump(const Duration(seconds: 2));
  }

  fail(
    'calendar_create_read_after_write_not_ready title=${draft.title} '
    'scope=${draft.scope.type} teamId=${draft.scope.teamId} '
    'channelId=${draft.scope.channelId} lastError=$lastError',
  );
}

Future<CalendarEventList> _waitForCalendarEventInScope(
  WidgetTester tester,
  CalendarRepository calendarRepository, {
  required CalendarScope scope,
  required String eventId,
  required String title,
}) async {
  final end = DateTime.now().add(const Duration(minutes: 1));
  Object? lastError;
  CalendarEventList? latestCalendar;
  while (DateTime.now().isBefore(end)) {
    try {
      latestCalendar = await calendarRepository.loadEvents(scope: scope);
      if (latestCalendar.events.any(
        (event) => event.id == eventId && event.title == title,
      )) {
        return latestCalendar;
      }
    } catch (error) {
      lastError = error;
    }
    _resetKeyboardTestState();
    await tester.pump(const Duration(seconds: 1));
  }

  fail(
    'calendar_created_event_not_listed eventId=$eventId title=$title '
    'scope=${scope.type} teamId=${scope.teamId} channelId=${scope.channelId} '
    'visibleEvents=${latestCalendar?.events.length} lastError=$lastError',
  );
}

Future<CalendarEventList> _waitForCalendarEventDeleted(
  WidgetTester tester,
  CalendarRepository calendarRepository, {
  required CalendarScope scope,
  required String eventId,
}) async {
  final end = DateTime.now().add(const Duration(minutes: 1));
  Object? lastError;
  CalendarEventList? latestCalendar;
  while (DateTime.now().isBefore(end)) {
    try {
      latestCalendar = await calendarRepository.loadEvents(scope: scope);
      if (latestCalendar.events.every((event) => event.id != eventId)) {
        return latestCalendar;
      }
    } catch (error) {
      lastError = error;
    }
    _resetKeyboardTestState();
    await tester.pump(const Duration(seconds: 1));
  }

  fail(
    'calendar_deleted_event_still_listed eventId=$eventId '
    'scope=${scope.type} teamId=${scope.teamId} channelId=${scope.channelId} '
    'visibleEvents=${latestCalendar?.events.length} lastError=$lastError',
  );
}

bool _isRetryableCalendarConsistencyError(Object error) {
  if (error is! AppFailure) {
    return false;
  }
  final message = error.message.toLowerCase();
  return message.contains('not found') ||
      message.contains('unavailable') ||
      message.contains('timed out') ||
      message.contains('timeout');
}

bool _isCapabilityPolicyBlockedFailure(AppFailure error) {
  final message = error.message.toLowerCase();
  return message.contains('blocked by workspace role') ||
      message.contains('capability-policy-blocked') ||
      message.contains('policy blocked') ||
      message.contains('policy-blocked');
}

Future<void> _waitFor(
  WidgetTester tester,
  bool Function() predicate, {
  required String reason,
  Duration timeout = const Duration(seconds: 30),
  String Function()? diagnostics,
}) async {
  final end = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(end)) {
    _resetKeyboardTestState();
    await tester.pump(const Duration(milliseconds: 250));
    if (predicate()) {
      return;
    }
  }
  final details = diagnostics?.call();
  fail(details == null ? reason : '$reason\n$details');
}

class _MemorySecureStore implements SecureStore {
  final Map<String, String> _values = <String, String>{};

  @override
  Future<void> delete(String key) async {
    _values.remove(key);
  }

  @override
  Future<String?> read(String key) async => _values[key];

  @override
  Future<void> write(String key, String value) async {
    _values[key] = value;
  }
}

class _MemoryServerConfigurationRepository
    implements ServerConfigurationRepository {
  _MemoryServerConfigurationRepository(this._configuration);

  ServerConfiguration? _configuration;

  @override
  Future<void> clearConfiguration() async {
    _configuration = null;
  }

  @override
  Future<ServerConfiguration?> loadConfiguration() async => _configuration;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    _configuration = configuration;
  }
}
