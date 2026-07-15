import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:crypto/crypto.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:weave/core/application_identity/presentation/providers/client_build_identity_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/l10n/app_locale_preference.dart';
import 'package:weave/core/router/app_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/presentation/calendar_screen.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/presentation/chat_screen.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/files_screen.dart';
import 'package:weave/features/home/presentation/home_screen.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/profile_screen.dart';
import 'package:weave/features/settings/presentation/settings_screen.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';
import 'package:weave/main.dart';

import 'helpers/isolated_stack_scope.dart';
import 'helpers/live_actor_session.dart';
import 'helpers/matrix_live_room_driver.dart';
import 'helpers/multi_user_test_config.dart';
import 'helpers/test_http_overrides.dart';

const _executionModeValue = String.fromEnvironment(
  'WEAVE_E2E_EXECUTION_MODE',
  defaultValue: 'collaboration',
);

const _supportSafeProgressPhases = <String>{
  'room-provision',
  'room-key-exchange-author',
  'room-key-exchange-collaborator',
  'room-key-exchange-author-send',
  'room-key-exchange-author-self-observe',
  'room-key-exchange-collaborator-observe-author',
  'room-key-exchange-collaborator-send',
  'room-key-exchange-collaborator-self-observe',
  'room-key-exchange-author-observe-collaborator',
  'home-baseline',
  'author-write',
  'author-capabilities',
  'author-profile',
  'author-chat-connect',
  'author-chat-room',
  'author-chat-send',
  'author-chat-observe',
  'author-files-connect',
  'author-files-upload',
  'author-calendar-scopes',
  'author-calendar-create',
  'collaborator-observe',
  'collaborator-capabilities',
  'collaborator-profile',
  'collaborator-chat-connect',
  'collaborator-chat-room',
  'collaborator-chat-observe',
  'collaborator-chat-send',
  'collaborator-files-connect',
  'collaborator-files-observe',
  'collaborator-files-update',
  'collaborator-calendar-scopes',
  'collaborator-calendar-observe',
  'collaborator-calendar-update',
  'outsider-authorization',
  'fresh-session-observation',
  'resource-cleanup',
  'independent-logout',
  'author-navigation',
  'collaborator-navigation',
  'collaboration-evidence',
  'containment-session',
  'containment-capability',
  'containment-calendar-health',
  'containment-shell-health',
  'containment-chat-health',
  'containment-files-health',
  'containment-navigation',
  'containment-calendar',
  'containment-evidence',
};

