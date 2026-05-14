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
  String get semanticDeckIcon => 'Future boards preview';

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
  String get filesScreenTitle => 'Files';

  @override
  String get filesLoadingLabel => 'Loading files…';

  @override
  String get filesLoadingHint =>
      'Refreshing the current folder and checking what changed.';

  @override
  String get filesNextcloudTitle => 'Nextcloud';

  @override
  String get filesConnectButton => 'Connect Nextcloud';

  @override
  String get filesReconnectButton => 'Reconnect Nextcloud';

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
      'Connect Nextcloud to browse your files.';

  @override
  String get filesInvalidSessionMessage =>
      'Reconnect Nextcloud because the saved session is no longer valid.';

  @override
  String get filesMisconfiguredMessage =>
      'Configure a Nextcloud URL before connecting files.';

  @override
  String filesConnectionConnected(String accountLabel) {
    return 'Connected as $accountLabel';
  }

  @override
  String get filesConnectionDisconnected =>
      'No Nextcloud session is connected on this device.';

  @override
  String get filesConnectionInvalid =>
      'The saved Nextcloud session needs attention.';

  @override
  String get filesConnectionMisconfigured =>
      'Server setup is incomplete for Nextcloud files.';

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
      'Weave Release 1 focuses on messaging, files, and the server connection that holds them together.';

  @override
  String get settingsShellModulesTitle => 'Shell modules';

  @override
  String get settingsShellModulesDescription =>
      'Choose which workspace shell modules stay visible. Navigation remains available even when a module is hidden.';

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
  String get deckEmptyMessage => 'No boards in this future preview yet';

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
  String get settingsWorkspaceCalendarLabel => 'Calendar';

  @override
  String get calendarWorkspaceScopeTitle => 'Workspace calendar';

  @override
  String get calendarWorkspaceScopeDescription =>
      'This first Calendar slice uses the shared Weave workspace calendar. Private user calendars stay unavailable until the access model is implemented.';

  @override
  String calendarGenericScopeDescription(String scopeLabel) {
    return 'Events are shown from $scopeLabel.';
  }

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
  String calendarEventSemantic(String title, String startsAt, String endsAt) {
    return '$title, starts $startsAt, ends $endsAt';
  }

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
  String get boardsPreviewIconSemantic => 'Future boards preview';

  @override
  String get boardsPreviewBoundaryTitle => 'Future boards/tasks preview';

  @override
  String get boardsPreviewBoundaryDescription =>
      'This design preview shows the intended Weave-owned board model and accessible task movement alternatives. It is hidden from Release 1 navigation and is not connected to Vikunja, Deck, or another provider yet.';

  @override
  String get boardsPreviewBoundarySemantic =>
      'Future boards/tasks preview. Hidden from Release 1 navigation. Provider-neutral Weave model with keyboard and screen-reader alternatives; no provider is connected yet.';

  @override
  String get boardsPreviewPostReleaseChip => 'Post-Release-1';

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
}
