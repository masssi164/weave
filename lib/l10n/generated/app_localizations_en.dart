// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'Weave';

  @override
  String get welcomeTitle => 'Welcome to Weave';

  @override
  String get welcomeSubtitle =>
      'Your unified collaboration hub for messaging, files, and secure self-hosted access.';

  @override
  String get continueButton => 'Get Started';

  @override
  String get setupTitle => 'Setup';

  @override
  String get setupProviderStepTitle => 'Connect Your Server';

  @override
  String get setupProviderStepDescription =>
      'Choose your OIDC provider and enter the issuer URL for your self-hosted setup.';

  @override
  String get setupServicesStepTitle => 'Review Service Endpoints';

  @override
  String get setupServicesStepDescription =>
      'Weave derives Matrix, Nextcloud, and backend API URLs from the issuer host. Review and edit them before finishing setup.';

  @override
  String get setupLanguageStepTitle => 'Your Language';

  @override
  String get setupLanguageStepDescription =>
      'Weave uses your device language. You can change it later in settings.';

  @override
  String get setupConfirmStepTitle => 'You\'re All Set';

  @override
  String get setupConfirmStepDescription => 'Tap Finish to start using Weave.';

  @override
  String get setupNextButton => 'Next';

  @override
  String get setupFinishButton => 'Finish';

  @override
  String get setupBackButton => 'Back';

  @override
  String setupStepIndicator(int current, int total) {
    return 'Step $current of $total';
  }

  @override
  String get navChat => 'Chat';

  @override
  String get navFiles => 'Files';

  @override
  String get navCalendar => 'Calendar';

  @override
  String get navDeck => 'Boards preview';

  @override
  String get navSettings => 'Settings';

  @override
  String get loadingLabel => 'Loading…';

  @override
  String get bootstrapLoadingLabel => 'Preparing Weave…';

  @override
  String get bootstrapLoadingHint =>
      'Checking your workspace services and getting the shell ready.';

  @override
  String get shellErrorTitle => 'We could not get Weave ready';

  @override
  String get shellErrorGuidance =>
      'Try again. If this keeps happening, check that your workspace services are reachable.';

  @override
  String get shellRecentActivityTitle => 'Recent activity';

  @override
  String get shellRecentActivityDescription =>
      'Quick links to recent rooms and file changes.';

  @override
  String get shellRecentActivitySemanticLabel => 'Recent activity quick links';

  @override
  String get shellRecentRoomsTitle => 'Rooms';

  @override
  String get shellRecentFilesTitle => 'Files';

  @override
  String get shellRecentRoomsLoading => 'Loading recent rooms…';

  @override
  String get shellRecentRoomsEmpty => 'No recent rooms yet.';

  @override
  String get shellRecentRoomsUnavailable =>
      'Recent rooms are unavailable until chat is connected.';

  @override
  String get shellRecentFilesLoading => 'Loading recent file changes…';

  @override
  String get shellRecentFilesEmpty => 'No recent file changes yet.';

  @override
  String get shellRecentFilesError =>
      'Recent file changes could not be loaded.';

  @override
  String get shellRecentFilesUnavailable =>
      'Recent files are unavailable until files are connected.';

  @override
  String get shellRecentActivityUnknownRecency => 'recent';

  @override
  String get shellRecentActivityNow => 'now';

  @override
  String shellRecentActivityMinutesAgo(int minutes) {
    return '${minutes}m ago';
  }

  @override
  String get shellRecentActivityToday => 'today';

  @override
  String get shellRecentActivityYesterday => 'yesterday';

  @override
  String shellRecentRoomItemSemantic(
    String roomName,
    String preview,
    String recency,
  ) {
    return 'Open room $roomName. Latest activity: $preview. $recency.';
  }

  @override
  String shellRecentFileItemSemantic(
    String itemType,
    String itemName,
    String path,
    String recency,
  ) {
    return 'Open $itemType $itemName in $path. Changed $recency.';
  }

  @override
  String get shellRecentFileFolderType => 'folder';

  @override
  String get shellRecentFileFileType => 'file';

  @override
  String get emptyStateLabel => 'Nothing here yet';

  @override
  String get errorStateLabel => 'Something went wrong';

  @override
  String get retryButton => 'Retry';

  @override
  String get semanticBackButton => 'Go back';

  @override
  String get semanticCloseButton => 'Close';

  @override
  String get semanticChatIcon => 'Chat messages';

  @override
  String get semanticFilesIcon => 'File browser';

  @override
  String get semanticCalendarIcon => 'Calendar events';

  @override
  String get semanticDeckIcon => 'Boards preview';

  @override
  String get semanticSettingsIcon => 'Application settings';

  @override
  String get semanticWeaveLogo => 'Weave logo';

  @override
  String get firstRunAppBarTitle => 'First-run status';

  @override
  String get firstRunLoadingLabel => 'Checking your Weave workspace…';

  @override
  String get firstRunLoadingHint =>
      'Loading your profile, role, and module readiness from the Weave backend.';

  @override
  String get firstRunLoadFailure =>
      'We could not load your first-run status from the Weave backend.';

  @override
  String get firstRunSignedOutMessage =>
      'Sign in to view your Weave first-run status.';

  @override
  String get firstRunReadyTitle => 'Your Weave workspace is ready';

  @override
  String get firstRunNeedsAttentionTitle =>
      'Your Weave workspace is being prepared';

  @override
  String get firstRunDescription =>
      'You signed in once with Weave SSO. Weave is checking your profile and collaboration modules; no separate Matrix or Nextcloud credentials are needed.';

  @override
  String get firstRunIdentitySectionTitle => 'Your Weave identity';

  @override
  String get firstRunIdentitySectionDescription =>
      'This profile and role come from the Weave backend contract after SSO.';

  @override
  String get firstRunDisplayNameLabel => 'Name';

  @override
  String get firstRunUsernameLabel => 'Username';

  @override
  String get firstRunEmailLabel => 'Email';

  @override
  String get firstRunRoleLabel => 'Role';

  @override
  String get firstRunInviteStatusLabel => 'Invite';

  @override
  String get firstRunModuleSectionTitle => 'Module readiness';

  @override
  String get firstRunProfileModuleTitle => 'Profile';

  @override
  String get firstRunChatModuleTitle => 'Chat';

  @override
  String get firstRunFilesModuleTitle => 'Files';

  @override
  String get firstRunCalendarModuleTitle => 'Calendar';

  @override
  String get firstRunStateReady => 'Ready';

  @override
  String get firstRunStatePending => 'Pending';

  @override
  String get firstRunStateUnavailable => 'Unavailable';

  @override
  String get firstRunStateDegraded => 'Degraded';

  @override
  String get firstRunStateActionNeeded => 'Action needed';

  @override
  String get firstRunNextStepsTitle => 'Next steps';

  @override
  String get firstRunRefreshButton => 'Refresh status';

  @override
  String get firstRunContinueButton => 'Continue to chat';

  @override
  String get chatProvisioningReadyTitle => 'Chat is ready';

  @override
  String get chatProvisioningPendingTitle =>
      'Chat rooms are still being prepared';

  @override
  String get chatProvisioningDegradedTitle =>
      'Chat is available with degraded setup';

  @override
  String get chatProvisioningActionNeededTitle =>
      'Chat setup needs admin attention';

  @override
  String get chatProvisioningRetryButton => 'Retry status';

  @override
  String get chatScreenTitle => 'Chat';

  @override
  String get chatOverviewTitle => 'Weave Home';

  @override
  String get chatOverviewDescription =>
      'Your personal messages, favorites, channels, and AI chats are grouped here so the workspace starts from intent instead of a flat room list.';

  @override
  String get chatFavoritesSectionTitle => 'Favorites';

  @override
  String get chatFavoritesSectionDescription =>
      'Pinned people, channels, and AI chats you want to reach first.';

  @override
  String get chatFavoritesSectionEmpty =>
      'No favorites yet. When favorites sync is available, important direct messages, channels, and AI chats will stay here.';

  @override
  String get chatPersonalMessagesSectionTitle => 'Personal messages';

  @override
  String get chatPersonalMessagesSectionDescription =>
      'Direct conversations with people in your workspace.';

  @override
  String get chatPersonalMessagesSectionEmpty =>
      'No personal messages are available yet.';

  @override
  String get chatChannelsSectionTitle => 'Channels';

  @override
  String get chatChannelsSectionDescription =>
      'Team and topic rooms for shared work.';

  @override
  String get chatChannelsSectionEmpty => 'No channels are available yet.';

  @override
  String get chatAiChatsSectionTitle => 'AI chats';

  @override
  String get chatAiChatsSectionDescription =>
      'Specialized assistant and agent chats live in their own area.';

  @override
  String get chatAiChatsSectionEmpty =>
      'No AI chats are connected yet. Future specialized agents will appear here instead of being mixed into personal messages.';

  @override
  String get chatAgentGovernanceTitle =>
      'Agent chats are governed by your workspace';

  @override
  String get chatAgentGovernanceDescription =>
      'Agents can help inside Weave only after an owner or admin enables a package, chooses scopes, and keeps consent and audit visible.';

  @override
  String get chatAgentContextPackTitle => 'Context pack before action';

  @override
  String get chatAgentContextPackDescription =>
      'When an agent is available, Weave will show what context is sent for this request before the agent uses it.';

  @override
  String get chatAgentContextPackScopedBullet =>
      'Context is scoped to a selected chat, file, calendar event, board, or explicit workspace source.';

  @override
  String get chatAgentContextPackConsentBullet =>
      'You will see permission hints before starting or approving an agent action.';

  @override
  String get chatAgentContextPackNoSurveillanceBullet =>
      'Agents do not continuously read rooms in the background.';

  @override
  String get chatAgentGovernanceAuditNote =>
      'Audit placeholders are part of this preview: agent creation, context access, tool/action execution, approval, and revocation must be recorded before runtime promotion.';

  @override
  String get chatAgentAvailabilityPreview => 'Preview only';

  @override
  String get chatAgentAvailabilityAdminSetup => 'Admin setup required';

  @override
  String get chatAgentAvailabilityBlocked => 'Blocked by policy';

  @override
  String get chatAgentPersonalAssistantTitle => 'Personal assistant';

  @override
  String get chatAgentPersonalAssistantDescription =>
      'A future private assistant chat for drafting, summaries, and reminders inside Weave.';

  @override
  String get chatAgentChannelAgentTitle => 'Channel agent';

  @override
  String get chatAgentChannelAgentDescription =>
      'A future helper for a channel or project space, governed by an admin-approved package.';

  @override
  String get chatAgentPersonalScope =>
      'Uses only context you choose for the current request; workspace policy decides which skills are available.';

  @override
  String get chatAgentPersonalBoundary =>
      'No continuous room reading; a context pack is assembled only after you start or approve a request.';

  @override
  String get chatAgentPersonalAudit =>
      'Creation, context access, tool use, and permission changes will be auditable before runtime use.';

  @override
  String get chatAgentChannelScope =>
      'An owner or admin must enable the package and choose allowed chat, files, calendar, and board scopes.';

  @override
  String get chatAgentChannelBoundary =>
      'The agent sees named spaces and explicit context packs, not every message in the workspace.';

  @override
  String get chatAgentChannelAudit =>
      'Approvals, revocations, and action attempts stay visible to admins without exposing secrets to the app.';

  @override
  String get chatAgentStartDisabledButton => 'Unavailable until enabled';

  @override
  String get chatLoadingLabel => 'Loading conversations…';

  @override
  String get chatLoadingHint =>
      'Gathering your latest rooms and recent conversation state.';

  @override
  String get chatConnectingLabel => 'Connecting to Matrix…';

  @override
  String get chatConnectingHint =>
      'We are opening your secure Matrix session and syncing the first room list.';

  @override
  String get chatConnectButton => 'Connect Matrix';

  @override
  String get chatRefreshingRoomsLabel => 'Refreshing chat rooms';

  @override
  String get chatStaleRoomsTitle => 'Showing last known rooms';

  @override
  String get chatStaleRoomsGuidance =>
      'We could not refresh Matrix just now. Your room list is preserved so you can keep your place and retry when the connection is back.';

  @override
  String get chatStaleRoomsRetryButton => 'Refresh rooms';

  @override
  String get filesScreenTitle => 'Files';

  @override
  String get filesLoadingLabel => 'Loading files…';

  @override
  String get filesLoadingHint =>
      'Refreshing the current folder and checking what changed.';

  @override
  String get filesStaleDirectoryTitle => 'Showing last known folder';

  @override
  String get filesStaleDirectoryGuidance =>
      'We could not refresh Files just now. The last folder listing stays visible so you can keep your place and retry when the connection is back.';

  @override
  String get filesStaleDirectoryRetryButton => 'Refresh folder';

  @override
  String get filesNextcloudTitle => 'Weave Files';

  @override
  String get filesProductTitle => 'Weave Files';

  @override
  String get filesProductBoundaryTitle => 'Weave product boundary';

  @override
  String get filesProductBoundaryBody =>
      'Files actions use the Weave backend facade. Nextcloud remains the storage provider and admin/fallback surface; raw provider paths and credentials are not part of the normal Files UX.';

  @override
  String get filesConnectButton => 'Connect Files';

  @override
  String get filesReconnectButton => 'Reconnect Files';

  @override
  String get filesDisconnectButton => 'Disconnect';

  @override
  String get filesRefreshButton => 'Refresh';

  @override
  String get filesUpButton => 'Up';

  @override
  String get filesRootBreadcrumb => 'Root';

  @override
  String filesOpenFolderSemantic(String name) {
    return 'Open folder: $name';
  }

  @override
  String filesCurrentFolderSemantic(String name) {
    return 'Current folder: $name';
  }

  @override
  String filesDirectorySummary(int folderCount, int fileCount) {
    String _temp0 = intl.Intl.pluralLogic(
      folderCount,
      locale: localeName,
      other: '$folderCount folders',
      one: '1 folder',
      zero: 'No folders',
    );
    String _temp1 = intl.Intl.pluralLogic(
      fileCount,
      locale: localeName,
      other: '$fileCount files',
      one: '1 file',
      zero: 'no files',
    );
    return '$_temp0 • $_temp1';
  }

  @override
  String get filesDisconnectedMessage =>
      'Connect Weave Files to browse workspace files.';

  @override
  String get filesInvalidSessionMessage =>
      'Reconnect Files because the Weave session is no longer valid.';

  @override
  String get filesMisconfiguredMessage =>
      'Finish Weave server setup before connecting files.';

  @override
  String filesConnectionConnected(String accountLabel) {
    return 'Connected as $accountLabel';
  }

  @override
  String get filesConnectionDisconnected =>
      'Files are not connected for this Weave session.';

  @override
  String get filesConnectionInvalid =>
      'The Weave Files session needs attention.';

  @override
  String get filesConnectionMisconfigured =>
      'Server setup is incomplete for Weave Files.';

  @override
  String get filesOpenParentSemantic => 'Open parent folder';

  @override
  String get filesRefreshCurrentFolderSemantic => 'Refresh the current folder';

  @override
  String get filesUploadButton => 'Upload';

  @override
  String get filesUploadCurrentFolderSemantic =>
      'Upload a file to the current folder';

  @override
  String get filesCreateFolderButton => 'New folder';

  @override
  String get filesCreateFolderCurrentFolderSemantic =>
      'Create a folder in the current folder';

  @override
  String get filesCreateFolderDialogTitle => 'Create folder';

  @override
  String get filesCreateFolderNameLabel => 'Folder name';

  @override
  String get filesCreateFolderNameHint => 'e.g. Project docs';

  @override
  String get filesCreateFolderConfirmButton => 'Create';

  @override
  String get filesCancelButton => 'Cancel';

  @override
  String get filesDeleteButton => 'Delete';

  @override
  String filesExportEntrySemantic(String name) {
    return 'Export $name to native files';
  }

  @override
  String filesExportProgressMessage(String name) {
    return 'Exporting $name…';
  }

  @override
  String get filesExportProgressUnknownMessage => 'Exporting file…';

  @override
  String filesExportCompletedMessage(String name, String destination) {
    return 'Exported $name to $destination.';
  }

  @override
  String get filesExportCompletedUnknownMessage =>
      'Exported file to native files.';

  @override
  String get filesExportUserVisibleFallback => 'a user-visible files location';

  @override
  String filesDeleteEntrySemantic(String name) {
    return 'Delete $name';
  }

  @override
  String filesDeleteEntryDialogTitle(String name) {
    return 'Delete $name?';
  }

  @override
  String get filesDeleteEntryDialogMessage =>
      'This removes it from Weave files for everyone with access. This cannot be undone.';

  @override
  String get filesCreateFolderProgressUnknownMessage => 'Creating folder…';

  @override
  String filesCreateFolderProgressMessage(String folderName) {
    return 'Creating folder $folderName…';
  }

  @override
  String get filesCreateFolderCompletedUnknownMessage => 'Folder created.';

  @override
  String filesCreateFolderCompletedMessage(String folderName) {
    return 'Created folder $folderName.';
  }

  @override
  String get filesDeleteProgressUnknownMessage => 'Deleting item…';

  @override
  String filesDeleteProgressMessage(String name) {
    return 'Deleting $name…';
  }

  @override
  String get filesDeleteCompletedUnknownMessage => 'Item deleted.';

  @override
  String filesDeleteCompletedMessage(String name) {
    return 'Deleted $name.';
  }

  @override
  String get filesEntryActionFailedMessage => 'File action failed.';

  @override
  String get filesUploadPickingMessage => 'Choose a file to upload…';

  @override
  String get filesUploadProgressUnknownMessage => 'Uploading file…';

  @override
  String filesUploadProgressIndeterminateMessage(String fileName) {
    return 'Uploading $fileName…';
  }

  @override
  String filesUploadProgressMessage(String fileName, int percent) {
    return 'Uploading $fileName: $percent%';
  }

  @override
  String filesUploadProgressSemantic(String fileName, int percent) {
    return 'Upload progress for $fileName: $percent percent';
  }

  @override
  String get filesUploadCompletedUnknownMessage => 'Upload complete.';

  @override
  String filesUploadCompletedMessage(String fileName) {
    return 'Uploaded $fileName.';
  }

  @override
  String get filesUploadFailedUnknownMessage => 'Upload failed.';

  @override
  String filesUploadFailedMessage(String fileName) {
    return 'Upload failed for $fileName.';
  }

  @override
  String filesFolderSemantic(String name) {
    return '$name, folder';
  }

  @override
  String filesFileSemantic(String name) {
    return '$name, file';
  }

  @override
  String get calendarScreenTitle => 'Calendar';

  @override
  String get deckScreenTitle => 'Boards preview';

  @override
  String get settingsScreenTitle => 'Settings';

  @override
  String get settingsBrandSectionDescription =>
      'Weave focuses on accessible, data-sovereign collaboration: chat, files, shared calendars, E2EE architecture, and boards behind clear gates.';

  @override
  String get settingsThemeTitle => 'Appearance';

  @override
  String get settingsThemeDescription =>
      'Choose the visual style for this profile. Workspace brand defaults stay separate, so your personal choice is not overwritten by admin setup.';

  @override
  String get settingsThemeSystemTitle => 'Use device setting';

  @override
  String get settingsThemeSystemDescription =>
      'Follow your device light or dark appearance.';

  @override
  String get settingsThemeLightTitle => 'Light';

  @override
  String get settingsThemeLightDescription =>
      'Use a bright professional palette.';

  @override
  String get settingsThemeDarkTitle => 'Dark';

  @override
  String get settingsThemeDarkDescription =>
      'Use a darker palette for low-light work.';

  @override
  String get settingsThemeHighContrastTitle => 'High contrast';

  @override
  String get settingsThemeHighContrastDescription =>
      'Use stronger contrast while still following your device light or dark appearance.';

  @override
  String get settingsThemeLoading => 'Loading appearance preferences…';

  @override
  String get settingsThemeError =>
      'Appearance preferences could not be saved. Try changing the setting again.';

  @override
  String get settingsHelpTitle => 'Help and user handbook';

  @override
  String get settingsHelpDescription =>
      'Open practical guidance for using Weave, recovering from issues, and understanding privacy basics.';

  @override
  String get helpScreenTitle => 'Help';

  @override
  String get helpHandbookTitle => 'User handbook';

  @override
  String get helpHandbookDescription =>
      'This handbook explains the everyday Weave app in plain language. It is available offline with the app and will grow as more surfaces become ready.';

  @override
  String get helpWhatIsWeaveTitle => 'What Weave is';

  @override
  String get helpWhatIsWeaveBody =>
      'Weave is a collaboration app for teams that want one accessible workspace without giving up data sovereignty. Chat, files, account settings, and future collaboration modules are presented through Weave while open services such as Matrix, Nextcloud, Keycloak, and the Weave backend work behind the scenes.';

  @override
  String get helpSignInTitle => 'Sign in basics';

  @override
  String get helpSignInBody =>
      'Use the workspace address provided by your admin, then sign in once with Weave SSO. You should not need separate Matrix or Nextcloud passwords for normal use. If sign-in loops or fails, check your connection, confirm the server address in Settings, and ask an admin whether your invite or account is active.';

  @override
  String get helpChatTitle => 'Chat';

  @override
  String get helpChatBody =>
      'Chat is the daily place for rooms and messages. Open Chat from the main navigation, connect Matrix if asked, then choose a room. Weave keeps room and recovery states visible so you can see when chat is connected, waiting, degraded, or needs admin attention.';

  @override
  String get helpFilesTitle => 'Files';

  @override
  String get helpFilesBody =>
      'Files lets you browse workspace documents through the Weave app. Open Files from the main navigation, move through folders, and retry if the folder could not refresh. The underlying storage is provided by your workspace services, but everyday browsing should stay inside Weave.';

  @override
  String get helpSettingsTitle => 'Settings, account, and session';

  @override
  String get helpSettingsBody =>
      'Settings shows your profile summary, workspace readiness, server configuration, Matrix security information, and sign-out control. Use it to check whether Chat, Files, Calendar, or other modules are ready, and sign out before handing a device to someone else.';

  @override
  String get helpCalendarBoardsTitle => 'Calendar and Boards availability';

  @override
  String get helpCalendarBoardsBody =>
      'Calendar and Boards are active Weave product scope, but they may be hidden or marked unavailable until your workspace has the required backend contracts and feature gates enabled. If they are not visible in navigation, use Chat, Files, and Settings for now and watch workspace readiness for changes.';

  @override
  String get helpTroubleshootingTitle => 'Troubleshooting and recovery';

  @override
  String get helpTroubleshootingBody =>
      'When something does not load, use Retry first. If a stale chat room list or folder remains visible, Weave is preserving your place while refresh fails. Persistent setup, sign-in, Matrix, files, or backend errors should be shared with your admin together with the visible message and the server address from Settings.';

  @override
  String get helpPrivacySecurityTitle => 'Privacy and security basics';

  @override
  String get helpPrivacySecurityBody =>
      'Your workspace controls its own services and data. Weave uses SSO for access and shows Matrix security status honestly. Do not assume chat is fully end-to-end encrypted unless Weave says the Matrix encryption, recovery, and device-trust gates are healthy. Keep recovery keys in a safe place and report lost devices to your admin.';

  @override
  String get settingsShellModulesTitle => 'Shell modules';

  @override
  String get settingsShellModulesDescription =>
      'Choose which workspace shell modules stay visible. Navigation remains available even when a module is hidden.';

  @override
  String get settingsShellWorkspaceStatusToggleTitle =>
      'Workspace status summary';

  @override
  String get settingsShellWorkspaceStatusToggleDescription =>
      'Show service readiness and recovery shortcuts above the bottom navigation.';

  @override
  String settingsShellMoveModuleUp(String moduleName) {
    return 'Move $moduleName up';
  }

  @override
  String settingsShellMoveModuleDown(String moduleName) {
    return 'Move $moduleName down';
  }

  @override
  String get settingsShellRecentActivityToggleTitle =>
      'Recent activity quick links';

  @override
  String get settingsShellRecentActivityToggleDescription =>
      'Show recent rooms and file changes above the bottom navigation.';

  @override
  String get settingsShellModulesLoading => 'Loading shell module preferences…';

  @override
  String get settingsShellModulesError =>
      'Shell module preferences could not be saved. Try changing the setting again.';

  @override
  String get chatSecuritySectionTitle => 'Matrix security';

  @override
  String get chatSecuritySectionDescription =>
      'Weave only treats Matrix encryption as healthy when secret storage, cross-signing, recovery, and device trust are all in place.';

  @override
  String get chatSecurityRecoveryKeyTitle =>
      'Save this Matrix recovery key now';

  @override
  String get chatSecurityRecoveryKeyDescription =>
      'Weave does not rely on app-only storage for this key because secure storage can disappear after reinstall, device replacement, or some platform restores. Keep it in your password manager or another secure place.';

  @override
  String get chatSecurityBannerTitle => 'Matrix security needs attention';

  @override
  String get chatSecurityBannerSetupMessage =>
      'Encrypted Matrix rooms are available, but this account still needs initial security setup.';

  @override
  String get chatSecurityBannerRecoveryMessage =>
      'This device needs your Matrix recovery key before older encrypted messages can be trusted again.';

  @override
  String get chatSecurityBannerVerificationMessage =>
      'This device or account is not fully verified yet. Compare security emoji with another signed-in Matrix device.';

  @override
  String get chatSecurityBannerMissingBackupMessage =>
      'Matrix key backup is still missing. Set it up before relying on encrypted chat recovery.';

  @override
  String get chatSecurityOpenSettingsButton => 'Open security settings';

  @override
  String get chatSecuritySetupCardTitle => 'Setup';

  @override
  String get chatSecurityCurrentDeviceCardTitle => 'Current device';

  @override
  String get chatSecurityRecoveryCardTitle => 'Recovery and key backup';

  @override
  String get chatSecurityRecoveryCardBody =>
      'The recovery key is needed when this device is replaced, reinstalled, or loses local crypto secrets.';

  @override
  String get chatSecurityEncryptedRoomsCardTitle => 'Encrypted rooms';

  @override
  String get chatSecurityEncryptedRoomsCardBodyExisting =>
      'Encrypted rooms already exist on this account. Warnings stay visible until trust and recovery are healthy.';

  @override
  String get chatSecurityEncryptedRoomsCardBodyNone =>
      'No encrypted rooms are known yet, but the account security state is still tracked here.';

  @override
  String get chatSecurityBoundaryCardTitle => 'Backend and agent boundary';

  @override
  String get chatSecurityBoundaryCardValue => 'Blocked until consent/audit';

  @override
  String get chatSecurityBoundaryCardBody =>
      'Encrypted message contents stay on Matrix devices. Backend diagnostics may use support-safe metadata such as room ID, encryption status, device trust, and timestamps, but not decrypted message bodies. Bots and connectors stay blocked from encrypted rooms until consent, audit, device-trust, and client-identity gates are implemented.';

  @override
  String get chatSecurityRecoveryGuidanceCardTitle =>
      'Device recovery checklist';

  @override
  String get chatSecurityRecoveryGuidanceValueActionRequired =>
      'Action required';

  @override
  String get chatSecurityRecoveryGuidanceValueReady =>
      'Ready for device changes';

  @override
  String get chatSecurityRecoveryGuidanceIntro =>
      'Before relying on encrypted chat across devices:';

  @override
  String get chatSecurityRecoveryGuidanceSaveRecovery =>
      'Save the recovery key or passphrase outside Weave, preferably in a password manager.';

  @override
  String get chatSecurityRecoveryGuidanceVerifyDevice =>
      'Verify this device with another signed-in Matrix device when possible.';

  @override
  String get chatSecurityRecoveryGuidanceNewDevice =>
      'On a new or reinstalled device, use the recovery key first, then verify the device.';

  @override
  String get chatSecurityRecoveryGuidanceLostDevice =>
      'If a device is lost, remove or distrust it from Matrix before trusting new encrypted rooms.';

  @override
  String get chatSecurityRecoveryGuidanceServerCannotRecover =>
      'Weave servers can report safe metadata, but cannot recover encrypted message contents for you.';

  @override
  String get chatSecurityStatusSignedOut => 'Matrix not connected';

  @override
  String get chatSecurityStatusSetupRequired => 'Setup required';

  @override
  String get chatSecurityStatusSetupIncomplete => 'Setup incomplete';

  @override
  String get chatSecurityStatusRecoveryRequired => 'Recovery required';

  @override
  String get chatSecurityStatusHealthy => 'Healthy';

  @override
  String get chatSecurityStatusUnavailable => 'Unavailable';

  @override
  String get chatSecurityStatusVerified => 'Verified';

  @override
  String get chatSecurityStatusUnverified => 'Unverified';

  @override
  String get chatSecurityStatusBlocked => 'Blocked';

  @override
  String get chatSecurityStatusMissing => 'Missing';

  @override
  String get chatSecurityStatusNeedsReconnect => 'Needs reconnect';

  @override
  String get chatSecurityStatusReady => 'Ready';

  @override
  String get chatSecurityEncryptedRoomsStatusNone => 'No encrypted rooms yet';

  @override
  String get chatSecurityEncryptedRoomsStatusAttention =>
      'Encrypted rooms need attention';

  @override
  String get chatSecuritySetupDescriptionSignedOut =>
      'Open Chat and connect Matrix before managing encryption.';

  @override
  String get chatSecuritySetupDescriptionNotInitialized =>
      'Set up secret storage, cross-signing, and online key backup before trusting encrypted rooms.';

  @override
  String get chatSecuritySetupDescriptionPartiallyInitialized =>
      'Some encryption parts exist, but recovery or cross-signing is still incomplete.';

  @override
  String get chatSecuritySetupDescriptionRecoveryRequired =>
      'This account was set up before, but this device needs the recovery key or passphrase to reconnect safely.';

  @override
  String get chatSecuritySetupDescriptionReady =>
      'This device can use the current Matrix crypto identity and recovery setup.';

  @override
  String get chatSecuritySetupDescriptionUnavailable =>
      'Matrix encryption is not available on this platform.';

  @override
  String get chatSecurityCurrentDeviceDescriptionVerified =>
      'Another trusted Matrix device has verified this session.';

  @override
  String get chatSecurityCurrentDeviceDescriptionUnverified =>
      'Compare security emoji or numbers with another signed-in Matrix device.';

  @override
  String get chatSecurityCurrentDeviceDescriptionBlocked =>
      'This device is blocked or its trust chain is broken.';

  @override
  String get chatSecurityCurrentDeviceDescriptionUnavailable =>
      'The current device key is not available yet.';

  @override
  String get chatSecurityActionsUnavailableSignedOut =>
      'Matrix security actions unlock after the Matrix session is connected.';

  @override
  String get chatSecurityWorkingButton => 'Working…';

  @override
  String get chatSecuritySetupButton => 'Set up encrypted chat';

  @override
  String get chatSecurityReconnectButton => 'Reconnect with recovery key';

  @override
  String get chatSecurityVerifyDeviceButton => 'Verify this device';

  @override
  String get chatSecurityAcceptVerificationButton => 'Accept verification';

  @override
  String get chatSecurityDeclineVerificationButton => 'Decline';

  @override
  String get chatSecurityCompareEmojiButton => 'Compare security emoji';

  @override
  String get chatSecurityUnlockVerificationButton =>
      'Continue verification with recovery key';

  @override
  String get chatSecurityEmojiMatchButton => 'Emoji match';

  @override
  String get chatSecurityEmojiMismatchButton => 'They do not match';

  @override
  String get chatSecurityDismissButton => 'Dismiss';

  @override
  String get chatSecurityNoActionNeeded => 'No action is needed right now.';

  @override
  String get chatSecurityGenericFailure =>
      'Unable to update Matrix security right now.';

  @override
  String get chatSecurityNoticeSetupComplete =>
      'Encrypted chat is now set up. Save your recovery key before closing this screen.';

  @override
  String get chatSecurityNoticeRecoveryRestored =>
      'Encrypted chat was reconnected for this device.';

  @override
  String get chatSecurityNoticeVerificationRequestSent =>
      'Verification request sent. Continue on your other Matrix device.';

  @override
  String get chatSecurityNoticeVerificationCancelled =>
      'Verification cancelled.';

  @override
  String get chatSecurityVerificationIncomingMessage =>
      'Another device wants to verify this session.';

  @override
  String get chatSecurityVerificationChooseMethodMessage =>
      'Choose a verification method to compare both devices.';

  @override
  String get chatSecurityVerificationWaitingMessage =>
      'Waiting for the other device to continue verification.';

  @override
  String get chatSecurityVerificationRecoveryMessage =>
      'This verification needs your Matrix recovery key or passphrase before it can continue.';

  @override
  String get chatSecurityVerificationRecoveryHelp =>
      'Unlock the existing Matrix secret storage to let this device complete verification safely.';

  @override
  String get chatSecurityVerificationCompareMessage =>
      'Compare the security emoji or numbers on both devices.';

  @override
  String get chatSecurityVerificationDoneMessage =>
      'This device is now verified.';

  @override
  String get chatSecurityVerificationCancelledMessage =>
      'Verification was cancelled before it finished.';

  @override
  String get chatSecurityVerificationFailedMessage =>
      'Verification could not be completed.';

  @override
  String get chatSecuritySetupDialogTitle => 'Set up encrypted chat';

  @override
  String get chatSecuritySetupDialogDescription =>
      'You can optionally protect the Matrix recovery key with a memorable passphrase. Leave this blank to use a generated recovery key instead.';

  @override
  String get chatSecurityOptionalPassphraseLabel => 'Optional passphrase';

  @override
  String get chatSecurityDialogCancelButton => 'Cancel';

  @override
  String get chatSecurityDialogContinueButton => 'Continue';

  @override
  String get chatSecurityRestoreDialogTitle => 'Reconnect encrypted chat';

  @override
  String get chatSecurityRestoreDialogDescription =>
      'Enter the Matrix recovery key or recovery passphrase that was created when encrypted chat was first set up.';

  @override
  String get chatSecurityVerificationRecoveryDialogTitle =>
      'Continue verification';

  @override
  String get chatSecurityVerificationRecoveryDialogDescription =>
      'Enter your Matrix recovery key or passphrase to continue this verification. This unlocks the secrets needed for verification rather than reconnecting the whole account.';

  @override
  String get chatSecurityRecoveryKeyFieldLabel => 'Recovery key or passphrase';

  @override
  String get chatSecurityRecoveryKeyDismissButton => 'I saved it';

  @override
  String get chatSecurityEmojiSummaryLabel => 'Security emoji';

  @override
  String chatSecurityNumbersSummaryLabel(String value) {
    return 'Security numbers $value';
  }

  @override
  String get settingsPreviewSurfacesTitle => 'Preview surfaces';

  @override
  String get settingsPreviewSurfacesDescription =>
      'These feature-gated surfaces stay honest about what is active, blocked, or still waiting for backend contracts.';

  @override
  String get settingsGuestPortalPreviewTitle => 'Guest Portal';

  @override
  String get settingsGuestPortalPreviewDescription =>
      'Guest invitations and constrained access will appear here without exposing member-only affordances.';

  @override
  String get settingsInteropAdminPreviewTitle =>
      'External connections admin status';

  @override
  String get settingsInteropAdminPreviewDescription =>
      'External provider status will explain data movement and consent; provider secrets are never collected in this client.';

  @override
  String get settingsMigrationDryRunPreviewTitle => 'Migration dry-run report';

  @override
  String get settingsMigrationDryRunPreviewDescription =>
      'Admins will be able to review inventory, risks, scopes, and mappings before any import starts.';

  @override
  String get settingsServerConfigurationTitle => 'Server Configuration';

  @override
  String get settingsWorkspaceReadinessTitle => 'Workspace Readiness';

  @override
  String get settingsWorkspaceReadinessDescription =>
      'Shell access is tracked separately from each service connection so Weave can show degraded integrations honestly.';

  @override
  String get settingsWorkspaceBackendUnreachable =>
      'Backend API is unreachable. Check that the Weave stack is running and the configured backend URL is correct.';

  @override
  String get settingsWorkspaceBackendUnauthorized =>
      'Backend API rejected the current session. Sign in again before retrying.';

  @override
  String get settingsWorkspaceBackendServerError =>
      'Backend API returned an unexpected response. Check the Weave stack logs before retrying.';

  @override
  String get settingsWorkspaceSummaryConnected =>
      'Shell access and the mapped services are ready.';

  @override
  String get settingsWorkspaceSummaryDegraded =>
      'Shell access is ready, but one or more services still need attention.';

  @override
  String get settingsWorkspaceSummaryNeedsSetup =>
      'Finish setup before the workspace shell can become available.';

  @override
  String get settingsWorkspaceSummaryNeedsSignIn =>
      'Sign in again to restore workspace shell access.';

  @override
  String get settingsWorkspaceShellAccessLabel => 'Shell access';

  @override
  String get settingsWorkspaceChatLabel => 'Chat';

  @override
  String get settingsWorkspaceFilesLabel => 'Files';

  @override
  String get settingsWorkspaceCapabilityLabel => 'Readiness';

  @override
  String get settingsWorkspaceConnectionLabel => 'Connection';

  @override
  String get settingsWorkspaceLastChangeLabel => 'Last change';

  @override
  String get settingsWorkspaceMatrixE2eeGateLabel => 'E2EE gate';

  @override
  String get settingsWorkspaceMatrixE2eeValidated => 'Validated';

  @override
  String get settingsWorkspaceMatrixE2eeNotValidated => 'Not validated';

  @override
  String get settingsWorkspaceMatrixServerBodiesLabel =>
      'Server-readable bodies';

  @override
  String get settingsWorkspaceMatrixServerBodiesOpaque => 'No';

  @override
  String get settingsWorkspaceMatrixServerBodiesReadable => 'Review';

  @override
  String get settingsWorkspaceMatrixAgentWritesLabel => 'Agent writes';

  @override
  String get settingsWorkspaceMatrixAgentWritesBlocked => 'Blocked/fail-closed';

  @override
  String get settingsWorkspaceMatrixAgentWritesReview => 'Review policy';

  @override
  String get settingsWorkspaceCapabilityReady => 'Ready';

  @override
  String get settingsWorkspaceCapabilityDegraded => 'Degraded';

  @override
  String get settingsWorkspaceCapabilityBlocked => 'Blocked';

  @override
  String get settingsWorkspaceCapabilityUnavailable => 'Unavailable';

  @override
  String get settingsWorkspaceConnectionConnected => 'Connected';

  @override
  String get settingsWorkspaceConnectionDisconnected => 'Disconnected';

  @override
  String get settingsWorkspaceConnectionDegraded => 'Degraded';

  @override
  String get settingsWorkspaceConnectionMisconfigured => 'Misconfigured';

  @override
  String get settingsWorkspaceConnectionRequiresReauthentication =>
      'Needs sign-in';

  @override
  String get settingsWorkspaceConnectionUnavailableOnPlatform =>
      'Unavailable on this platform';

  @override
  String get settingsWorkspaceInvalidationAuthConfigurationChanged =>
      'Auth configuration changed';

  @override
  String get settingsWorkspaceInvalidationMatrixHomeserverChanged =>
      'Matrix homeserver changed';

  @override
  String get settingsWorkspaceInvalidationNextcloudBaseUrlChanged =>
      'Nextcloud base URL changed';

  @override
  String get settingsWorkspaceInvalidationExplicitSignOut =>
      'Explicit sign-out';

  @override
  String get settingsWorkspaceInvalidationRestartSetup => 'Restarted setup';

  @override
  String get settingsWorkspaceInvalidationBackendApiBaseUrlChanged =>
      'Backend API URL changed';

  @override
  String get settingsServerConfigurationDescription =>
      'Update the provider and service URLs Weave should use for your self-hosted environment.';

  @override
  String get settingsSaveButton => 'Save Changes';

  @override
  String get settingsSaveInProgress => 'Saving…';

  @override
  String get settingsSignOutTitle => 'Session';

  @override
  String get settingsSignOutDescription =>
      'Sign out of the current server session and return to the sign-in gate.';

  @override
  String get settingsSignOutButton => 'Sign Out';

  @override
  String get settingsSignOutInProgress => 'Signing out…';

  @override
  String get chatEmptyMessage => 'No conversations yet';

  @override
  String get chatEmptyGuidance =>
      'Workspace rooms and direct messages will appear here when chat is ready.';

  @override
  String get chatErrorTitle => 'Chat is not available right now';

  @override
  String get chatConversationNoPreview => 'No recent messages';

  @override
  String get chatConversationEncryptedPreview => 'Encrypted message';

  @override
  String get chatConversationUnsupportedPreview => 'Unsupported message';

  @override
  String get chatConversationInviteLabel => 'Invitation';

  @override
  String get chatConversationDirectMessageLabel => 'Direct conversation';

  @override
  String get chatConversationRecentNow => 'Active now';

  @override
  String get chatConversationRecentToday => 'Today';

  @override
  String get chatConversationRecentYesterday => 'Yesterday';

  @override
  String get chatConversationRecentThisWeek => 'This week';

  @override
  String chatConversationUnreadCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count unread messages',
      one: '1 unread message',
      zero: 'No unread messages',
    );
    return '$_temp0';
  }

  @override
  String get chatRoomLoadingLabel => 'Loading conversation…';

  @override
  String get chatRoomEmptyMessage => 'No messages yet';

  @override
  String get chatRoomDraftRestoredMessage => 'Draft restored from this device.';

  @override
  String get chatRoomComposerHint => 'Write a message';

  @override
  String get chatRoomComposerDisabledHint =>
      'Messages are unavailable in this room right now';

  @override
  String get chatRoomSendButton => 'Send';

  @override
  String get chatRoomSendingButton => 'Sending…';

  @override
  String get chatRoomRetrySendAction => 'Retry send';

  @override
  String get chatRoomYouLabel => 'You';

  @override
  String get chatRoomMessageSendingStatus => 'Sending…';

  @override
  String get chatRoomMessageFailedStatus => 'Not sent';

  @override
  String get chatRoomEncryptedMessageLabel => 'Encrypted message';

  @override
  String get chatRoomUnsupportedMessageLabel => 'Unsupported message';

  @override
  String get chatRoomMessageActionsLabel => 'Message actions';

  @override
  String get chatRoomArchiveAction => 'Archive';

  @override
  String get chatRoomArchiveDialogTitle => 'Archive message?';

  @override
  String get chatRoomArchiveDialogMessage =>
      'This hides the message from your main timeline on this device. You can review or restore it from Archived messages.';

  @override
  String get chatRoomArchivedMessagesAction => 'Review archived messages';

  @override
  String get chatRoomActiveTimelineAction => 'Back to active timeline';

  @override
  String get chatRoomArchivedReviewTitle => 'Archived messages';

  @override
  String chatRoomArchivedReviewDescription(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count archived messages are shown separately from the active timeline.',
      one: '1 archived message is shown separately from the active timeline.',
      zero:
          'Archived messages from this room appear here, separate from the active timeline.',
    );
    return '$_temp0';
  }

  @override
  String get chatRoomArchivedReviewEmptyMessage => 'No archived messages yet.';

  @override
  String get chatRoomArchivedMessageLabel => 'Archived';

  @override
  String get chatRoomRestoreAction => 'Restore to timeline';

  @override
  String get chatRoomRestoreSuccessMessage =>
      'Message restored to the active timeline.';

  @override
  String get chatRoomRestoreFailureMessage =>
      'This message could not be restored right now.';

  @override
  String get chatRoomArchiveSuccessMessage => 'Message archived.';

  @override
  String get chatRoomArchiveFailureMessage =>
      'This message could not be archived right now.';

  @override
  String get chatRoomArchivedEmptyMessage =>
      'Archived messages are hidden from this timeline.';

  @override
  String get chatRoomContextPackTitle => 'Context for this room';

  @override
  String get chatRoomContextPackDescription =>
      'Weave will only include scoped context that you can see here, such as this room, selected files, linked tasks, and recent decisions.';

  @override
  String chatRoomContextPackCounts(int includedCount, int availableCount) {
    String _temp0 = intl.Intl.pluralLogic(
      includedCount,
      locale: localeName,
      other: '$includedCount sources included',
      one: '1 source included',
      zero: 'No sources included',
    );
    String _temp1 = intl.Intl.pluralLogic(
      availableCount,
      locale: localeName,
      other: '$availableCount optional sources available',
      one: '1 optional source available',
      zero: 'No optional sources available',
    );
    return '$_temp0. $_temp1.';
  }

  @override
  String get chatRoomContextPackNoBackgroundReading =>
      'No agent is reading this room in the background.';

  @override
  String get chatRoomContextCurrentRoomLabel => 'Current room';

  @override
  String get chatRoomContextSelectedFilesLabel => 'Selected files';

  @override
  String get chatRoomContextLinkedTasksLabel => 'Linked tasks';

  @override
  String get chatRoomContextRecentDecisionsLabel => 'Recent decisions';

  @override
  String get chatRoomContextIncludedStatus => 'Included';

  @override
  String get chatRoomContextAvailableStatus => 'Available when selected';

  @override
  String get chatDecisionEvidencePanelTitle =>
      'Decisions, risks, questions, and evidence';

  @override
  String get chatDecisionEvidencePanelDescription =>
      'Capture important messages explicitly so the room keeps the reason behind the work. Nothing here is created by hidden room scanning.';

  @override
  String get chatDecisionEvidenceNoBackgroundReading =>
      'Records come from message actions you choose; no automatic continuous room reading is running.';

  @override
  String get chatDecisionEvidenceEmptyState =>
      'No records captured yet. Use a message action to capture a decision, risk, open question, or evidence with its source.';

  @override
  String chatDecisionEvidenceCountLabel(String label, int count) {
    return '$label: $count';
  }

  @override
  String get chatDecisionEvidenceDecisionLabel => 'Decision';

  @override
  String get chatDecisionEvidenceDecisionsLabel => 'Decisions';

  @override
  String get chatDecisionEvidenceRiskLabel => 'Risk';

  @override
  String get chatDecisionEvidenceRisksLabel => 'Risks';

  @override
  String get chatDecisionEvidenceOpenQuestionLabel => 'Open question';

  @override
  String get chatDecisionEvidenceOpenQuestionsLabel => 'Open questions';

  @override
  String get chatDecisionEvidenceEvidenceLabel => 'Evidence';

  @override
  String get chatDecisionEvidenceEvidencePluralLabel => 'Evidence';

  @override
  String get chatDecisionEvidenceOwnerYou => 'You';

  @override
  String get chatDecisionEvidenceStatusActive => 'Active';

  @override
  String get chatDecisionEvidenceStatusResolved => 'Resolved';

  @override
  String get chatDecisionEvidenceStatusArchived => 'Archived';

  @override
  String chatDecisionEvidenceRecordMeta(
    String status,
    String owner,
    String sender,
  ) {
    return '$status. Captured by $owner. Source: message from $sender.';
  }

  @override
  String chatDecisionEvidenceSourceLabel(String sender) {
    return 'Source: message from $sender';
  }

  @override
  String chatDecisionEvidenceCapturedMessage(String kind) {
    return 'Captured as $kind. Source linked to this message.';
  }

  @override
  String chatDecisionEvidenceMoreRecords(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count more records',
      one: '1 more record',
    );
    return '$_temp0';
  }

  @override
  String get chatDecisionEvidenceCaptureDecisionAction => 'Capture as decision';

  @override
  String get chatDecisionEvidenceCaptureRiskAction => 'Capture as risk';

  @override
  String get chatDecisionEvidenceCaptureQuestionAction =>
      'Capture as open question';

  @override
  String get chatDecisionEvidenceCaptureEvidenceAction => 'Capture as evidence';

  @override
  String get filesEmptyMessage => 'No files yet';

  @override
  String get filesEmptyGuidance =>
      'Upload a file or create a folder when you are ready to add workspace files.';

  @override
  String get filesDisconnectedTitle => 'Files are not connected';

  @override
  String get filesSetupNeededTitle => 'Files need setup';

  @override
  String get filesSessionExpiredTitle => 'Files need to reconnect';

  @override
  String get filesLoadErrorTitle => 'Files could not be loaded';

  @override
  String get filesErrorGuidance =>
      'Try again. If this keeps happening, check the workspace files status in setup or diagnostics.';

  @override
  String get calendarEmptyMessage => 'No events yet';

  @override
  String get deckEmptyMessage => 'No boards in this active preview yet';

  @override
  String get deviceLanguageLabel => 'Device Language';

  @override
  String get serverConfigurationProviderLabel => 'OIDC Provider';

  @override
  String get serverConfigurationProviderFieldLabel => 'Provider type';

  @override
  String get oidcProviderAuthentik => 'Authentik';

  @override
  String get oidcProviderKeycloak => 'Keycloak';

  @override
  String get serverConfigurationIssuerLabel => 'OIDC Issuer URL';

  @override
  String get serverConfigurationIssuerHelper =>
      'This must be the absolute issuer URL for your OIDC provider.';

  @override
  String get serverConfigurationClientIdLabel => 'OIDC Client ID';

  @override
  String get serverConfigurationClientIdHelper =>
      'Enter the public/native client ID registered for Weave on this issuer.';

  @override
  String get serverConfigurationServicesLabel => 'Service Endpoints';

  @override
  String get serverConfigurationServicesHelper =>
      'Defaults for Matrix, Nextcloud, and the backend API are derived from the issuer host. Edit them if your services live elsewhere.';

  @override
  String get serverConfigurationMatrixLabel => 'Matrix Homeserver URL';

  @override
  String get serverConfigurationNextcloudLabel => 'Nextcloud Base URL';

  @override
  String get serverConfigurationBackendApiLabel => 'Backend API Base URL';

  @override
  String serverConfigurationDerivedHint(String value) {
    return 'Derived default: $value';
  }

  @override
  String get oidcRegistrationHelpTitle =>
      'Register Weave as a native/public client';

  @override
  String get oidcRegistrationHelpDescription =>
      'Use Authorization Code + PKCE with the system browser, and allow the Weave redirect URIs below on the provider-side client registration.';

  @override
  String get oidcRegistrationHelpNoSecret =>
      'Do not create or paste a client secret here. Weave uses a public native-client flow.';

  @override
  String get oidcRegistrationHelpAuthentikSteps =>
      'In Authentik, create an OAuth2/OpenID Connect provider for Weave, add these redirect URIs to the provider, and ensure the client is configured for Authorization Code flow with `offline_access` available if you want refresh tokens.';

  @override
  String get oidcRegistrationHelpKeycloakSteps =>
      'In Keycloak, create a public OpenID Connect client for Weave, add these redirect URIs and post-logout redirect URIs, and enable Standard Flow with PKCE (S256) so Weave can sign in without a client secret.';

  @override
  String get oidcRegistrationHelpRedirectsTitle =>
      'Register these redirect URIs';

  @override
  String oidcRegistrationHelpRedirectValue(String value) {
    return 'Sign-in redirect: $value';
  }

  @override
  String oidcRegistrationHelpPostLogoutRedirectValue(String value) {
    return 'Post-logout redirect: $value';
  }

  @override
  String get signInScreenTitle => 'Sign In';

  @override
  String get signInTitle => 'Sign in to continue';

  @override
  String get signInDescription =>
      'Weave is configured. Use your provider account in the system browser to open the authenticated app shell.';

  @override
  String get signInConfigurationTitle => 'Current sign-in configuration';

  @override
  String signInConfigurationProvider(String value) {
    return 'Provider: $value';
  }

  @override
  String signInConfigurationIssuer(String value) {
    return 'Issuer: $value';
  }

  @override
  String signInConfigurationClientId(String value) {
    return 'Client ID: $value';
  }

  @override
  String get signInButton => 'Sign In';

  @override
  String get signInInProgress => 'Signing in…';

  @override
  String get signInBackToSetupButton => 'Back to Setup';

  @override
  String get signInMissingConfigurationTitle => 'Finish setup to sign in';

  @override
  String get signInMissingConfigurationDescription =>
      'Weave still needs a valid issuer URL and client ID before it can open the browser sign-in flow.';

  @override
  String get profileSectionTitle => 'Weave profile';

  @override
  String get profileSectionDescription =>
      'This profile comes from the Weave backend identity facade and is shared by product modules.';

  @override
  String get profileLoadFailure =>
      'The Weave profile could not be loaded right now.';

  @override
  String get profileSignedOutMessage => 'Sign in to view your Weave profile.';

  @override
  String get profileDisplayNameLabel => 'Display name';

  @override
  String get profileUsernameLabel => 'Username';

  @override
  String get profileEmailLabel => 'Email';

  @override
  String get profileEmailVerifiedLabel => 'Email verified';

  @override
  String get profileEmailVerifiedYes => 'Yes';

  @override
  String get profileEmailVerifiedNo => 'No';

  @override
  String get profileLocaleLabel => 'Locale';

  @override
  String get profileTimezoneLabel => 'Timezone';

  @override
  String get profileRolesLabel => 'Roles';

  @override
  String get profileGroupsLabel => 'Groups';

  @override
  String get profileEditingBlockedMessage =>
      'Profile editing is prepared in the app, but saving changes is blocked until the backend exposes PATCH /api/profile.';

  @override
  String get profileEditSectionTitle => 'Edit profile';

  @override
  String get profileEditSectionDescription =>
      'Save changes through the Weave backend profile facade so every product module sees the same profile.';

  @override
  String get profileDisplayNameHelper =>
      'Shown to workspace members in Weave surfaces.';

  @override
  String get profileLocaleHelper => 'Use a locale code such as en or de.';

  @override
  String get profileTimezoneHelper =>
      'Use an IANA timezone such as Europe/Berlin.';

  @override
  String get profileEditRequiredFieldError => 'This field is required.';

  @override
  String get profileEditSaveButton => 'Save profile';

  @override
  String get profileEditSavingButton => 'Saving profile…';

  @override
  String get profileEditSavedMessage => 'Profile saved.';

  @override
  String get settingsWorkspaceCalendarLabel => 'Calendar';

  @override
  String get settingsWorkspaceBoardsLabel => 'Boards';

  @override
  String get calendarWorkspaceScopeTitle => 'Workspace calendar';

  @override
  String get calendarWorkspaceScopeDescription =>
      'This first Calendar slice is the workspace scope of Weave shared scheduling. Team and channel calendars are the next product scopes; private personal calendars are out of scope.';

  @override
  String calendarGenericScopeDescription(String scopeLabel) {
    return 'Events are shown from $scopeLabel.';
  }

  @override
  String get calendarClientSetupTitle => 'Use Calendar in other apps';

  @override
  String get calendarClientSetupDescription =>
      'Weave can hand native clients secret-free setup details. Weave still owns the product calendar UI.';

  @override
  String get calendarClientSetupIconSemantic => 'External calendar setup';

  @override
  String get calendarClientSetupLoading => 'Loading setup options…';

  @override
  String get calendarClientSetupUnavailable =>
      'Calendar setup options are unavailable right now.';

  @override
  String get calendarCapabilityLoading => 'Checking Calendar availability…';

  @override
  String get calendarCapabilityError =>
      'Calendar availability could not be checked right now.';

  @override
  String get calendarUnavailableTitle => 'Calendar is unavailable';

  @override
  String calendarUnavailableDescription(String readiness) {
    return 'Backend readiness is $readiness. Event changes stay disabled until the Weave backend reports Calendar ready.';
  }

  @override
  String get calendarClientSetupUsernameLabel => 'Username';

  @override
  String get calendarClientSetupDiscoveryUrlLabel => 'CalDAV discovery URL';

  @override
  String get calendarClientSetupPrincipalUrlLabel => 'Principal URL';

  @override
  String get calendarClientSetupCredentialPolicyTitle => 'Credential safety';

  @override
  String get calendarClientSetupAccessModelTitle => 'Access model';

  @override
  String get calendarClientSetupPrivateCalendarsAvailable =>
      'Private personal calendars out of scope';

  @override
  String get calendarClientSetupPrivateCalendarsBlocked =>
      'Private personal calendars out of scope';

  @override
  String calendarClientSetupExternalCredentialModel(String model) {
    return 'External credential model: $model';
  }

  @override
  String get calendarClientSetupCredentialReadinessTitle =>
      'Credential readiness';

  @override
  String calendarClientSetupCredentialReadinessStatus(String status) {
    return 'Status: $status';
  }

  @override
  String get calendarClientSetupAppleProfileBlocked =>
      'Apple profiles stay disabled until profiles are signed and safe credentials exist.';

  @override
  String get calendarClientSetupSubscriptionsBlocked =>
      'Webcal/ICS subscriptions stay disabled until revocable read-only tokens exist.';

  @override
  String get calendarClientSetupCredentialsSafe =>
      'Backend actor credentials are not exposed to client setup artifacts.';

  @override
  String get calendarClientSetupCredentialsUnsafe =>
      'Setup is blocked because backend actor credentials would be exposed.';

  @override
  String get calendarClientSetupPlatformsTitle => 'Platform setup';

  @override
  String get calendarClientSetupAvailableStatus => 'available';

  @override
  String get calendarClientSetupPlannedStatus => 'planned';

  @override
  String get calendarClientSetupPlannedFallback =>
      'This setup path is feature-gated until revocation, provisioning, and platform profile tests are complete.';

  @override
  String calendarClientSetupOptionTitle(
    String platform,
    String method,
    String status,
  ) {
    return '$platform via $method: $status';
  }

  @override
  String calendarClientSetupCopyTooltip(String label) {
    return 'Copy $label';
  }

  @override
  String get calendarClientSetupCopied => 'Calendar setup value copied.';

  @override
  String get calendarCreateButton => 'Create event';

  @override
  String get calendarCreateDialogTitle => 'Create calendar event';

  @override
  String get calendarEditDialogTitle => 'Edit calendar event';

  @override
  String get calendarTitleFieldLabel => 'Title';

  @override
  String get calendarDescriptionFieldLabel => 'Description';

  @override
  String get calendarLocationFieldLabel => 'Location';

  @override
  String get calendarTitleRequired => 'Enter an event title.';

  @override
  String get calendarCancelButton => 'Cancel';

  @override
  String get calendarSaveButton => 'Save event';

  @override
  String calendarDeleteEventTooltip(String title) {
    return 'Delete $title';
  }

  @override
  String calendarEditEventTooltip(String title) {
    return 'Edit $title';
  }

  @override
  String calendarViewEventTooltip(String title) {
    return 'View $title';
  }

  @override
  String calendarEventSemantic(String title, String startsAt, String endsAt) {
    return '$title, starts $startsAt, ends $endsAt';
  }

  @override
  String get calendarDetailsDialogTitle => 'Calendar event details';

  @override
  String get calendarDetailsLoading => 'Loading event details…';

  @override
  String get calendarDetailsError =>
      'Calendar event details are unavailable right now.';

  @override
  String get calendarDetailsTimeLabel => 'Time';

  @override
  String get calendarDetailsScopeLabel => 'Calendar scope';

  @override
  String get calendarDetailsContextLabel => 'Context';

  @override
  String get calendarDetailsMeetingThreadLabel => 'Meeting thread';

  @override
  String get calendarDetailsMeetingThreadPending =>
      'Safe context metadata is available; chat thread linkage is not configured yet.';

  @override
  String get calendarDetailsAttendeesLabel => 'Attendees';

  @override
  String get calendarDetailsProviderLabel => 'Provider reference';

  @override
  String get calendarDetailsProviderPathHidden => 'raw provider path hidden';

  @override
  String get calendarDetailsUpdatedLabel => 'Updated';

  @override
  String get calendarDetailsLocationLabel => 'Location';

  @override
  String get calendarDetailsDescriptionLabel => 'Description';

  @override
  String get calendarCloseButton => 'Close';

  @override
  String get calendarCreateSuccess => 'Calendar event created.';

  @override
  String get calendarUpdateSuccess => 'Calendar event updated.';

  @override
  String get calendarDeleteSuccess => 'Calendar event deleted.';

  @override
  String get calendarOperationFailure =>
      'The calendar could not save that change right now.';

  @override
  String get boardsPreviewScreenTitle => 'Boards preview';

  @override
  String get boardsPreviewIconSemantic => 'Boards preview';

  @override
  String get boardsPreviewBoundaryTitle => 'Active boards/tasks preview';

  @override
  String get boardsPreviewBoundaryDescription =>
      'This active preview shows the intended Weave-owned board model and accessible task movement alternatives. It remains feature-gated and is not connected to Vikunja, Deck, or another provider yet.';

  @override
  String get boardsPreviewBoundarySemantic =>
      'Active boards/tasks preview. Feature-gated provider-neutral Weave model with keyboard and screen-reader alternatives; no live provider is connected yet.';

  @override
  String get boardsPreviewActivePreviewChip => 'Active preview';

  @override
  String get boardsPreviewProviderNeutralChip => 'Provider-neutral model';

  @override
  String get boardsPreviewKeyboardChip => 'No drag required';

  @override
  String boardsPreviewColumnCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count columns',
      one: '1 column',
      zero: 'No columns',
    );
    return '$_temp0';
  }

  @override
  String boardsPreviewTaskCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tasks',
      one: '1 task',
      zero: 'No tasks',
    );
    return '$_temp0';
  }

  @override
  String get boardsPreviewNonDragMovement => 'Move menu instead of drag-only';

  @override
  String boardsPreviewBoardSemantic(
    String boardName,
    int columnCount,
    int taskCount,
  ) {
    String _temp0 = intl.Intl.pluralLogic(
      columnCount,
      locale: localeName,
      other: '$columnCount columns',
      one: '1 column',
    );
    String _temp1 = intl.Intl.pluralLogic(
      taskCount,
      locale: localeName,
      other: '$taskCount tasks',
      one: '1 task',
    );
    return 'Board $boardName, $_temp0, $_temp1.';
  }

  @override
  String boardsPreviewColumnSemantic(
    String columnName,
    String status,
    int taskCount,
  ) {
    String _temp0 = intl.Intl.pluralLogic(
      taskCount,
      locale: localeName,
      other: '$taskCount tasks',
      one: '1 task',
    );
    return 'Column $columnName, status $status, $_temp0.';
  }

  @override
  String boardsPreviewColumnTaskSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tasks in this column',
      one: '1 task in this column',
      zero: 'No tasks in this column',
    );
    return '$_temp0';
  }

  @override
  String boardsPreviewColumnWipSummary(int count, int limit) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tasks',
      one: '1 task',
      zero: 'No tasks',
    );
    return '$_temp0 · WIP limit $limit';
  }

  @override
  String boardsPreviewTaskSemantic(
    String taskTitle,
    String columnName,
    String status,
    String assignee,
    String due,
    String priority,
  ) {
    return 'Task $taskTitle. Column $columnName. Status $status. Assignee $assignee. Due $due. Priority $priority.';
  }

  @override
  String boardsPreviewTaskActionsTooltip(String taskTitle) {
    return 'Task actions for $taskTitle';
  }

  @override
  String get boardsPreviewMoveTaskAction => 'Move to another column';

  @override
  String get boardsPreviewMarkDoneAction => 'Mark done';

  @override
  String get boardsPreviewBlockTaskAction => 'Mark blocked';

  @override
  String get boardsPreviewActionPreviewOnly =>
      'Preview only — no task was changed.';

  @override
  String get boardsPreviewStatusNotStarted => 'Not started';

  @override
  String get boardsPreviewStatusInProgress => 'In progress';

  @override
  String get boardsPreviewStatusBlocked => 'Blocked';

  @override
  String get boardsPreviewStatusDone => 'Done';

  @override
  String boardsPreviewStatusSemantic(String status) {
    return 'Status: $status';
  }

  @override
  String get boardsPreviewBackendFedChip => 'Backend facade fed';

  @override
  String get boardsPreviewProviderBlockedChip => 'Provider runtime blocked';

  @override
  String get boardsPreviewStaticFixtureChip => 'Static fixture preview';

  @override
  String boardsPreviewProviderCapabilitySummary(String provider) {
    return 'Provider: $provider';
  }

  @override
  String get boardsPreviewCapabilityNonDragReady =>
      'Backend non-drag actions ready';

  @override
  String get boardsPreviewCapabilityNonDragBlocked =>
      'Backend non-drag actions blocked';

  @override
  String get boardsPreviewProviderInMemory => 'in-memory backend facade';

  @override
  String get boardsPreviewProviderVikunja => 'Vikunja adapter';

  @override
  String get boardsPreviewProviderOpenProject => 'OpenProject adapter';

  @override
  String get boardsPreviewProviderNextcloudDeck => 'Nextcloud Deck adapter';

  @override
  String get boardsPreviewProviderNone => 'no backend provider';

  @override
  String get boardsPreviewProviderUnavailable => 'backend unavailable';

  @override
  String get boardsPreviewProviderUnknown => 'unknown provider';

  @override
  String get boardsPreviewActionMoved =>
      'Task moved through the backend facade.';

  @override
  String get boardsPreviewActionCompleted =>
      'Task marked done through the backend facade.';

  @override
  String get boardsPreviewActionFailed =>
      'The backend facade could not save that Boards preview action.';

  @override
  String get boardsPreviewActionNoNextColumn =>
      'This task is already in the last preview column.';

  @override
  String get settingsAdminSetupTitle => 'Owner and admin setup';

  @override
  String get settingsAdminSetupDescription =>
      'Workspace owners and admins manage OIDC, realm, organization, and service endpoints here. Members and guests only see sign-in and product settings.';

  @override
  String get settingsAdminPermissionTitle => 'Admin controls unlocked';

  @override
  String settingsAdminPermissionDescription(String roles) {
    return 'Visible because your Weave roles are: $roles. Backend APIs remain the authority for every write.';
  }

  @override
  String settingsAdminPermissionSemantic(String roles) {
    return 'Admin controls unlocked. Visible because your Weave roles are: $roles. Backend APIs remain the authority for every write.';
  }

  @override
  String get settingsAdminBoundaryTitle => 'Workspace setup is admin-only';

  @override
  String get settingsAdminBoundaryDescription =>
      'OIDC, realm, organization, and service endpoint setup is handled by workspace owners or admins. Normal users can keep using Weave without Matrix, Nextcloud, or realm details.';

  @override
  String get settingsAdminPermissionLoading => 'Checking admin permissions…';

  @override
  String get chatContextCardTitle => 'Context for this workspace';

  @override
  String get chatContextCardDescription =>
      'Weave can prepare focused context from channels, decisions, and shared work when you ask for help. It does not show a database diagram or continuously read everything.';

  @override
  String get chatContextCardPolicy =>
      'Agents use scoped context on demand, show what context was used, and stay inside admin-defined boundaries.';

  @override
  String get chatContextChannelHintTitle => 'Channel context';

  @override
  String get chatContextChannelHintDescription =>
      'Recent room signals can become a small context card for the current task.';

  @override
  String get chatContextEvidenceHintTitle => 'Decisions and evidence';

  @override
  String get chatContextEvidenceHintDescription =>
      'Decision notes and supporting links can be cited without exposing graph internals.';

  @override
  String get chatContextAgentHintTitle => 'Agent context packs';

  @override
  String get chatContextAgentHintDescription =>
      'Assistants receive only the scoped pack for the request, mention, or schedule.';

  @override
  String get firstRunAdminSetupTitle => 'Owner/admin setup responsibilities';

  @override
  String get firstRunAdminSetupDescription =>
      'Your role can administer workspace setup. Keep OIDC, realm, organization, invite, and service endpoint changes here or in Settings; normal users should only need one Weave sign-in.';

  @override
  String get agentCapabilityPolicyTitle => 'AI agent capability governance';

  @override
  String get agentCapabilityPolicyAdminDescription =>
      'Owners and admins decide which agent packages and connectors can be used. This preview stays off until permission, consent, and audit controls are connected.';

  @override
  String get agentCapabilityPolicyUserDescription =>
      'AI agent chats are not enabled for this workspace yet. You can keep using Weave normally; an owner or admin must turn this on first.';

  @override
  String get agentCapabilityPolicyFailClosedNotice =>
      'Agent capabilities are blocked until Weave can confirm your role and the workspace policy.';

  @override
  String get agentCapabilityPolicyManageDisabledButton =>
      'Management unavailable in this preview';

  @override
  String get agentCapabilityPolicyAskAdminHint =>
      'Need an agent for your team? Ask a workspace owner or admin to review agent capabilities when they are available.';

  @override
  String get agentCapabilityPolicyAdminStateHint =>
      'Current state: off by default. Future controls will require owner/admin review before users can start an agent.';

  @override
  String get agentCapabilityPersonalAssistantTitle => 'Personal assistant';

  @override
  String get agentCapabilityPersonalAssistantDescription =>
      'Will only use context you choose for a request, after your workspace enables the capability.';

  @override
  String get agentCapabilityChannelAgentTitle => 'Channel agent';

  @override
  String get agentCapabilityChannelAgentDescription =>
      'Requires an owner or admin to choose which channels, files, calendar items, or boards the agent may use.';

  @override
  String get agentCapabilityAvailabilityPreviewOnly => 'Preview only';

  @override
  String get agentCapabilityAvailabilityAdminSetupRequired =>
      'Admin setup required';

  @override
  String get agentCapabilityAvailabilityBlocked => 'Blocked';

  @override
  String get agentCapabilityPolicyErrorTitle =>
      'Agent capability policy is unavailable.';

  @override
  String get agentCapabilityPolicyLoading =>
      'Checking agent capability policy…';
}