void main() {
  final previousPlatformErrorHandler = ui.PlatformDispatcher.instance.onError;
  ui.PlatformDispatcher.instance.onError = (Object error, StackTrace stack) {
    return previousPlatformErrorHandler?.call(error, stack) ?? false;
  };
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  binding.platformDispatcher.semanticsEnabledTestValue = false;
  HttpOverrides.global = TestHttpOverrides();
  final executionMode = MultiUserExecutionMode.parse(_executionModeValue);

  late MultiUserTestConfig configuration;
  var profiles = <CollaborationActorRole, LiveActorProfile>{};

  setUpAll(() {
    configuration = MultiUserTestConfig.fromEnvironment();
    configuration.requireReady();
    profiles = <CollaborationActorRole, LiveActorProfile>{
      for (final role in CollaborationActorRole.values)
        role: LiveActorProfile(role: role, configuration: configuration),
    };
  });

  tearDownAll(() async {
    for (final profile in profiles.values) {
      await profile.clearTestStorage();
    }
  });

  testWidgets(
    'three isolated app profiles prove live cross-user collaboration',
    (tester) async {
      requireIsolatedStackScope();
      final cleanup = _RunCleanup(
        profiles: profiles,
        matrixHomeserver: configuration.common.matrixHomeserverUrl,
      );
      addTearDown(cleanup.bestEffort);
      final suffix = '${configuration.runHash}-${configuration.runIndex}';
      final authorMessage = 'weave-author-$suffix';
      final collaboratorReply = 'weave-collaborator-$suffix';
      final fileName = 'weave-collaboration-$suffix.txt';
      final initialFileBytes = utf8.encode('initial-$suffix');
      final updatedFileBytes = utf8.encode('updated-$suffix');
      final initialEventTitle = 'Weave collaboration $suffix';
      final updatedEventTitle = 'Weave collaboration updated $suffix';
      final profileNames = <CollaborationActorRole, String>{
        for (final role in CollaborationActorRole.values)
          role: 'Weave ${role.name} $suffix',
      };
      final localePreferences = <CollaborationActorRole, AppLocalePreference>{
        CollaborationActorRole.author: AppLocalePreference.english,
        CollaborationActorRole.collaborator: AppLocalePreference.german,
        CollaborationActorRole.outsider: AppLocalePreference.system,
      };

      late String roomId;
      late String calendarEventId;
      late CalendarScope calendarScope;
      var authorHomeActivities = const <WorkspaceHomeActivity>[];
      var collaboratorHomeActivities = const <WorkspaceHomeActivity>[];
      var outsiderHomeActivities = const <WorkspaceHomeActivity>[];
      var authorMessageObserved = false;
      var collaboratorReplyObserved = false;
      var ciphertextOnlyTransport = false;
      var coldCollaboratorDeviceSetVerified = false;
      var outsiderChatDenied = false;
      var outsiderFilesReadDenied = false;
      var outsiderFilesMutationDenied = false;
      var outsiderFilesDenied = false;
      var outsiderCalendarReadDenied = false;
      var outsiderCalendarMutationDenied = false;
      var outsiderCalendarDenied = false;
      var collaboratorFileObserved = false;
      var authorFileUpdateObserved = false;
      var collaboratorEventObserved = false;
      var authorEventUpdateObserved = false;
      var restoredSessionCount = 0;
      var independentLogout = false;
      var buildIdentityVisible = false;
      var authorNavigationCount = 0;
      var collaboratorNavigationCount = 0;

      final author = profiles[CollaborationActorRole.author]!;
      final collaborator = profiles[CollaborationActorRole.collaborator]!;
      final outsider = profiles[CollaborationActorRole.outsider]!;

      _emitProgress(configuration, 'room-provision');
      roomId = await _provisionEncryptedSharedRoom(
        configuration: configuration,
        author: author,
        collaborator: collaborator,
        homeserver: configuration.common.matrixHomeserverUrl,
        roomName: 'Weave encrypted collaboration $suffix',
        cleanup: cleanup,
      );
      coldCollaboratorDeviceSetVerified = configuration.runIndex == 1;

      _emitProgress(configuration, 'home-baseline');
      final homeActivityBaseline = <CollaborationActorRole, Set<String>>{};
      for (final entry in profiles.entries) {
        await _withSession(entry.value, (session) async {
          final home = await _requireHome(session);
          homeActivityBaseline[entry.key] = home.recentActivity
              .map((activity) => activity.activityRef)
              .toSet();
        });
      }

      _emitProgress(configuration, 'author-write');
      await _withSession(author, (session) async {
        _emitProgress(configuration, 'author-capabilities');
        await _requireCurrentCapabilities(session);
        _emitProgress(configuration, 'author-profile');
        final original = await _requireProfile(session);
        cleanup.rememberProfile(CollaborationActorRole.author, original);
        cleanup.rememberLocale(
          CollaborationActorRole.author,
          await author.localePreferences.loadUserPreference(),
        );
        await _updateProfile(
          session,
          original,
          profileNames[CollaborationActorRole.author]!,
        );
        await author.localePreferences.saveUserPreference(
          localePreferences[CollaborationActorRole.author]!,
        );

        _emitProgress(configuration, 'author-chat-connect');
        await session.chat.connect();
        _emitProgress(configuration, 'author-chat-room');
        await _requireEncryptedConversation(session, roomId);
        _emitProgress(configuration, 'author-chat-send');
        await session.chat.sendMessage(roomId: roomId, message: authorMessage);
        _emitProgress(configuration, 'author-chat-observe');
        final sentAuthorMessage = await _waitForChatMessage(
          session,
          roomId,
          authorMessage,
        );
        cleanup.rememberChatEvents(roomId, <String>{sentAuthorMessage.id});

        _emitProgress(configuration, 'author-files-connect');
        await session.files.connect();
        _emitProgress(configuration, 'author-files-upload');
        final existing = (await session.files.listDirectory(
          '/',
        )).entries.where((entry) => entry.name == fileName).firstOrNull;
        if (existing != null) {
          await _deleteFile(session.files, existing);
        }
        await session.files.uploadFile(
          '/',
          FileUploadRequest(
            fileName: fileName,
            sizeInBytes: initialFileBytes.length,
            byteStream: Stream<List<int>>.value(initialFileBytes),
          ),
        );
        cleanup.rememberFile(fileName);

        _emitProgress(configuration, 'author-calendar-scopes');
        final scopes = await session.calendar.loadScopes();
        calendarScope = _requireWritableWorkspaceScope(scopes);
        final start = DateTime.now().toUtc().add(const Duration(days: 1));
        _emitProgress(configuration, 'author-calendar-create');
        final event = await session.calendar.createEvent(
          CalendarEventDraft(
            title: initialEventTitle,
            startTime: start,
            endTime: start.add(const Duration(hours: 1)),
            timezone: 'UTC',
            scope: calendarScope,
          ),
        );
        calendarEventId = event.id;
        cleanup.rememberEvent(event.id, calendarScope);
      });

      _emitProgress(configuration, 'collaborator-observe');
      await _withSession(collaborator, (session) async {
        _emitProgress(configuration, 'collaborator-capabilities');
        await _requireCurrentCapabilities(session);
        _emitProgress(configuration, 'collaborator-profile');
        final original = await _requireProfile(session);
        cleanup.rememberProfile(CollaborationActorRole.collaborator, original);
        cleanup.rememberLocale(
          CollaborationActorRole.collaborator,
          await collaborator.localePreferences.loadUserPreference(),
        );
        await _updateProfile(
          session,
          original,
          profileNames[CollaborationActorRole.collaborator]!,
        );
        await collaborator.localePreferences.saveUserPreference(
          localePreferences[CollaborationActorRole.collaborator]!,
        );

        _emitProgress(configuration, 'collaborator-chat-connect');
        await session.chat.connect();
        _emitProgress(configuration, 'collaborator-chat-room');
        await _requireEncryptedConversation(session, roomId);
        _emitProgress(configuration, 'collaborator-chat-observe');
        await _waitForChatMessage(session, roomId, authorMessage);
        authorMessageObserved = true;
        _emitProgress(configuration, 'collaborator-chat-send');
        await session.chat.sendMessage(
          roomId: roomId,
          message: collaboratorReply,
        );
        final sentCollaboratorReply = await _waitForChatMessage(
          session,
          roomId,
          collaboratorReply,
        );
        cleanup.rememberChatEvents(roomId, <String>{sentCollaboratorReply.id});

        _emitProgress(configuration, 'collaborator-files-connect');
        await session.files.connect();
        _emitProgress(configuration, 'collaborator-files-observe');
        final sharedFile = await _waitForFile(session, fileName);
        final initialDownload = await _downloadFile(session.files, sharedFile);
        expect(_hashBytes(initialDownload), _hashBytes(initialFileBytes));
        collaboratorFileObserved = true;
        _emitProgress(configuration, 'collaborator-files-update');
        await _deleteFile(session.files, sharedFile);
        await session.files.uploadFile(
          '/',
          FileUploadRequest(
            fileName: fileName,
            sizeInBytes: updatedFileBytes.length,
            byteStream: Stream<List<int>>.value(updatedFileBytes),
          ),
        );

        _emitProgress(configuration, 'collaborator-calendar-scopes');
        final collaboratorScope = _matchingScope(
          await session.calendar.loadScopes(),
          calendarScope,
        );
        _emitProgress(configuration, 'collaborator-calendar-observe');
        final event = await _waitForCalendarEvent(
          session,
          collaboratorScope,
          title: initialEventTitle,
        );
        collaboratorEventObserved = true;
        _emitProgress(configuration, 'collaborator-calendar-update');
        await _updateCalendarEventEventually(
          session,
          eventId: event.id,
          scope: collaboratorScope,
          expectedTitle: updatedEventTitle,
          draft: CalendarEventDraft(
            title: updatedEventTitle,
            description: event.description,
            startTime: event.startTime,
            endTime: event.endTime,
            timezone: event.timezone ?? 'UTC',
            location: event.location,
            allDay: event.allDay,
            scope: collaboratorScope,
          ),
        );
      });

      _emitProgress(configuration, 'outsider-authorization');
      await _withSession(outsider, (session) async {
        await _requireCurrentCapabilities(session);
        final original = await _requireProfile(session);
        cleanup.rememberProfile(CollaborationActorRole.outsider, original);
        cleanup.rememberLocale(
          CollaborationActorRole.outsider,
          await outsider.localePreferences.loadUserPreference(),
        );
        await _updateProfile(
          session,
          original,
          profileNames[CollaborationActorRole.outsider]!,
        );
        await outsider.localePreferences.saveUserPreference(
          localePreferences[CollaborationActorRole.outsider]!,
        );

        await session.chat.connect();
        final outsiderConversations = await session.chat.loadConversations();
        final targetMembershipVisible = outsiderConversations.any(
          (conversation) => conversation.id == roomId,
        );
        var targetMessageVisible = false;
        try {
          final timeline = await session.chat.loadRoomTimeline(roomId);
          targetMessageVisible = timeline.messages.any(
            (message) =>
                message.text == authorMessage ||
                message.text == collaboratorReply,
          );
        } on ChatFailure {
          targetMessageVisible = false;
        }
        var outsiderSendRejected = false;
        try {
          await session.chat.sendMessage(
            roomId: roomId,
            message: 'weave-outsider-denied-$suffix',
          );
        } on ChatFailure {
          outsiderSendRejected = true;
        }
        outsiderChatDenied =
            !targetMembershipVisible &&
            !targetMessageVisible &&
            outsiderSendRejected;
        expect(outsiderChatDenied, isTrue);

        await session.files.connect();
        var outsiderListingRejected = false;
        var outsiderFileVisible = false;
        try {
          final outsiderListing = await session.files.listDirectory('/');
          outsiderFileVisible = outsiderListing.entries.any(
            (entry) => entry.name == fileName,
          );
        } on FilesFailure catch (failure) {
          outsiderListingRejected = _isWorkspaceAccessDenied(failure);
        }
        var outsiderDownloadRejected = false;
        try {
          await _downloadFile(
            session.files,
            FileEntry(
              id: '/$fileName',
              name: fileName,
              path: '/$fileName',
              isDirectory: false,
            ),
          );
        } on FilesFailure catch (failure) {
          outsiderDownloadRejected = _isWorkspaceAccessDenied(failure);
        }
        var outsiderDeleteRejected = false;
        try {
          await _deleteFile(
            session.files,
            FileEntry(
              id: '/$fileName',
              name: fileName,
              path: '/$fileName',
              isDirectory: false,
            ),
          );
        } on FilesFailure catch (failure) {
          outsiderDeleteRejected = _isWorkspaceAccessDenied(failure);
        }
        outsiderFilesReadDenied =
            (outsiderListingRejected || !outsiderFileVisible) &&
            outsiderDownloadRejected;
        outsiderFilesMutationDenied = outsiderDeleteRejected;
        outsiderFilesDenied =
            outsiderFilesReadDenied && outsiderFilesMutationDenied;
        expect(outsiderFilesDenied, isTrue);

        var outsiderEventVisible = false;
        var outsiderScopedListingRejected = false;
        try {
          final outsiderScopes = await session.calendar.loadScopes();
          for (final outsiderScope in outsiderScopes.scopes.where(
            (scope) => scope.isWorkspace,
          )) {
            try {
              final outsiderEvents = await session.calendar.loadEvents(
                scope: outsiderScope,
              );
              outsiderEventVisible =
                  outsiderEventVisible ||
                  outsiderEvents.events.any(
                    (event) =>
                        event.title == initialEventTitle ||
                        event.title == updatedEventTitle,
                  );
            } on AppFailure {
              outsiderScopedListingRejected = true;
            }
          }
        } on AppFailure {
          outsiderScopedListingRejected = true;
        }
        var outsiderEventReadRejected = false;
        try {
          await session.calendar.readEvent(calendarEventId);
        } on AppFailure {
          outsiderEventReadRejected = true;
        }
        var outsiderEventMutationRejected = false;
        try {
          await session.calendar.deleteEvent(calendarEventId);
        } on AppFailure {
          outsiderEventMutationRejected = true;
        }
        outsiderCalendarReadDenied =
            !outsiderEventVisible &&
            (outsiderScopedListingRejected || outsiderEventReadRejected);
        outsiderCalendarMutationDenied = outsiderEventMutationRejected;
        outsiderCalendarDenied =
            outsiderCalendarReadDenied && outsiderCalendarMutationDenied;
        expect(outsiderCalendarDenied, isTrue);
      });

      _emitProgress(configuration, 'fresh-session-observation');
      await _withRelaunchedSession(author, (session) async {
        restoredSessionCount++;
        await _requireCurrentCapabilities(session);
        final profile = await _requireProfile(session);
        expect(
          profile.displayName == profileNames[CollaborationActorRole.author],
          isTrue,
        );
        expect(
          await author.localePreferences.loadUserPreference(),
          localePreferences[CollaborationActorRole.author],
        );
        await session.chat.connect();
        final observedReply = await _waitForChatMessage(
          session,
          roomId,
          collaboratorReply,
        );
        collaboratorReplyObserved = true;
        final authorTimeline = await session.chat.loadRoomTimeline(roomId);
        final observedAuthorMessage = authorTimeline.messages.firstWhere(
          (message) => message.text == authorMessage,
        );
        cleanup.rememberChatEvents(roomId, <String>{
          observedAuthorMessage.id,
          observedReply.id,
        });
        ciphertextOnlyTransport = await _verifyCiphertextOnlyTransport(
          session,
          configuration.common.matrixHomeserverUrl,
          roomId: roomId,
          eventIds: <String>{observedAuthorMessage.id, observedReply.id},
          plaintexts: <String>{authorMessage, collaboratorReply},
        );
        expect(ciphertextOnlyTransport, isTrue);

        await session.files.connect();
        final updatedFile = await _waitForFile(session, fileName);
        final updatedDownload = await _downloadFile(session.files, updatedFile);
        expect(_hashBytes(updatedDownload), _hashBytes(updatedFileBytes));
        authorFileUpdateObserved = true;

        final event = await _waitForCalendarEvent(
          session,
          _matchingScope(await session.calendar.loadScopes(), calendarScope),
          title: updatedEventTitle,
        );
        expect(event.id == calendarEventId, isTrue);
        authorEventUpdateObserved = true;

        final home = await _requireHome(session);
        authorHomeActivities = _newCompletedFilesActivities(
          home,
          homeActivityBaseline[CollaborationActorRole.author]!,
        );
      });

      await _withRelaunchedSession(collaborator, (session) async {
        restoredSessionCount++;
        final profile = await _requireProfile(session);
        expect(
          profile.displayName ==
              profileNames[CollaborationActorRole.collaborator],
          isTrue,
        );
        expect(
          await collaborator.localePreferences.loadUserPreference(),
          localePreferences[CollaborationActorRole.collaborator],
        );
        final home = await _requireHome(session);
        collaboratorHomeActivities = _newCompletedFilesActivities(
          home,
          homeActivityBaseline[CollaborationActorRole.collaborator]!,
        );
      });

      await _withRelaunchedSession(outsider, (session) async {
        restoredSessionCount++;
        final profile = await _requireProfile(session);
        expect(
          profile.displayName == profileNames[CollaborationActorRole.outsider],
          isTrue,
        );
        expect(
          await outsider.localePreferences.loadUserPreference(),
          localePreferences[CollaborationActorRole.outsider],
        );
        final home = await _requireHome(session);
        outsiderHomeActivities = _newCompletedFilesActivities(
          home,
          homeActivityBaseline[CollaborationActorRole.outsider]!,
        );
      });

      expect(
        profileNames.values.toSet().length ==
            CollaborationActorRole.values.length,
        isTrue,
      );
      expect(restoredSessionCount, CollaborationActorRole.values.length);
      _emitProgress(configuration, 'resource-cleanup');
      final cleanupComplete = await cleanup.requireComplete();
      expect(cleanupComplete, isTrue);

      _emitProgress(configuration, 'independent-logout');
      final authorLogoutSession = await author.relaunch();
      final collaboratorLogoutSession = await collaborator.relaunch();
      try {
        expect(await authorLogoutSession.hasRestorableSession(), isTrue);
        expect(await collaboratorLogoutSession.hasRestorableSession(), isTrue);
        await authorLogoutSession.signOut();
        final authorSignedOut = !await authorLogoutSession
            .hasRestorableSession();
        final collaboratorStayedSignedIn = await collaboratorLogoutSession
            .hasRestorableSession();
        await collaboratorLogoutSession.signOut();
        final collaboratorSignedOut = !await collaboratorLogoutSession
            .hasRestorableSession();
        independentLogout =
            authorSignedOut &&
            collaboratorStayedSignedIn &&
            collaboratorSignedOut;
        expect(independentLogout, isTrue);
      } finally {
        await authorLogoutSession.close();
        await collaboratorLogoutSession.close();
      }

      _emitProgress(configuration, 'author-navigation');
      final authorUiSession = await author.open();
      try {
        authorNavigationCount = await _visitCurrentMemberRoutes(
          tester,
          authorUiSession,
        );
        authorUiSession.container
            .read(appRouterProvider)
            .go(AppRoutes.settings);
        final buildIdentity = await authorUiSession.container.read(
          clientBuildIdentityProvider.future,
        );
        expect(buildIdentity.isCandidateTraceable, isTrue);
        await _pumpUntil(
          tester,
          () =>
              find.text(buildIdentity.candidateCommit).evaluate().isNotEmpty &&
              find.text(buildIdentity.bundleIdentifier).evaluate().isNotEmpty &&
              find
                  .text(configuration.common.backendApiBaseUrl.origin)
                  .evaluate()
                  .isNotEmpty,
          reason: 'Settings did not expose support-safe candidate diagnostics.',
        );
        buildIdentityVisible = true;
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump();
        await authorUiSession.close();
      }

      _emitProgress(configuration, 'collaborator-navigation');
      final collaboratorUiSession = await collaborator.open();
      try {
        collaboratorNavigationCount = await _visitCurrentMemberRoutes(
          tester,
          collaboratorUiSession,
        );
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump();
        await collaboratorUiSession.close();
      }

      const expectedCompletedFilesActivityCount = 3;
      final authorActivityRefs = authorHomeActivities
          .map((activity) => activity.activityRef)
          .toSet();
      final collaboratorActivityRefs = collaboratorHomeActivities
          .map((activity) => activity.activityRef)
          .toSet();
      final authorCurrentActorHashes = _actorHashes(
        authorHomeActivities,
        currentUser: true,
      );
      final authorOtherActorHashes = _actorHashes(
        authorHomeActivities,
        currentUser: false,
      );
      final collaboratorCurrentActorHashes = _actorHashes(
        collaboratorHomeActivities,
        currentUser: true,
      );
      final collaboratorOtherActorHashes = _actorHashes(
        collaboratorHomeActivities,
        currentUser: false,
      );
      final authorizedProjectionMatches =
          _sameSet(authorActivityRefs, collaboratorActivityRefs) &&
          authorActivityRefs.length == expectedCompletedFilesActivityCount;
      final actorPerspectiveMatches =
          authorCurrentActorHashes.length == 1 &&
          authorOtherActorHashes.length == 1 &&
          collaboratorCurrentActorHashes.length == 1 &&
          collaboratorOtherActorHashes.length == 1 &&
          _sameSet(authorCurrentActorHashes, collaboratorOtherActorHashes) &&
          _sameSet(authorOtherActorHashes, collaboratorCurrentActorHashes);
      final homeEvidencePassed =
          authorizedProjectionMatches &&
          actorPerspectiveMatches &&
          authorHomeActivities.length == expectedCompletedFilesActivityCount &&
          collaboratorHomeActivities.length ==
              expectedCompletedFilesActivityCount &&
          authorHomeActivities
                  .where((activity) => activity.actorIsCurrentUser)
                  .length ==
              1 &&
          collaboratorHomeActivities
                  .where((activity) => activity.actorIsCurrentUser)
                  .length ==
              2 &&
          outsiderHomeActivities.isEmpty &&
          authorHomeActivities.every((activity) => activity.supportSafe) &&
          collaboratorHomeActivities.every((activity) => activity.supportSafe);
      final wrongWorkspaceVerified =
          outsiderChatDenied && outsiderFilesDenied && outsiderCalendarDenied;
      final authorizationEvidencePassed =
          wrongWorkspaceVerified &&
          configuration.missingCapabilityVerified &&
          configuration.expiredTokenVerified &&
          configuration.revokedSessionVerified;
      final verifiedAuthorizationModeCount = <bool>[
        wrongWorkspaceVerified,
        configuration.missingCapabilityVerified,
        configuration.expiredTokenVerified,
        configuration.revokedSessionVerified,
      ].where((verified) => verified).length;
      final organizationDiscoveryCount = profiles.values.fold<int>(
        0,
        (count, profile) => count + profile.organizationDiscoveryCount,
      );
      final realDeviceStorageProfiles = profiles.values.every(
        (profile) => profile.usesRealDeviceStorage,
      );
      expect(organizationDiscoveryCount, CollaborationActorRole.values.length);
      expect(realDeviceStorageProfiles, isTrue);

      _emitProgress(configuration, 'collaboration-evidence');
      _emitEvidence('MULTI_USER_AUTH_SHELL_RESULT', configuration, <
        String,
        Object
      >{
        'actorCount': CollaborationActorRole.values.length,
        'authorHash': configuration.actorHash(CollaborationActorRole.author),
        'collaboratorHash': configuration.actorHash(
          CollaborationActorRole.collaborator,
        ),
        'outsiderHash': configuration.actorHash(
          CollaborationActorRole.outsider,
        ),
        'sessionRestoreCount': restoredSessionCount,
        'shellReached': true,
        'authorNavigationCount': authorNavigationCount,
        'collaboratorNavigationCount': collaboratorNavigationCount,
        'authorAllDestinationsVisited': authorNavigationCount == 6,
        'collaboratorAllDestinationsVisited': collaboratorNavigationCount == 6,
        'organizationDiscoveryCount': organizationDiscoveryCount,
        'authorOrganizationDiscovered': author.organizationDiscovered,
        'collaboratorOrganizationDiscovered':
            collaborator.organizationDiscovered,
        'realDeviceStorageProfiles': realDeviceStorageProfiles,
      });
      _emitEvidence(
        'MULTI_USER_HOME_RESULT',
        configuration,
        <String, Object>{
          'authorObservedCount': authorHomeActivities.length,
          'collaboratorObservedCount': collaboratorHomeActivities.length,
          'outsiderObservedCount': outsiderHomeActivities.length,
          'sharedActivityCount': authorActivityRefs.length,
          'authorizedProjectionMatches': authorizedProjectionMatches,
          'actorPerspectiveMatches': actorPerspectiveMatches,
          'itemLevelProjectionAvailable': true,
          'unauthorizedActivityExcluded': outsiderHomeActivities.isEmpty,
        },
        status: homeEvidencePassed ? 'passed' : 'failed',
      );
      _emitEvidence('MULTI_USER_CHAT_RESULT', configuration, <String, Object>{
        'authorMessageObserved': authorMessageObserved,
        'collaboratorReplyObserved': collaboratorReplyObserved,
        'ciphertextOnlyTransport': ciphertextOnlyTransport,
        if (configuration.runIndex == 1)
          'coldCollaboratorDeviceSetVerified':
              coldCollaboratorDeviceSetVerified,
        'outsiderDenied': outsiderChatDenied,
        'messageCount': 2,
        'messageCleanupComplete': cleanup.messageCleanupComplete,
        'redactedMessageCount': cleanup.redactedMessageCount,
        'roomMembershipCleanupComplete': cleanup.roomMembershipCleanupComplete,
      });
      _emitEvidence('MULTI_USER_FILES_RESULT', configuration, <String, Object>{
        'collaboratorObserved': collaboratorFileObserved,
        'authorUpdateObserved': authorFileUpdateObserved,
        'outsiderDenied': outsiderFilesDenied,
        'outsiderReadDenied': outsiderFilesReadDenied,
        'outsiderMutationDenied': outsiderFilesMutationDenied,
        'initialChecksumHash': _hashBytes(initialFileBytes),
        'updatedChecksumHash': _hashBytes(updatedFileBytes),
        'cleanupComplete': cleanupComplete,
      });
      _emitEvidence(
        'MULTI_USER_CALENDAR_RESULT',
        configuration,
        <String, Object>{
          'collaboratorObserved': collaboratorEventObserved,
          'authorUpdateObserved': authorEventUpdateObserved,
          'outsiderDenied': outsiderCalendarDenied,
          'outsiderReadDenied': outsiderCalendarReadDenied,
          'outsiderMutationDenied': outsiderCalendarMutationDenied,
          'eventCount': 1,
          'cleanupComplete': cleanupComplete,
        },
      );
      _emitEvidence(
        'MULTI_USER_SETTINGS_PROFILE_RESULT',
        configuration,
        <String, Object>{
          'profileCount': CollaborationActorRole.values.length,
          'settingsPersisted': true,
          'profilePersisted': true,
          'identityIsolation': true,
          'independentLogout': independentLogout,
          'buildIdentityVisible': buildIdentityVisible,
          'cleanupComplete': cleanupComplete,
        },
      );
      _emitEvidence(
        'MULTI_USER_AUTHORIZATION_RESULT',
        configuration,
        <String, Object>{
          'chatDenied': outsiderChatDenied,
          'filesDenied': outsiderFilesDenied,
          'calendarDenied': outsiderCalendarDenied,
          'wrongWorkspaceVerified': wrongWorkspaceVerified,
          'missingCapabilityVerified': configuration.missingCapabilityVerified,
          'expiredTokenVerified': configuration.expiredTokenVerified,
          'revokedSessionVerified': configuration.revokedSessionVerified,
          'verifiedModeCount': verifiedAuthorizationModeCount,
        },
        status: authorizationEvidencePassed
            ? 'passed'
            : wrongWorkspaceVerified
            ? 'blocked'
            : 'failed',
      );
      if (!homeEvidencePassed || !authorizationEvidencePassed) {
        final blockers = <String>[
          if (!homeEvidencePassed)
            'Home activity authorization evidence failed',
          if (!authorizationEvidencePassed)
            'one or more authorization runtime modes are not verified',
        ];
        fail('Live readiness remains blocked: ${blockers.join('; ')}.');
      }
    },
    timeout: const Timeout(Duration(minutes: 15)),
    skip: executionMode != MultiUserExecutionMode.collaboration,
  );

  testWidgets(
    'a controlled Calendar outage stays local to the Calendar route',
    (tester) async {
      requireIsolatedStackScope();
      final author = profiles[CollaborationActorRole.author]!;
      _emitProgress(configuration, 'containment-session');
      final session = await author.open();
      try {
        _emitProgress(configuration, 'containment-capability');
        _emitProgress(configuration, 'containment-calendar-health');
        final outageSnapshot = await _waitForCalendarNotReady(session);
        _emitProgress(configuration, 'containment-shell-health');
        expect(outageSnapshot.shellAccess.isReady, isTrue);
        _emitProgress(configuration, 'containment-chat-health');
        expect(outageSnapshot.chat.isReady, isTrue);
        _emitProgress(configuration, 'containment-files-health');
        expect(outageSnapshot.files.isReady, isTrue);

        _emitProgress(configuration, 'containment-navigation');
        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: session.container,
            child: const WeaveApp(),
          ),
        );
        await _pumpUntil(
          tester,
          () => find.byType(NavigationBar).evaluate().isNotEmpty,
          reason: 'Authenticated controlled-failure app did not reach shell.',
        );

        for (final routeAndType in <(String, Type)>[
          (AppRoutes.home, HomeScreen),
          (AppRoutes.chat, ChatScreen),
          (AppRoutes.files, FilesScreen),
          (AppRoutes.settings, SettingsScreen),
          (AppRoutes.profile, ProfileScreen),
        ]) {
          session.container.read(appRouterProvider).go(routeAndType.$1);
          await _pumpUntil(
            tester,
            () => find.byType(routeAndType.$2).evaluate().isNotEmpty,
            reason: 'An unrelated member route was not reachable.',
          );
          expect(find.byType(NavigationBar), findsOneWidget);
        }

        _emitProgress(configuration, 'containment-calendar');
        session.container.read(appRouterProvider).go(AppRoutes.calendar);
        await _pumpUntil(
          tester,
          () => find.byType(CalendarScreen).evaluate().isNotEmpty,
          reason: 'Calendar route was not reachable during its outage.',
        );
        final calendarContext = tester.element(find.byType(CalendarScreen));
        expect(
          find.text(
            AppLocalizations.of(calendarContext).calendarUnavailableTitle,
          ),
          findsOneWidget,
        );
        expect(find.byType(NavigationBar), findsOneWidget);

        _emitProgress(configuration, 'containment-evidence');
        _emitEvidence(
          'MULTI_USER_FAILURE_CONTAINMENT_RESULT',
          configuration,
          <String, Object>{
            'calendarUnavailable': true,
            'realCapabilitySnapshot': true,
            'unrelatedRouteCount': 5,
            'shellPreserved': true,
          },
        );
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump();
        await session.close();
      }
    },
    timeout: const Timeout(Duration(minutes: 5)),
    skip: executionMode != MultiUserExecutionMode.calendarFailureContainment,
  );
}

Future<String> _provisionEncryptedSharedRoom({
  required MultiUserTestConfig configuration,
  required LiveActorProfile author,
  required LiveActorProfile collaborator,
  required Uri homeserver,
  required String roomName,
  required _RunCleanup cleanup,
}) async {
  LiveActorSession? authorSession;
  LiveActorSession? collaboratorSession;
  final client = createTrustedTestHttpClient();
  try {
    authorSession = await author.open();
    collaboratorSession = await collaborator.open();
    await authorSession.chat.connect();
    await collaboratorSession.chat.connect();
    final authorCredentials = await authorSession.matrixTransportCredentials();
    final collaboratorCredentials = await collaboratorSession
        .matrixTransportCredentials();
    final driver = MatrixLiveRoomDriver(client: client, homeserver: homeserver);
    final provisioned = await driver.createEncryptedRoom(
      author: MatrixLiveActorCredentials(
        accessToken: authorCredentials.accessToken,
        deviceId: authorCredentials.deviceId,
      ),
      collaborator: MatrixLiveActorCredentials(
        accessToken: collaboratorCredentials.accessToken,
        deviceId: collaboratorCredentials.deviceId,
      ),
      roomName: roomName,
      requireColdCollaboratorDevice: configuration.runIndex == 1,
    );
    cleanup.rememberChatRoom(provisioned.roomId);
    if (provisioned.collaboratorUserId == null ||
        provisioned.collaboratorUserId == provisioned.authorUserId) {
      throw StateError(
        'The isolated Chat room did not bind two distinct live identities.',
      );
    }
    // The room is arranged through the Matrix facade after both app-owned
    // crypto clients have opened. Synchronize that committed provider state
    // into each isolated device store before disposing the setup sessions so
    // the subsequent fresh sessions exercise restoration, not a setup race.
    await _requireEncryptedConversation(authorSession, provisioned.roomId);
    await _requireEncryptedConversation(
      collaboratorSession,
      provisioned.roomId,
    );
    final keyExchangeEventIds = <String>{};
    try {
      await _establishEncryptedDeviceExchange(
        configuration: configuration,
        authorSession: authorSession,
        collaboratorSession: collaboratorSession,
        roomId: provisioned.roomId,
        eventIds: keyExchangeEventIds,
      );
      final redactedCount = await driver.redactEventsAndVerify(
        actor: MatrixLiveActorCredentials(
          accessToken: authorCredentials.accessToken,
          deviceId: authorCredentials.deviceId,
        ),
        roomId: provisioned.roomId,
        eventIds: keyExchangeEventIds,
      );
      if (redactedCount != keyExchangeEventIds.length) {
        throw StateError(
          'The encrypted device-key exchange was not cleaned completely.',
        );
      }
    } catch (_) {
      if (keyExchangeEventIds.isNotEmpty) {
        try {
          await driver.redactEventsAndVerify(
            actor: MatrixLiveActorCredentials(
              accessToken: authorCredentials.accessToken,
              deviceId: authorCredentials.deviceId,
            ),
            roomId: provisioned.roomId,
            eventIds: keyExchangeEventIds,
          );
        } catch (_) {
          // The run-level cleanup still owns the room and will remove the
          // isolated namespace even when best-effort probe redaction fails.
        }
      }
      rethrow;
    }
    return provisioned.roomId;
  } finally {
    client.close();
    await collaboratorSession?.close();
    await authorSession?.close();
  }
}

Future<void> _establishEncryptedDeviceExchange({
  required MultiUserTestConfig configuration,
  required LiveActorSession authorSession,
  required LiveActorSession collaboratorSession,
  required String roomId,
  required Set<String> eventIds,
}) async {
  const maximumAttempts = 3;
  const observationTimeout = Duration(seconds: 20);
  Object? lastFailure;

  for (var attempt = 1; attempt <= maximumAttempts; attempt++) {
    try {
      // Re-synchronize both established device stores before every bounded
      // attempt. This exercises Matrix device-list and to-device key delivery
      // instead of accepting room membership as proof of decryptability.
      await authorSession.chat.connect();
      await collaboratorSession.chat.connect();

      _emitProgress(configuration, 'room-key-exchange-author');
      _emitProgress(configuration, 'room-key-exchange-author-send');
      final authorProbe =
          'weave-key-exchange-author-${configuration.runHash}-'
          '${configuration.runIndex}-$attempt';
      await authorSession.chat.sendMessage(
        roomId: roomId,
        message: authorProbe,
      );
      _emitProgress(configuration, 'room-key-exchange-author-self-observe');
      final authorEvent = await _waitForChatMessage(
        authorSession,
        roomId,
        authorProbe,
        timeout: observationTimeout,
      );
      eventIds.add(authorEvent.id);
      _emitProgress(
        configuration,
        'room-key-exchange-collaborator-observe-author',
      );
      final collaboratorObservation = await _waitForChatMessage(
        collaboratorSession,
        roomId,
        authorProbe,
        timeout: observationTimeout,
      );
      if (collaboratorObservation.id != authorEvent.id) {
        throw StateError(
          'The collaborator resolved a different encrypted Chat event.',
        );
      }

      _emitProgress(configuration, 'room-key-exchange-collaborator');
      _emitProgress(configuration, 'room-key-exchange-collaborator-send');
      final collaboratorProbe =
          'weave-key-exchange-collaborator-${configuration.runHash}-'
          '${configuration.runIndex}-$attempt';
      await collaboratorSession.chat.sendMessage(
        roomId: roomId,
        message: collaboratorProbe,
      );
      _emitProgress(
        configuration,
        'room-key-exchange-collaborator-self-observe',
      );
      final collaboratorEvent = await _waitForChatMessage(
        collaboratorSession,
        roomId,
        collaboratorProbe,
        timeout: observationTimeout,
      );
      eventIds.add(collaboratorEvent.id);
      _emitProgress(
        configuration,
        'room-key-exchange-author-observe-collaborator',
      );
      final authorObservation = await _waitForChatMessage(
        authorSession,
        roomId,
        collaboratorProbe,
        timeout: observationTimeout,
      );
      if (authorObservation.id != collaboratorEvent.id) {
        throw StateError(
          'The author resolved a different encrypted Chat event.',
        );
      }
      return;
    } catch (error) {
      lastFailure = error;
      if (attempt < maximumAttempts) {
        await Future<void>.delayed(const Duration(seconds: 1));
      }
    }
  }

  final supportCode = lastFailure is _ChatObservationFailure
      ? lastFailure.code
      : 'M_WEAVE_E2EE_DEVICE_EXCHANGE_FAILED';
  throw StateError(
    'The two established Matrix devices could not exchange encrypted '
    'messages. Failure code: $supportCode.',
  );
}

Future<ChatConversation> _requireEncryptedConversation(
  LiveActorSession session,
  String roomId,
) async {
  final conversation = await _eventually(
    session.chat.loadConversations,
    (conversations) => conversations.any(
      (candidate) =>
          candidate.id == roomId &&
          candidate.previewType == ChatConversationPreviewType.encrypted &&
          !candidate.isInvite,
    ),
    reason: 'The fresh production Chat session did not resolve its room.',
  );
  return conversation.firstWhere((candidate) => candidate.id == roomId);
}

Future<WorkspaceCapabilitySnapshot> _waitForCalendarNotReady(
  LiveActorSession session,
) async {
  final snapshot = await _eventually<WorkspaceCapabilitySnapshot?>(
    () async {
      session.container.invalidate(weaveApiWorkspaceCapabilitySnapshotProvider);
      return session.container.read(
        weaveApiWorkspaceCapabilitySnapshotProvider.future,
      );
    },
    (candidate) => candidate != null && !candidate.calendar.isReady,
    reason: 'The real backend did not report Calendar as non-ready.',
    timeout: const Duration(minutes: 2),
  );
  if (snapshot == null) {
    throw StateError('The real backend returned no capability snapshot.');
  }
  return snapshot;
}

Future<T> _withSession<T>(
  LiveActorProfile profile,
  Future<T> Function(LiveActorSession session) action,
) async {
  final session = await profile.open();
  try {
    return await action(session);
  } finally {
    await session.close();
  }
}

bool _isWorkspaceAccessDenied(FilesFailure failure) {
  return failure.type == FilesFailureType.invalidCredentials &&
      failure.cause == HttpStatus.forbidden;
}

Future<T> _withRelaunchedSession<T>(
  LiveActorProfile profile,
  Future<T> Function(LiveActorSession session) action,
) async {
  final session = await profile.relaunch();
  try {
    return await action(session);
  } finally {
    await session.close();
  }
}

Future<int> _visitCurrentMemberRoutes(
  WidgetTester tester,
  LiveActorSession session,
) async {
  await tester.pumpWidget(
    UncontrolledProviderScope(
      container: session.container,
      child: const WeaveApp(),
    ),
  );
  await _pumpUntil(
    tester,
    () => find.byType(NavigationBar).evaluate().isNotEmpty,
    reason: 'Authenticated member did not reach the application shell.',
  );

  var visitedCount = 0;
  for (final routeAndType in <(String, Type)>[
    (AppRoutes.home, HomeScreen),
    (AppRoutes.chat, ChatScreen),
    (AppRoutes.files, FilesScreen),
    (AppRoutes.calendar, CalendarScreen),
    (AppRoutes.settings, SettingsScreen),
    (AppRoutes.profile, ProfileScreen),
  ]) {
    session.container.read(appRouterProvider).go(routeAndType.$1);
    await _pumpUntil(
      tester,
      () => find.byType(routeAndType.$2).evaluate().isNotEmpty,
      reason: 'A current member route was not reachable after sign-in.',
    );
    expect(find.byType(NavigationBar), findsOneWidget);
    visitedCount += 1;
  }
  return visitedCount;
}

Future<WorkspaceCapabilitySnapshot> _requireCurrentCapabilities(
  LiveActorSession session,
) async {
  final snapshot = await session.container.read(
    weaveApiWorkspaceCapabilitySnapshotProvider.future,
  );
  if (snapshot == null) {
    throw StateError('Live workspace capabilities were not returned.');
  }
  expect(snapshot.shellAccess.isReady, isTrue);
  expect(snapshot.chat.isReady, isTrue);
  expect(snapshot.files.isReady, isTrue);
  expect(snapshot.calendar.isReady, isTrue);
  expect(snapshot.chat.grants('chat.send'), isTrue);
  expect(snapshot.files.grants('files.upload'), isTrue);
  expect(snapshot.calendar.grants('calendar.manage_events'), isTrue);
  return snapshot;
}

Future<WorkspaceHomeSnapshot> _requireHome(LiveActorSession session) async {
  final home = await session.container.read(
    weaveApiWorkspaceHomeProvider.future,
  );
  if (home == null) {
    throw StateError('Live Home projection was not returned.');
  }
  expect(home.version, 2);
  expect(home.supportSafe, isTrue);
  // Home aggregates product-line sections beyond the current AppShell scope.
  // A degraded aggregate is navigable, but blocked/unavailable is not; the
  // release gate below relies on fresh authorized activity items plus the
  // independent UI navigation evidence rather than this aggregate alone.
  expect(home.isMemberSurfaceAvailable, isTrue);
  expect(home.recentActivity.every((activity) => activity.supportSafe), isTrue);
  return home;
}

List<WorkspaceHomeActivity> _newCompletedFilesActivities(
  WorkspaceHomeSnapshot home,
  Set<String> baselineRefs,
) {
  return home.recentActivity
      .where(
        (activity) =>
            !baselineRefs.contains(activity.activityRef) &&
            activity.domain == WorkspaceHomeActivityDomain.files &&
            activity.action ==
                WorkspaceHomeActivityAction.filesWebDavWriteCompleted,
      )
      .toList(growable: false);
}

Set<String> _actorHashes(
  List<WorkspaceHomeActivity> activities, {
  required bool currentUser,
}) {
  return activities
      .where((activity) => activity.actorIsCurrentUser == currentUser)
      .map((activity) => activity.actorRefHash)
      .toSet();
}

bool _sameSet(Set<String> left, Set<String> right) {
  return left.length == right.length && left.containsAll(right);
}

Future<UserProfile> _requireProfile(LiveActorSession session) async {
  final profile = await session.profile.loadProfile();
  if (profile == null) {
    throw StateError('Authenticated live profile was not returned.');
  }
  return profile;
}

Future<void> _updateProfile(
  LiveActorSession session,
  UserProfile original,
  String displayName,
) async {
  final updated = await session.profile.updateProfile(
    UserProfileUpdate(
      displayName: displayName,
      locale: original.locale,
      timezone: original.timezone,
    ),
  );
  expect(updated.displayName == displayName, isTrue);
}

Future<ChatMessage> _waitForChatMessage(
  LiveActorSession session,
  String roomId,
  String expectedText, {
  Duration timeout = const Duration(seconds: 45),
}) async {
  try {
    final timeline = await _eventually(
      () => session.chat.loadRoomTimeline(roomId),
      (timeline) =>
          timeline.messages.any((message) => message.text == expectedText),
      reason: 'A committed Chat message was not observed in a fresh session.',
      timeout: timeout,
    );
    return timeline.messages.firstWhere(
      (message) => message.text == expectedText,
    );
  } catch (_) {
    var supportCode = 'M_WEAVE_E2EE_MESSAGE_NOT_OBSERVED';
    try {
      supportCode = (await session.chatDecryptionDiagnostics(
        roomId,
      )).supportCode;
    } catch (_) {
      // The generic code remains support-safe when diagnostics are unavailable.
    }
    throw _ChatObservationFailure(supportCode);
  }
}

class _ChatObservationFailure implements Exception {
  const _ChatObservationFailure(this.code);

  final String code;

  @override
  String toString() => code;
}

Future<bool> _verifyCiphertextOnlyTransport(
  LiveActorSession session,
  Uri homeserver, {
  required String roomId,
  required Set<String> eventIds,
  required Set<String> plaintexts,
}) async {
  final credentials = await session.matrixTransportCredentials();
  final client = createTrustedTestHttpClient();
  try {
    final rawTimeline = await _eventually(
      () async {
        final response = await client.get(
          homeserver.replace(
            pathSegments: <String>[
              '_matrix',
              'client',
              'v3',
              'rooms',
              roomId,
              'messages',
            ],
            queryParameters: const <String, String>{'dir': 'b', 'limit': '100'},
          ),
          headers: <String, String>{
            'Authorization': 'Bearer ${credentials.accessToken}',
            'X-Weave-Matrix-Device-Id': credentials.deviceId,
            'Accept': 'application/json',
          },
        );
        if (response.statusCode < 200 || response.statusCode >= 300) {
          throw StateError(
            'The raw Chat transport returned HTTP ${response.statusCode}.',
          );
        }
        return response.body;
      },
      (body) => _containsEncryptedEvents(
        body,
        eventIds: eventIds,
        plaintexts: plaintexts,
      ),
      reason: 'Committed Chat events were not ciphertext-only in transport.',
    );
    return _containsEncryptedEvents(
      rawTimeline,
      eventIds: eventIds,
      plaintexts: plaintexts,
    );
  } finally {
    client.close();
  }
}

bool _containsEncryptedEvents(
  String body, {
  required Set<String> eventIds,
  required Set<String> plaintexts,
}) {
  try {
    final decoded = jsonDecode(body);
    if (decoded is! Map || decoded['chunk'] is! List) {
      return false;
    }
    final eventsById = <String, Map<Object?, Object?>>{
      for (final event in decoded['chunk'] as List)
        if (event is Map && event['event_id'] is String)
          event['event_id'] as String: event,
    };
    final expectedEvents = eventIds.map((eventId) => eventsById[eventId]);
    return expectedEvents.every(
          (event) => event != null && event['type'] == 'm.room.encrypted',
        ) &&
        plaintexts.every((plaintext) => !body.contains(plaintext));
  } on FormatException {
    return false;
  }
}

Future<FileEntry> _waitForFile(
  LiveActorSession session,
  String fileName,
) async {
  final listing = await _eventually(
    () => session.files.listDirectory('/'),
    (value) => value.entries.any((entry) => entry.name == fileName),
    reason: 'A committed workspace file was not observed in a fresh session.',
  );
  return listing.entries.firstWhere((entry) => entry.name == fileName);
}

Future<List<int>> _downloadFile(
  FilesRepository repository,
  FileEntry entry,
) async {
  if (repository is! FilesExportRepository) {
    throw StateError('The live Files repository cannot download files.');
  }
  return (await (repository as FilesExportRepository).downloadFile(
    entry,
  )).bytes;
}

Future<void> _deleteFile(FilesRepository repository, FileEntry entry) async {
  if (repository is! FilesEntryMutationRepository) {
    throw StateError('The live Files repository cannot delete files.');
  }
  await (repository as FilesEntryMutationRepository).deleteEntry(entry);
}

CalendarScope _requireWritableWorkspaceScope(CalendarScopeList scopeList) {
  const requiredScopeOperations = <String>{'read', 'create', 'edit', 'delete'};
  return scopeList.scopes.firstWhere(
    (scope) =>
        scope.isWorkspace &&
        requiredScopeOperations.every(scope.capabilities.contains),
    orElse: () => throw StateError(
      'The workspace Calendar capability does not allow live collaboration.',
    ),
  );
}

CalendarScope _matchingScope(
  CalendarScopeList scopeList,
  CalendarScope expected,
) {
  return scopeList.scopes.firstWhere(
    (scope) => scope.id == expected.id && scope.type == expected.type,
    orElse: () => throw StateError(
      'A collaborator did not receive the shared Calendar scope.',
    ),
  );
}

Future<CalendarEvent> _waitForCalendarEvent(
  LiveActorSession session,
  CalendarScope scope, {
  required String title,
}) async {
  final events = await _eventually(
    () => session.calendar.loadEvents(scope: scope),
    (value) => value.events.any((event) => event.title == title),
    reason: 'A committed Calendar event was not observed in a fresh session.',
  );
  return events.events.firstWhere((event) => event.title == title);
}

Future<CalendarEvent> _updateCalendarEventEventually(
  LiveActorSession session, {
  required String eventId,
  required CalendarScope scope,
  required String expectedTitle,
  required CalendarEventDraft draft,
}) async {
  final deadline = DateTime.now().add(const Duration(seconds: 60));
  Object? lastFailure;
  var mutationAccepted = false;

  while (DateTime.now().isBefore(deadline)) {
    try {
      final listed = await session.calendar.loadEvents(scope: scope);
      final listedUpdate = listed.events
          .where((event) => event.id == eventId && event.title == expectedTitle)
          .firstOrNull;
      if (listedUpdate != null) {
        return listedUpdate;
      }

      final current = await session.calendar.readEvent(eventId);
      if (current.id == eventId && current.title == expectedTitle) {
        return current;
      }
      if (!mutationAccepted) {
        final etag = current.etag;
        if (etag == null || etag.isEmpty) {
          throw StateError(
            'The current Calendar event has no concurrency version.',
          );
        }
        await session.calendar.updateEvent(eventId, draft, etag: etag);
        mutationAccepted = true;
      }
    } catch (error) {
      lastFailure = error;
    }
    await Future<void>.delayed(const Duration(seconds: 1));
  }

  throw StateError(
    'The collaborator Calendar update did not converge through CalDAV. '
    'Last failure type: ${lastFailure?.runtimeType ?? 'none'}.',
  );
}

Future<T> _eventually<T>(
  Future<T> Function() operation,
  bool Function(T value) predicate, {
  required String reason,
  Duration timeout = const Duration(seconds: 45),
}) async {
  final deadline = DateTime.now().add(timeout);
  Object? lastError;
  while (DateTime.now().isBefore(deadline)) {
    try {
      final value = await operation();
      if (predicate(value)) {
        return value;
      }
    } catch (error) {
      lastError = error;
    }
    await Future<void>.delayed(const Duration(seconds: 1));
  }
  throw StateError(
    '$reason Last failure type: ${lastError?.runtimeType ?? 'none'}.',
  );
}

Future<void> _pumpUntil(
  WidgetTester tester,
  bool Function() condition, {
  required String reason,
  Duration timeout = const Duration(seconds: 45),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(milliseconds: 250));
    if (condition()) {
      return;
    }
  }
  fail(reason);
}

String _hashBytes(List<int> bytes) => sha256.convert(bytes).toString();

void _emitProgress(MultiUserTestConfig configuration, String phase) {
  if (!_supportSafeProgressPhases.contains(phase)) {
    throw StateError('Unsupported multi-user progress phase.');
  }
  // Fixed allowlisted phase names provide failure locality without identities,
  // provider responses, URLs, credentials, or mutable application content.
  // ignore: avoid_print
  print('MULTI_USER_PROGRESS phase=$phase runIndex=${configuration.runIndex}');
}

void _emitEvidence(
  String marker,
  MultiUserTestConfig configuration,
  Map<String, Object> fields, {
  String status = 'passed',
}) {
  final evidence = <String, Object>{
    'status': status,
    'runIndex': configuration.runIndex,
    'runHash': configuration.runHash,
    'supportSafe': true,
    ...fields,
  };
  for (final entry in evidence.entries) {
    final value = entry.value;
    final valid =
        value is bool ||
        (value is int &&
            (entry.key == 'runIndex' || entry.key.endsWith('Count'))) ||
        (value is String &&
            entry.key == 'status' &&
            const <String>{'passed', 'blocked', 'failed'}.contains(value)) ||
        (value is String &&
            entry.key.endsWith('Hash') &&
            RegExp(r'^[0-9a-f]{16,64}$').hasMatch(value));
    if (!valid) {
      throw StateError(
        'Support-safe evidence field ${entry.key} has an unsupported shape.',
      );
    }
  }
  // ignore: avoid_print
  print('$marker ${jsonEncode(evidence)}');
}

class _RunCleanup {
  _RunCleanup({required this.profiles, required this.matrixHomeserver});

  final Map<CollaborationActorRole, LiveActorProfile> profiles;
  final Uri matrixHomeserver;
  final Map<CollaborationActorRole, UserProfile> _originalProfiles =
      <CollaborationActorRole, UserProfile>{};
  final Map<CollaborationActorRole, AppLocalePreference?> _originalLocales =
      <CollaborationActorRole, AppLocalePreference?>{};
  String? _fileName;
  String? _eventId;
  CalendarScope? _eventScope;
  String? _chatRoomId;
  Set<String> _chatEventIds = const <String>{};
  bool _messageCleanupComplete = false;
  int _redactedMessageCount = 0;
  bool _collaboratorMembershipLeft = false;
  bool _authorMembershipLeft = false;
  bool _completed = false;

  bool get messageCleanupComplete => _messageCleanupComplete;
  int get redactedMessageCount => _redactedMessageCount;
  bool get roomMembershipCleanupComplete =>
      _collaboratorMembershipLeft && _authorMembershipLeft;

  void rememberProfile(CollaborationActorRole role, UserProfile profile) {
    _originalProfiles.putIfAbsent(role, () => profile);
  }

  void rememberLocale(
    CollaborationActorRole role,
    AppLocalePreference? preference,
  ) {
    _originalLocales.putIfAbsent(role, () => preference);
  }

  void rememberFile(String fileName) {
    _fileName = fileName;
  }

  void rememberEvent(String eventId, CalendarScope scope) {
    _eventId = eventId;
    _eventScope = scope;
  }

  void rememberChatRoom(String roomId) {
    _chatRoomId = roomId;
  }

  void rememberChatEvents(String roomId, Set<String> eventIds) {
    rememberChatRoom(roomId);
    _chatEventIds = Set<String>.unmodifiable(<String>{
      ..._chatEventIds,
      ...eventIds,
    });
  }

  Future<bool> requireComplete() async {
    final result = await _cleanup();
    _completed = result;
    return result;
  }

  Future<void> bestEffort() async {
    if (_completed) {
      return;
    }
    await _cleanup();
  }

  Future<bool> _cleanup() async {
    var complete = true;
    final authorProfile = profiles[CollaborationActorRole.author]!;
    try {
      await _withSession(authorProfile, (session) async {
        complete =
            await _attemptResult(() => _cleanupChat(session)) && complete;
        complete = await _attempt(() => _cleanupFile(session)) && complete;
        complete = await _attempt(() => _cleanupEvent(session)) && complete;
        complete =
            await _attempt(
              () => _restoreProfile(
                session,
                _originalProfiles[CollaborationActorRole.author],
              ),
            ) &&
            complete;
      });
    } catch (_) {
      complete = false;
    }

    for (final role in <CollaborationActorRole>[
      CollaborationActorRole.collaborator,
      CollaborationActorRole.outsider,
    ]) {
      final profile = profiles[role]!;
      try {
        await _withSession(profile, (session) {
          return _restoreProfile(session, _originalProfiles[role]);
        });
      } catch (_) {
        complete = false;
      }
    }

    for (final role in CollaborationActorRole.values) {
      final profile = profiles[role]!;
      try {
        final original = _originalLocales[role];
        if (original == null) {
          await profile.clearLocalePreference();
        } else {
          await profile.localePreferences.saveUserPreference(original);
        }
      } catch (_) {
        complete = false;
      }
    }
    return complete;
  }

  Future<bool> _cleanupChat(LiveActorSession authorSession) async {
    final chatRoomId = _chatRoomId;
    if (chatRoomId == null ||
        (_messageCleanupComplete && roomMembershipCleanupComplete)) {
      return true;
    }

    var complete = true;
    await authorSession.chat.connect();
    final authorCredentials = await authorSession.matrixTransportCredentials();
    final authorActor = MatrixLiveActorCredentials(
      accessToken: authorCredentials.accessToken,
      deviceId: authorCredentials.deviceId,
    );
    final client = createTrustedTestHttpClient();
    final driver = MatrixLiveRoomDriver(
      client: client,
      homeserver: matrixHomeserver,
    );
    try {
      if (!_messageCleanupComplete) {
        if (_chatEventIds.isEmpty) {
          _messageCleanupComplete = true;
        } else {
          try {
            _redactedMessageCount = await driver.redactEventsAndVerify(
              actor: authorActor,
              roomId: chatRoomId,
              eventIds: _chatEventIds,
            );
            _messageCleanupComplete =
                _redactedMessageCount == _chatEventIds.length;
          } catch (_) {
            complete = false;
          }
        }
      }

      if (!_collaboratorMembershipLeft) {
        complete =
            await _attempt(() async {
              final collaboratorProfile =
                  profiles[CollaborationActorRole.collaborator]!;
              await _withSession(collaboratorProfile, (
                collaboratorSession,
              ) async {
                await collaboratorSession.chat.connect();
                final credentials = await collaboratorSession
                    .matrixTransportCredentials();
                await driver.leaveRoom(
                  actor: MatrixLiveActorCredentials(
                    accessToken: credentials.accessToken,
                    deviceId: credentials.deviceId,
                  ),
                  roomId: chatRoomId,
                );
                _collaboratorMembershipLeft = true;
              });
            }) &&
            complete;
      }

      final safeToLeaveAuthor =
          _messageCleanupComplete || _chatEventIds.isEmpty;
      if (safeToLeaveAuthor && !_authorMembershipLeft) {
        complete =
            await _attempt(() async {
              await driver.leaveRoom(actor: authorActor, roomId: chatRoomId);
              _authorMembershipLeft = true;
            }) &&
            complete;
      }
      return complete &&
          _messageCleanupComplete &&
          roomMembershipCleanupComplete;
    } finally {
      client.close();
    }
  }

  Future<void> _cleanupFile(LiveActorSession session) async {
    final fileName = _fileName;
    if (fileName == null) {
      return;
    }
    await session.files.connect();
    final listing = await session.files.listDirectory('/');
    final file = listing.entries
        .where((entry) => entry.name == fileName)
        .firstOrNull;
    if (file != null) {
      await _deleteFile(session.files, file);
    }
    final after = await session.files.listDirectory('/');
    if (after.entries.any((entry) => entry.name == fileName)) {
      throw StateError('Disposable file cleanup did not complete.');
    }
  }

  Future<void> _cleanupEvent(LiveActorSession session) async {
    final eventId = _eventId;
    final eventScope = _eventScope;
    if (eventId == null || eventScope == null) {
      return;
    }
    try {
      await session.calendar.deleteEvent(eventId);
    } on AppFailure {
      // A missing event is already clean; verify through the scope list.
    }
    final after = await session.calendar.loadEvents(scope: eventScope);
    if (after.events.any((event) => event.id == eventId)) {
      throw StateError('Disposable Calendar cleanup did not complete.');
    }
  }

  Future<bool> _attempt(Future<void> Function() action) async {
    try {
      await action();
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<bool> _attemptResult(Future<bool> Function() action) async {
    try {
      return await action();
    } catch (_) {
      return false;
    }
  }

  Future<void> _restoreProfile(
    LiveActorSession session,
    UserProfile? original,
  ) async {
    if (original == null) {
      return;
    }
    final restored = await session.profile.updateProfile(
      UserProfileUpdate(
        displayName: original.displayName,
        locale: original.locale,
        timezone: original.timezone,
      ),
    );
    if (restored.displayName != original.displayName) {
      throw StateError('Disposable profile cleanup did not complete.');
    }
  }
}
