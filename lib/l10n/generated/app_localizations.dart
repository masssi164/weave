import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_de.dart';
import 'app_localizations_en.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'generated/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('de'),
  ];

  /// Application title shown in the app bar and system task switcher
  ///
  /// In en, this message translates to:
  /// **'Weave'**
  String get appTitle;

  /// Main heading on the welcome screen
  ///
  /// In en, this message translates to:
  /// **'Welcome to Weave'**
  String get welcomeTitle;

  /// Subtitle text below the welcome heading
  ///
  /// In en, this message translates to:
  /// **'Your unified collaboration hub for messaging, files, and secure self-hosted access.'**
  String get welcomeSubtitle;

  /// Label for the primary CTA on the welcome screen
  ///
  /// In en, this message translates to:
  /// **'Get Started'**
  String get continueButton;

  /// Title for the setup flow screen
  ///
  /// In en, this message translates to:
  /// **'Setup'**
  String get setupTitle;

  /// Title for the setup provider and issuer step
  ///
  /// In en, this message translates to:
  /// **'Connect Your Server'**
  String get setupProviderStepTitle;

  /// Description shown in the setup provider step
  ///
  /// In en, this message translates to:
  /// **'Choose your OIDC provider and enter the issuer URL for your self-hosted setup.'**
  String get setupProviderStepDescription;

  /// Title for the setup services step
  ///
  /// In en, this message translates to:
  /// **'Review Service Endpoints'**
  String get setupServicesStepTitle;

  /// Description shown in the setup services step
  ///
  /// In en, this message translates to:
  /// **'Weave derives Matrix, Nextcloud, and backend API URLs from the issuer host. Review and edit them before finishing setup.'**
  String get setupServicesStepDescription;

  /// Title for the language preference step
  ///
  /// In en, this message translates to:
  /// **'Your Language'**
  String get setupLanguageStepTitle;

  /// Description shown in the language step
  ///
  /// In en, this message translates to:
  /// **'Weave uses your device language. You can change it later in settings.'**
  String get setupLanguageStepDescription;

  /// Title for the confirmation step
  ///
  /// In en, this message translates to:
  /// **'You\'re All Set'**
  String get setupConfirmStepTitle;

  /// Description shown in the confirmation step
  ///
  /// In en, this message translates to:
  /// **'Tap Finish to start using Weave.'**
  String get setupConfirmStepDescription;

  /// Button to advance to the next setup step
  ///
  /// In en, this message translates to:
  /// **'Next'**
  String get setupNextButton;

  /// Button to complete setup
  ///
  /// In en, this message translates to:
  /// **'Finish'**
  String get setupFinishButton;

  /// Button to go back to the previous setup step
  ///
  /// In en, this message translates to:
  /// **'Back'**
  String get setupBackButton;

  /// Accessibility label for setup step progress
  ///
  /// In en, this message translates to:
  /// **'Step {current} of {total}'**
  String setupStepIndicator(int current, int total);

  /// Label for the Chat navigation destination
  ///
  /// In en, this message translates to:
  /// **'Chat'**
  String get navChat;

  /// Label for the Files navigation destination
  ///
  /// In en, this message translates to:
  /// **'Files'**
  String get navFiles;

  /// Label for the Calendar navigation destination
  ///
  /// In en, this message translates to:
  /// **'Calendar'**
  String get navCalendar;

  /// Label for the hidden legacy Deck route, now provider-neutral boards preview
  ///
  /// In en, this message translates to:
  /// **'Boards preview'**
  String get navDeck;

  /// Label for the Settings navigation destination
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get navSettings;

  /// Screen reader label for loading indicators
  ///
  /// In en, this message translates to:
  /// **'Loading…'**
  String get loadingLabel;

  /// Message shown while bootstrap state is resolving
  ///
  /// In en, this message translates to:
  /// **'Preparing Weave…'**
  String get bootstrapLoadingLabel;

  /// Supporting copy shown while the workspace bootstrap state is resolving
  ///
  /// In en, this message translates to:
  /// **'Checking your workspace services and getting the shell ready.'**
  String get bootstrapLoadingHint;

  /// Friendly error-state title shown when the app shell cannot finish bootstrap
  ///
  /// In en, this message translates to:
  /// **'We could not get Weave ready'**
  String get shellErrorTitle;

  /// Friendly recovery guidance shown when app shell bootstrap fails
  ///
  /// In en, this message translates to:
  /// **'Try again. If this keeps happening, check that your workspace services are reachable.'**
  String get shellErrorGuidance;

  /// Title for the compact recent activity card in the app shell
  ///
  /// In en, this message translates to:
  /// **'Recent activity'**
  String get shellRecentActivityTitle;

  /// Description for the recent activity card in the app shell
  ///
  /// In en, this message translates to:
  /// **'Quick links to recent rooms and file changes.'**
  String get shellRecentActivityDescription;

  /// Semantic label for the recent activity card
  ///
  /// In en, this message translates to:
  /// **'Recent activity quick links'**
  String get shellRecentActivitySemanticLabel;

  /// Section title for recent room quick links in the app shell
  ///
  /// In en, this message translates to:
  /// **'Rooms'**
  String get shellRecentRoomsTitle;

  /// Section title for recent file quick links in the app shell
  ///
  /// In en, this message translates to:
  /// **'Files'**
  String get shellRecentFilesTitle;

  /// Loading label for recent room quick links
  ///
  /// In en, this message translates to:
  /// **'Loading recent rooms…'**
  String get shellRecentRoomsLoading;

  /// Empty state for recent room quick links
  ///
  /// In en, this message translates to:
  /// **'No recent rooms yet.'**
  String get shellRecentRoomsEmpty;

  /// Unavailable/error state for recent room quick links
  ///
  /// In en, this message translates to:
  /// **'Recent rooms are unavailable until chat is connected.'**
  String get shellRecentRoomsUnavailable;

  /// Loading label for recent file quick links
  ///
  /// In en, this message translates to:
  /// **'Loading recent file changes…'**
  String get shellRecentFilesLoading;

  /// Empty state for recent file quick links
  ///
  /// In en, this message translates to:
  /// **'No recent file changes yet.'**
  String get shellRecentFilesEmpty;

  /// Error state for recent file quick links
  ///
  /// In en, this message translates to:
  /// **'Recent file changes could not be loaded.'**
  String get shellRecentFilesError;

  /// Unavailable state for recent file quick links
  ///
  /// In en, this message translates to:
  /// **'Recent files are unavailable until files are connected.'**
  String get shellRecentFilesUnavailable;

  /// Fallback recency hint for activity items without a timestamp
  ///
  /// In en, this message translates to:
  /// **'recent'**
  String get shellRecentActivityUnknownRecency;

  /// Recency hint for activity that just happened
  ///
  /// In en, this message translates to:
  /// **'now'**
  String get shellRecentActivityNow;

  /// Recency hint for activity from the past hour
  ///
  /// In en, this message translates to:
  /// **'{minutes}m ago'**
  String shellRecentActivityMinutesAgo(int minutes);

  /// Recency hint for activity from today
  ///
  /// In en, this message translates to:
  /// **'today'**
  String get shellRecentActivityToday;

  /// Recency hint for activity from yesterday
  ///
  /// In en, this message translates to:
  /// **'yesterday'**
  String get shellRecentActivityYesterday;

  /// Semantic label for a recent room quick link
  ///
  /// In en, this message translates to:
  /// **'Open room {roomName}. Latest activity: {preview}. {recency}.'**
  String shellRecentRoomItemSemantic(
    String roomName,
    String preview,
    String recency,
  );

  /// Semantic label for a recent file quick link
  ///
  /// In en, this message translates to:
  /// **'Open {itemType} {itemName} in {path}. Changed {recency}.'**
  String shellRecentFileItemSemantic(
    String itemType,
    String itemName,
    String path,
    String recency,
  );

  /// File activity type label for folders
  ///
  /// In en, this message translates to:
  /// **'folder'**
  String get shellRecentFileFolderType;

  /// File activity type label for files
  ///
  /// In en, this message translates to:
  /// **'file'**
  String get shellRecentFileFileType;

  /// Message shown when a list has no items
  ///
  /// In en, this message translates to:
  /// **'Nothing here yet'**
  String get emptyStateLabel;

  /// Message shown when an error occurs
  ///
  /// In en, this message translates to:
  /// **'Something went wrong'**
  String get errorStateLabel;

  /// Label for the retry action button
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retryButton;

  /// Semantic label for back navigation buttons
  ///
  /// In en, this message translates to:
  /// **'Go back'**
  String get semanticBackButton;

  /// Semantic label for close buttons
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get semanticCloseButton;

  /// Semantic label for the chat icon
  ///
  /// In en, this message translates to:
  /// **'Chat messages'**
  String get semanticChatIcon;

  /// Semantic label for the files icon
  ///
  /// In en, this message translates to:
  /// **'File browser'**
  String get semanticFilesIcon;

  /// Semantic label for the calendar icon
  ///
  /// In en, this message translates to:
  /// **'Calendar events'**
  String get semanticCalendarIcon;

  /// Semantic label for the hidden future boards preview icon
  ///
  /// In en, this message translates to:
  /// **'Boards preview'**
  String get semanticDeckIcon;

  /// Semantic label for the settings icon
  ///
  /// In en, this message translates to:
  /// **'Application settings'**
  String get semanticSettingsIcon;

  /// Semantic label for the Weave brand logo image
  ///
  /// In en, this message translates to:
  /// **'Weave logo'**
  String get semanticWeaveLogo;

  /// No description provided for @firstRunAppBarTitle.
  ///
  /// In en, this message translates to:
  /// **'First-run status'**
  String get firstRunAppBarTitle;

  /// No description provided for @firstRunLoadingLabel.
  ///
  /// In en, this message translates to:
  /// **'Checking your Weave workspace…'**
  String get firstRunLoadingLabel;

  /// No description provided for @firstRunLoadingHint.
  ///
  /// In en, this message translates to:
  /// **'Loading your profile, role, and module readiness from the Weave backend.'**
  String get firstRunLoadingHint;

  /// No description provided for @firstRunLoadFailure.
  ///
  /// In en, this message translates to:
  /// **'We could not load your first-run status from the Weave backend.'**
  String get firstRunLoadFailure;

  /// No description provided for @firstRunSignedOutMessage.
  ///
  /// In en, this message translates to:
  /// **'Sign in to view your Weave first-run status.'**
  String get firstRunSignedOutMessage;

  /// No description provided for @firstRunReadyTitle.
  ///
  /// In en, this message translates to:
  /// **'Your Weave workspace is ready'**
  String get firstRunReadyTitle;

  /// No description provided for @firstRunNeedsAttentionTitle.
  ///
  /// In en, this message translates to:
  /// **'Your Weave workspace is being prepared'**
  String get firstRunNeedsAttentionTitle;

  /// No description provided for @firstRunDescription.
  ///
  /// In en, this message translates to:
  /// **'You signed in once with Weave SSO. Weave is checking your profile and collaboration modules; no separate Matrix or Nextcloud credentials are needed.'**
  String get firstRunDescription;

  /// No description provided for @firstRunIdentitySectionTitle.
  ///
  /// In en, this message translates to:
  /// **'Your Weave identity'**
  String get firstRunIdentitySectionTitle;

  /// No description provided for @firstRunIdentitySectionDescription.
  ///
  /// In en, this message translates to:
  /// **'This profile and role come from the Weave backend contract after SSO.'**
  String get firstRunIdentitySectionDescription;

  /// No description provided for @firstRunDisplayNameLabel.
  ///
  /// In en, this message translates to:
  /// **'Name'**
  String get firstRunDisplayNameLabel;

  /// No description provided for @firstRunUsernameLabel.
  ///
  /// In en, this message translates to:
  /// **'Username'**
  String get firstRunUsernameLabel;

  /// No description provided for @firstRunEmailLabel.
  ///
  /// In en, this message translates to:
  /// **'Email'**
  String get firstRunEmailLabel;

  /// No description provided for @firstRunRoleLabel.
  ///
  /// In en, this message translates to:
  /// **'Role'**
  String get firstRunRoleLabel;

  /// No description provided for @firstRunInviteStatusLabel.
  ///
  /// In en, this message translates to:
  /// **'Invite'**
  String get firstRunInviteStatusLabel;

  /// No description provided for @firstRunModuleSectionTitle.
  ///
  /// In en, this message translates to:
  /// **'Module readiness'**
  String get firstRunModuleSectionTitle;

  /// No description provided for @firstRunProfileModuleTitle.
  ///
  /// In en, this message translates to:
  /// **'Profile'**
  String get firstRunProfileModuleTitle;

  /// No description provided for @firstRunChatModuleTitle.
  ///
  /// In en, this message translates to:
  /// **'Chat'**
  String get firstRunChatModuleTitle;

  /// No description provided for @firstRunFilesModuleTitle.
  ///
  /// In en, this message translates to:
  /// **'Files'**
  String get firstRunFilesModuleTitle;

  /// No description provided for @firstRunCalendarModuleTitle.
  ///
  /// In en, this message translates to:
  /// **'Calendar'**
  String get firstRunCalendarModuleTitle;

  /// No description provided for @firstRunStateReady.
  ///
  /// In en, this message translates to:
  /// **'Ready'**
  String get firstRunStateReady;

  /// No description provided for @firstRunStatePending.
  ///
  /// In en, this message translates to:
  /// **'Pending'**
  String get firstRunStatePending;

  /// No description provided for @firstRunStateUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Unavailable'**
  String get firstRunStateUnavailable;

  /// No description provided for @firstRunStateDegraded.
  ///
  /// In en, this message translates to:
  /// **'Degraded'**
  String get firstRunStateDegraded;

  /// No description provided for @firstRunStateActionNeeded.
  ///
  /// In en, this message translates to:
  /// **'Action needed'**
  String get firstRunStateActionNeeded;

  /// No description provided for @firstRunNextStepsTitle.
  ///
  /// In en, this message translates to:
  /// **'Next steps'**
  String get firstRunNextStepsTitle;

  /// No description provided for @firstRunRefreshButton.
  ///
  /// In en, this message translates to:
  /// **'Refresh status'**
  String get firstRunRefreshButton;

  /// No description provided for @firstRunContinueButton.
  ///
  /// In en, this message translates to:
  /// **'Continue to chat'**
  String get firstRunContinueButton;

  /// No description provided for @chatProvisioningReadyTitle.
  ///
  /// In en, this message translates to:
  /// **'Chat is ready'**
  String get chatProvisioningReadyTitle;

  /// No description provided for @chatProvisioningPendingTitle.
  ///
  /// In en, this message translates to:
  /// **'Chat rooms are still being prepared'**
  String get chatProvisioningPendingTitle;

  /// No description provided for @chatProvisioningDegradedTitle.
  ///
  /// In en, this message translates to:
  /// **'Chat is available with degraded setup'**
  String get chatProvisioningDegradedTitle;

  /// No description provided for @chatProvisioningActionNeededTitle.
  ///
  /// In en, this message translates to:
  /// **'Chat setup needs admin attention'**
  String get chatProvisioningActionNeededTitle;

  /// No description provided for @chatProvisioningRetryButton.
  ///
  /// In en, this message translates to:
  /// **'Retry status'**
  String get chatProvisioningRetryButton;

  /// Title for the chat screen app bar
  ///
  /// In en, this message translates to:
  /// **'Chat'**
  String get chatScreenTitle;

  /// Message shown while the chat room list is loading
  ///
  /// In en, this message translates to:
  /// **'Loading conversations…'**
  String get chatLoadingLabel;

  /// Supporting copy shown while the chat conversation list is loading
  ///
  /// In en, this message translates to:
  /// **'Gathering your latest rooms and recent conversation state.'**
  String get chatLoadingHint;

  /// Message shown while Matrix OAuth sign-in is in progress
  ///
  /// In en, this message translates to:
  /// **'Connecting to Matrix…'**
  String get chatConnectingLabel;

  /// Supporting copy shown while Matrix sign-in is connecting
  ///
  /// In en, this message translates to:
  /// **'We are opening your secure Matrix session and syncing the first room list.'**
  String get chatConnectingHint;

  /// Button label to start or retry Matrix sign-in
  ///
  /// In en, this message translates to:
  /// **'Connect Matrix'**
  String get chatConnectButton;

  /// Accessibility label for the progress indicator shown while the existing chat room list is refreshing
  ///
  /// In en, this message translates to:
  /// **'Refreshing chat rooms'**
  String get chatRefreshingRoomsLabel;

  /// Title for a chat notice shown when refresh failed but cached conversations remain visible
  ///
  /// In en, this message translates to:
  /// **'Showing last known rooms'**
  String get chatStaleRoomsTitle;

  /// Guidance for a chat notice shown when refresh failed but cached conversations remain visible
  ///
  /// In en, this message translates to:
  /// **'We could not refresh Matrix just now. Your room list is preserved so you can keep your place and retry when the connection is back.'**
  String get chatStaleRoomsGuidance;

  /// Button label for retrying a stale chat room list refresh
  ///
  /// In en, this message translates to:
  /// **'Refresh rooms'**
  String get chatStaleRoomsRetryButton;

  /// Title for the files screen app bar
  ///
  /// In en, this message translates to:
  /// **'Files'**
  String get filesScreenTitle;

  /// Message shown while the Files screen is loading the current directory
  ///
  /// In en, this message translates to:
  /// **'Loading files…'**
  String get filesLoadingLabel;

  /// Supporting copy shown while the Files screen is loading the current directory
  ///
  /// In en, this message translates to:
  /// **'Refreshing the current folder and checking what changed.'**
  String get filesLoadingHint;

  /// Title for a Files notice shown when refresh failed but the previous directory listing remains visible
  ///
  /// In en, this message translates to:
  /// **'Showing last known folder'**
  String get filesStaleDirectoryTitle;

  /// Guidance for a Files notice shown when refresh failed but cached folder contents remain visible
  ///
  /// In en, this message translates to:
  /// **'We could not refresh Files just now. The last folder listing stays visible so you can keep your place and retry when the connection is back.'**
  String get filesStaleDirectoryGuidance;

  /// Button label for retrying a stale Files directory refresh
  ///
  /// In en, this message translates to:
  /// **'Refresh folder'**
  String get filesStaleDirectoryRetryButton;

  /// Section title for the Nextcloud files connection card
  ///
  /// In en, this message translates to:
  /// **'Nextcloud'**
  String get filesNextcloudTitle;

  /// Button label used to start the Nextcloud connection flow
  ///
  /// In en, this message translates to:
  /// **'Connect Nextcloud'**
  String get filesConnectButton;

  /// Button label used to reconnect an invalid Nextcloud session
  ///
  /// In en, this message translates to:
  /// **'Reconnect Nextcloud'**
  String get filesReconnectButton;

  /// Button label used to disconnect the saved Nextcloud session
  ///
  /// In en, this message translates to:
  /// **'Disconnect'**
  String get filesDisconnectButton;

  /// Button label used to refresh the current Nextcloud directory
  ///
  /// In en, this message translates to:
  /// **'Refresh'**
  String get filesRefreshButton;

  /// Button label used to open the parent Nextcloud directory
  ///
  /// In en, this message translates to:
  /// **'Up'**
  String get filesUpButton;

  /// Label for the root breadcrumb in the files browser
  ///
  /// In en, this message translates to:
  /// **'Root'**
  String get filesRootBreadcrumb;

  /// Semantic label for a breadcrumb that opens a folder in the files browser
  ///
  /// In en, this message translates to:
  /// **'Open folder: {name}'**
  String filesOpenFolderSemantic(String name);

  /// Semantic label for the current breadcrumb in the files browser
  ///
  /// In en, this message translates to:
  /// **'Current folder: {name}'**
  String filesCurrentFolderSemantic(String name);

  /// Summary for the current directory contents
  ///
  /// In en, this message translates to:
  /// **'{folderCount, plural, =0{No folders} one{1 folder} other{{folderCount} folders}} • {fileCount, plural, =0{no files} one{1 file} other{{fileCount} files}}'**
  String filesDirectorySummary(int folderCount, int fileCount);

  /// Message shown when the Files screen is disconnected from Nextcloud
  ///
  /// In en, this message translates to:
  /// **'Connect Nextcloud to browse your files.'**
  String get filesDisconnectedMessage;

  /// Message shown when the saved Nextcloud session is no longer valid
  ///
  /// In en, this message translates to:
  /// **'Reconnect Nextcloud because the saved session is no longer valid.'**
  String get filesInvalidSessionMessage;

  /// Message shown when the Files feature is missing a valid Nextcloud base URL
  ///
  /// In en, this message translates to:
  /// **'Configure a Nextcloud URL before connecting files.'**
  String get filesMisconfiguredMessage;

  /// Status message shown when the Files feature is connected to Nextcloud
  ///
  /// In en, this message translates to:
  /// **'Connected as {accountLabel}'**
  String filesConnectionConnected(String accountLabel);

  /// Status message shown when no Nextcloud session is saved locally
  ///
  /// In en, this message translates to:
  /// **'No Nextcloud session is connected on this device.'**
  String get filesConnectionDisconnected;

  /// Status message shown when the saved Nextcloud session is invalid
  ///
  /// In en, this message translates to:
  /// **'The saved Nextcloud session needs attention.'**
  String get filesConnectionInvalid;

  /// Status message shown when Nextcloud server setup is incomplete
  ///
  /// In en, this message translates to:
  /// **'Server setup is incomplete for Nextcloud files.'**
  String get filesConnectionMisconfigured;

  /// Semantic label for the action that opens the parent Nextcloud directory
  ///
  /// In en, this message translates to:
  /// **'Open parent folder'**
  String get filesOpenParentSemantic;

  /// Semantic label for the action that refreshes the current Nextcloud directory
  ///
  /// In en, this message translates to:
  /// **'Refresh the current folder'**
  String get filesRefreshCurrentFolderSemantic;

  /// Button label for uploading a file to the current folder
  ///
  /// In en, this message translates to:
  /// **'Upload'**
  String get filesUploadButton;

  /// Semantic label for the action that uploads a file to the current folder
  ///
  /// In en, this message translates to:
  /// **'Upload a file to the current folder'**
  String get filesUploadCurrentFolderSemantic;

  /// Button label for creating a folder in the current files directory
  ///
  /// In en, this message translates to:
  /// **'New folder'**
  String get filesCreateFolderButton;

  /// Semantic label for the action that creates a folder in the current directory
  ///
  /// In en, this message translates to:
  /// **'Create a folder in the current folder'**
  String get filesCreateFolderCurrentFolderSemantic;

  /// Title for the create-folder dialog
  ///
  /// In en, this message translates to:
  /// **'Create folder'**
  String get filesCreateFolderDialogTitle;

  /// Input label for a new folder name
  ///
  /// In en, this message translates to:
  /// **'Folder name'**
  String get filesCreateFolderNameLabel;

  /// Input hint for a new folder name
  ///
  /// In en, this message translates to:
  /// **'e.g. Project docs'**
  String get filesCreateFolderNameHint;

  /// Button label to confirm folder creation
  ///
  /// In en, this message translates to:
  /// **'Create'**
  String get filesCreateFolderConfirmButton;

  /// Button label to cancel a files action
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get filesCancelButton;

  /// Button label to confirm deletion of a file or folder
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get filesDeleteButton;

  /// Tooltip/semantic label for exporting a file to native platform file surfaces
  ///
  /// In en, this message translates to:
  /// **'Export {name} to native files'**
  String filesExportEntrySemantic(String name);

  /// Status message while a file is exported
  ///
  /// In en, this message translates to:
  /// **'Exporting {name}…'**
  String filesExportProgressMessage(String name);

  /// Status message while an unnamed file is exported
  ///
  /// In en, this message translates to:
  /// **'Exporting file…'**
  String get filesExportProgressUnknownMessage;

  /// Status message after a file is exported to a native file surface
  ///
  /// In en, this message translates to:
  /// **'Exported {name} to {destination}.'**
  String filesExportCompletedMessage(String name, String destination);

  /// Status message after an unnamed file is exported
  ///
  /// In en, this message translates to:
  /// **'Exported file to native files.'**
  String get filesExportCompletedUnknownMessage;

  /// Fallback destination label when the platform does not expose an export path
  ///
  /// In en, this message translates to:
  /// **'a user-visible files location'**
  String get filesExportUserVisibleFallback;

  /// Semantic label for deleting a file or folder
  ///
  /// In en, this message translates to:
  /// **'Delete {name}'**
  String filesDeleteEntrySemantic(String name);

  /// Title for the delete confirmation dialog
  ///
  /// In en, this message translates to:
  /// **'Delete {name}?'**
  String filesDeleteEntryDialogTitle(String name);

  /// Warning shown before deleting a file or folder
  ///
  /// In en, this message translates to:
  /// **'This removes it from Weave files for everyone with access. This cannot be undone.'**
  String get filesDeleteEntryDialogMessage;

  /// Status shown while creating a folder when no name is available
  ///
  /// In en, this message translates to:
  /// **'Creating folder…'**
  String get filesCreateFolderProgressUnknownMessage;

  /// Status shown while creating a folder
  ///
  /// In en, this message translates to:
  /// **'Creating folder {folderName}…'**
  String filesCreateFolderProgressMessage(String folderName);

  /// Status shown after creating a folder when no name is available
  ///
  /// In en, this message translates to:
  /// **'Folder created.'**
  String get filesCreateFolderCompletedUnknownMessage;

  /// Status shown after creating a folder
  ///
  /// In en, this message translates to:
  /// **'Created folder {folderName}.'**
  String filesCreateFolderCompletedMessage(String folderName);

  /// Status shown while deleting a file or folder when no name is available
  ///
  /// In en, this message translates to:
  /// **'Deleting item…'**
  String get filesDeleteProgressUnknownMessage;

  /// Status shown while deleting a file or folder
  ///
  /// In en, this message translates to:
  /// **'Deleting {name}…'**
  String filesDeleteProgressMessage(String name);

  /// Status shown after deleting a file or folder when no name is available
  ///
  /// In en, this message translates to:
  /// **'Item deleted.'**
  String get filesDeleteCompletedUnknownMessage;

  /// Status shown after deleting a file or folder
  ///
  /// In en, this message translates to:
  /// **'Deleted {name}.'**
  String filesDeleteCompletedMessage(String name);

  /// Fallback status shown when a file operation fails
  ///
  /// In en, this message translates to:
  /// **'File action failed.'**
  String get filesEntryActionFailedMessage;

  /// Status shown while the native file picker is open
  ///
  /// In en, this message translates to:
  /// **'Choose a file to upload…'**
  String get filesUploadPickingMessage;

  /// Status shown while uploading when no filename is available
  ///
  /// In en, this message translates to:
  /// **'Uploading file…'**
  String get filesUploadProgressUnknownMessage;

  /// Status shown while uploading when total progress is unavailable
  ///
  /// In en, this message translates to:
  /// **'Uploading {fileName}…'**
  String filesUploadProgressIndeterminateMessage(String fileName);

  /// Status shown while a file upload is in progress
  ///
  /// In en, this message translates to:
  /// **'Uploading {fileName}: {percent}%'**
  String filesUploadProgressMessage(String fileName, int percent);

  /// Semantic label for file upload progress
  ///
  /// In en, this message translates to:
  /// **'Upload progress for {fileName}: {percent} percent'**
  String filesUploadProgressSemantic(String fileName, int percent);

  /// Status shown after an upload completes when no filename is available
  ///
  /// In en, this message translates to:
  /// **'Upload complete.'**
  String get filesUploadCompletedUnknownMessage;

  /// Status shown after a file upload completes
  ///
  /// In en, this message translates to:
  /// **'Uploaded {fileName}.'**
  String filesUploadCompletedMessage(String fileName);

  /// Status shown after an upload fails when no filename is available
  ///
  /// In en, this message translates to:
  /// **'Upload failed.'**
  String get filesUploadFailedUnknownMessage;

  /// Status shown after a file upload fails
  ///
  /// In en, this message translates to:
  /// **'Upload failed for {fileName}.'**
  String filesUploadFailedMessage(String fileName);

  /// Semantic label for a folder row in the Files list
  ///
  /// In en, this message translates to:
  /// **'{name}, folder'**
  String filesFolderSemantic(String name);

  /// Semantic label for a file row in the Files list
  ///
  /// In en, this message translates to:
  /// **'{name}, file'**
  String filesFileSemantic(String name);

  /// Title for the calendar screen app bar
  ///
  /// In en, this message translates to:
  /// **'Calendar'**
  String get calendarScreenTitle;

  /// Compatibility title for the hidden legacy Deck route
  ///
  /// In en, this message translates to:
  /// **'Boards preview'**
  String get deckScreenTitle;

  /// Title for the settings screen app bar
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settingsScreenTitle;

  /// Subtle branded copy shown in the settings header card
  ///
  /// In en, this message translates to:
  /// **'Weave focuses on accessible, data-sovereign collaboration: chat, files, shared calendars, E2EE architecture, and boards behind clear gates.'**
  String get settingsBrandSectionDescription;

  /// Section title for user-configurable shell module visibility
  ///
  /// In en, this message translates to:
  /// **'Shell modules'**
  String get settingsShellModulesTitle;

  /// Description for shell module visibility settings
  ///
  /// In en, this message translates to:
  /// **'Choose which workspace shell modules stay visible. Navigation remains available even when a module is hidden.'**
  String get settingsShellModulesDescription;

  /// Switch title for the workspace status shell module visibility preference
  ///
  /// In en, this message translates to:
  /// **'Workspace status summary'**
  String get settingsShellWorkspaceStatusToggleTitle;

  /// Switch description for the workspace status shell module visibility preference
  ///
  /// In en, this message translates to:
  /// **'Show service readiness and recovery shortcuts above the bottom navigation.'**
  String get settingsShellWorkspaceStatusToggleDescription;

  /// Tooltip for moving a shell module earlier in the shell order
  ///
  /// In en, this message translates to:
  /// **'Move {moduleName} up'**
  String settingsShellMoveModuleUp(String moduleName);

  /// Tooltip for moving a shell module later in the shell order
  ///
  /// In en, this message translates to:
  /// **'Move {moduleName} down'**
  String settingsShellMoveModuleDown(String moduleName);

  /// Switch title for the recent activity shell module visibility preference
  ///
  /// In en, this message translates to:
  /// **'Recent activity quick links'**
  String get settingsShellRecentActivityToggleTitle;

  /// Switch description for the recent activity shell module visibility preference
  ///
  /// In en, this message translates to:
  /// **'Show recent rooms and file changes above the bottom navigation.'**
  String get settingsShellRecentActivityToggleDescription;

  /// Loading state for shell module visibility settings
  ///
  /// In en, this message translates to:
  /// **'Loading shell module preferences…'**
  String get settingsShellModulesLoading;

  /// Error shown when shell module preferences cannot load or save
  ///
  /// In en, this message translates to:
  /// **'Shell module preferences could not be saved. Try changing the setting again.'**
  String get settingsShellModulesError;

  /// Section title for Matrix security status and actions in settings
  ///
  /// In en, this message translates to:
  /// **'Matrix security'**
  String get chatSecuritySectionTitle;

  /// Description shown above the Matrix security section in settings
  ///
  /// In en, this message translates to:
  /// **'Weave only treats Matrix encryption as healthy when secret storage, cross-signing, recovery, and device trust are all in place.'**
  String get chatSecuritySectionDescription;

  /// Title shown when the app displays the generated Matrix recovery key
  ///
  /// In en, this message translates to:
  /// **'Save this Matrix recovery key now'**
  String get chatSecurityRecoveryKeyTitle;

  /// Warning text shown alongside the generated Matrix recovery key
  ///
  /// In en, this message translates to:
  /// **'Weave does not rely on app-only storage for this key because secure storage can disappear after reinstall, device replacement, or some platform restores. Keep it in your password manager or another secure place.'**
  String get chatSecurityRecoveryKeyDescription;

  /// Title for the in-chat warning banner about Matrix security
  ///
  /// In en, this message translates to:
  /// **'Matrix security needs attention'**
  String get chatSecurityBannerTitle;

  /// Banner body when Matrix encryption setup has not been completed yet
  ///
  /// In en, this message translates to:
  /// **'Encrypted Matrix rooms are available, but this account still needs initial security setup.'**
  String get chatSecurityBannerSetupMessage;

  /// Banner body when Matrix recovery is required on the current device
  ///
  /// In en, this message translates to:
  /// **'This device needs your Matrix recovery key before older encrypted messages can be trusted again.'**
  String get chatSecurityBannerRecoveryMessage;

  /// Banner body when Matrix device or account verification still needs attention
  ///
  /// In en, this message translates to:
  /// **'This device or account is not fully verified yet. Compare security emoji with another signed-in Matrix device.'**
  String get chatSecurityBannerVerificationMessage;

  /// Banner body when Matrix key backup has not been configured
  ///
  /// In en, this message translates to:
  /// **'Matrix key backup is still missing. Set it up before relying on encrypted chat recovery.'**
  String get chatSecurityBannerMissingBackupMessage;

  /// Button label that opens settings from the Matrix security banner
  ///
  /// In en, this message translates to:
  /// **'Open security settings'**
  String get chatSecurityOpenSettingsButton;

  /// Title for the Matrix security setup status card
  ///
  /// In en, this message translates to:
  /// **'Setup'**
  String get chatSecuritySetupCardTitle;

  /// Title for the Matrix security current device status card
  ///
  /// In en, this message translates to:
  /// **'Current device'**
  String get chatSecurityCurrentDeviceCardTitle;

  /// Title for the Matrix security recovery status card
  ///
  /// In en, this message translates to:
  /// **'Recovery and key backup'**
  String get chatSecurityRecoveryCardTitle;

  /// Description in the Matrix recovery card
  ///
  /// In en, this message translates to:
  /// **'The recovery key is needed when this device is replaced, reinstalled, or loses local crypto secrets.'**
  String get chatSecurityRecoveryCardBody;

  /// Title for the Matrix encrypted rooms status card
  ///
  /// In en, this message translates to:
  /// **'Encrypted rooms'**
  String get chatSecurityEncryptedRoomsCardTitle;

  /// Description when encrypted rooms exist on the Matrix account
  ///
  /// In en, this message translates to:
  /// **'Encrypted rooms already exist on this account. Warnings stay visible until trust and recovery are healthy.'**
  String get chatSecurityEncryptedRoomsCardBodyExisting;

  /// Description when no encrypted rooms are known yet
  ///
  /// In en, this message translates to:
  /// **'No encrypted rooms are known yet, but the account security state is still tracked here.'**
  String get chatSecurityEncryptedRoomsCardBodyNone;

  /// Status label when Matrix is not connected
  ///
  /// In en, this message translates to:
  /// **'Matrix not connected'**
  String get chatSecurityStatusSignedOut;

  /// Status label when Matrix encrypted chat setup is required
  ///
  /// In en, this message translates to:
  /// **'Setup required'**
  String get chatSecurityStatusSetupRequired;

  /// Status label when Matrix encrypted chat setup is only partially complete
  ///
  /// In en, this message translates to:
  /// **'Setup incomplete'**
  String get chatSecurityStatusSetupIncomplete;

  /// Status label when Matrix recovery is required
  ///
  /// In en, this message translates to:
  /// **'Recovery required'**
  String get chatSecurityStatusRecoveryRequired;

  /// Status label when Matrix security is healthy
  ///
  /// In en, this message translates to:
  /// **'Healthy'**
  String get chatSecurityStatusHealthy;

  /// Generic status label when Matrix security data is unavailable
  ///
  /// In en, this message translates to:
  /// **'Unavailable'**
  String get chatSecurityStatusUnavailable;

  /// Status label for a verified Matrix device
  ///
  /// In en, this message translates to:
  /// **'Verified'**
  String get chatSecurityStatusVerified;

  /// Status label for an unverified Matrix device
  ///
  /// In en, this message translates to:
  /// **'Unverified'**
  String get chatSecurityStatusUnverified;

  /// Status label for a blocked Matrix device
  ///
  /// In en, this message translates to:
  /// **'Blocked'**
  String get chatSecurityStatusBlocked;

  /// Status label when Matrix key backup is missing
  ///
  /// In en, this message translates to:
  /// **'Missing'**
  String get chatSecurityStatusMissing;

  /// Status label when Matrix recovery material needs to be reconnected on the device
  ///
  /// In en, this message translates to:
  /// **'Needs reconnect'**
  String get chatSecurityStatusNeedsReconnect;

  /// Status label when a Matrix security feature is ready
  ///
  /// In en, this message translates to:
  /// **'Ready'**
  String get chatSecurityStatusReady;

  /// Status label when there are no encrypted Matrix rooms yet
  ///
  /// In en, this message translates to:
  /// **'No encrypted rooms yet'**
  String get chatSecurityEncryptedRoomsStatusNone;

  /// Status label when encrypted Matrix rooms need user attention
  ///
  /// In en, this message translates to:
  /// **'Encrypted rooms need attention'**
  String get chatSecurityEncryptedRoomsStatusAttention;

  /// Setup card description when Matrix is not connected
  ///
  /// In en, this message translates to:
  /// **'Open Chat and connect Matrix before managing encryption.'**
  String get chatSecuritySetupDescriptionSignedOut;

  /// Setup card description when Matrix encryption has not been initialized
  ///
  /// In en, this message translates to:
  /// **'Set up secret storage, cross-signing, and online key backup before trusting encrypted rooms.'**
  String get chatSecuritySetupDescriptionNotInitialized;

  /// Setup card description when Matrix encryption setup is incomplete
  ///
  /// In en, this message translates to:
  /// **'Some encryption parts exist, but recovery or cross-signing is still incomplete.'**
  String get chatSecuritySetupDescriptionPartiallyInitialized;

  /// Setup card description when Matrix recovery is required
  ///
  /// In en, this message translates to:
  /// **'This account was set up before, but this device needs the recovery key or passphrase to reconnect safely.'**
  String get chatSecuritySetupDescriptionRecoveryRequired;

  /// Setup card description when Matrix setup is healthy
  ///
  /// In en, this message translates to:
  /// **'This device can use the current Matrix crypto identity and recovery setup.'**
  String get chatSecuritySetupDescriptionReady;

  /// Setup card description when Matrix encryption is unavailable
  ///
  /// In en, this message translates to:
  /// **'Matrix encryption is not available on this platform.'**
  String get chatSecuritySetupDescriptionUnavailable;

  /// Current device card description when the device is verified
  ///
  /// In en, this message translates to:
  /// **'Another trusted Matrix device has verified this session.'**
  String get chatSecurityCurrentDeviceDescriptionVerified;

  /// Current device card description when the device is unverified
  ///
  /// In en, this message translates to:
  /// **'Compare security emoji or numbers with another signed-in Matrix device.'**
  String get chatSecurityCurrentDeviceDescriptionUnverified;

  /// Current device card description when the device is blocked
  ///
  /// In en, this message translates to:
  /// **'This device is blocked or its trust chain is broken.'**
  String get chatSecurityCurrentDeviceDescriptionBlocked;

  /// Current device card description when the device key is unavailable
  ///
  /// In en, this message translates to:
  /// **'The current device key is not available yet.'**
  String get chatSecurityCurrentDeviceDescriptionUnavailable;

  /// Message shown instead of actions when Matrix is not connected
  ///
  /// In en, this message translates to:
  /// **'Matrix security actions unlock after the Matrix session is connected.'**
  String get chatSecurityActionsUnavailableSignedOut;

  /// Button label shown while a Matrix security action is running
  ///
  /// In en, this message translates to:
  /// **'Working…'**
  String get chatSecurityWorkingButton;

  /// Button label to initialize Matrix encrypted chat
  ///
  /// In en, this message translates to:
  /// **'Set up encrypted chat'**
  String get chatSecuritySetupButton;

  /// Button label to reconnect Matrix encrypted chat with recovery material
  ///
  /// In en, this message translates to:
  /// **'Reconnect with recovery key'**
  String get chatSecurityReconnectButton;

  /// Button label to start Matrix device verification
  ///
  /// In en, this message translates to:
  /// **'Verify this device'**
  String get chatSecurityVerifyDeviceButton;

  /// Button label to accept a Matrix verification request
  ///
  /// In en, this message translates to:
  /// **'Accept verification'**
  String get chatSecurityAcceptVerificationButton;

  /// Button label to decline a Matrix verification request
  ///
  /// In en, this message translates to:
  /// **'Decline'**
  String get chatSecurityDeclineVerificationButton;

  /// Button label to continue Matrix verification with SAS emoji
  ///
  /// In en, this message translates to:
  /// **'Compare security emoji'**
  String get chatSecurityCompareEmojiButton;

  /// Button label to continue Matrix verification by unlocking existing secret storage
  ///
  /// In en, this message translates to:
  /// **'Continue verification with recovery key'**
  String get chatSecurityUnlockVerificationButton;

  /// Button label confirming the Matrix SAS emoji match
  ///
  /// In en, this message translates to:
  /// **'Emoji match'**
  String get chatSecurityEmojiMatchButton;

  /// Button label when the Matrix SAS emoji do not match
  ///
  /// In en, this message translates to:
  /// **'They do not match'**
  String get chatSecurityEmojiMismatchButton;

  /// Button label to dismiss a Matrix verification result
  ///
  /// In en, this message translates to:
  /// **'Dismiss'**
  String get chatSecurityDismissButton;

  /// Message shown when there are no Matrix security actions to take
  ///
  /// In en, this message translates to:
  /// **'No action is needed right now.'**
  String get chatSecurityNoActionNeeded;

  /// Fallback error shown when Matrix security actions fail without a more specific message
  ///
  /// In en, this message translates to:
  /// **'Unable to update Matrix security right now.'**
  String get chatSecurityGenericFailure;

  /// Feedback message shown after encrypted chat setup completes
  ///
  /// In en, this message translates to:
  /// **'Encrypted chat is now set up. Save your recovery key before closing this screen.'**
  String get chatSecurityNoticeSetupComplete;

  /// Feedback message shown after Matrix recovery succeeds
  ///
  /// In en, this message translates to:
  /// **'Encrypted chat was reconnected for this device.'**
  String get chatSecurityNoticeRecoveryRestored;

  /// Feedback message shown after starting Matrix device verification
  ///
  /// In en, this message translates to:
  /// **'Verification request sent. Continue on your other Matrix device.'**
  String get chatSecurityNoticeVerificationRequestSent;

  /// Feedback message shown after cancelling Matrix verification
  ///
  /// In en, this message translates to:
  /// **'Verification cancelled.'**
  String get chatSecurityNoticeVerificationCancelled;

  /// Message shown for an incoming Matrix verification request
  ///
  /// In en, this message translates to:
  /// **'Another device wants to verify this session.'**
  String get chatSecurityVerificationIncomingMessage;

  /// Message shown when the user should choose a Matrix verification method
  ///
  /// In en, this message translates to:
  /// **'Choose a verification method to compare both devices.'**
  String get chatSecurityVerificationChooseMethodMessage;

  /// Message shown while Matrix verification waits for the other device
  ///
  /// In en, this message translates to:
  /// **'Waiting for the other device to continue verification.'**
  String get chatSecurityVerificationWaitingMessage;

  /// Message shown when Matrix verification needs access to secret storage before continuing
  ///
  /// In en, this message translates to:
  /// **'This verification needs your Matrix recovery key or passphrase before it can continue.'**
  String get chatSecurityVerificationRecoveryMessage;

  /// Help text shown when Matrix verification needs secret storage access
  ///
  /// In en, this message translates to:
  /// **'Unlock the existing Matrix secret storage to let this device complete verification safely.'**
  String get chatSecurityVerificationRecoveryHelp;

  /// Message shown while Matrix SAS values should be compared
  ///
  /// In en, this message translates to:
  /// **'Compare the security emoji or numbers on both devices.'**
  String get chatSecurityVerificationCompareMessage;

  /// Message shown after Matrix verification succeeds
  ///
  /// In en, this message translates to:
  /// **'This device is now verified.'**
  String get chatSecurityVerificationDoneMessage;

  /// Message shown after Matrix verification is cancelled
  ///
  /// In en, this message translates to:
  /// **'Verification was cancelled before it finished.'**
  String get chatSecurityVerificationCancelledMessage;

  /// Message shown after Matrix verification fails
  ///
  /// In en, this message translates to:
  /// **'Verification could not be completed.'**
  String get chatSecurityVerificationFailedMessage;

  /// Dialog title for initializing Matrix encrypted chat
  ///
  /// In en, this message translates to:
  /// **'Set up encrypted chat'**
  String get chatSecuritySetupDialogTitle;

  /// Dialog description for the Matrix encrypted chat setup flow
  ///
  /// In en, this message translates to:
  /// **'You can optionally protect the Matrix recovery key with a memorable passphrase. Leave this blank to use a generated recovery key instead.'**
  String get chatSecuritySetupDialogDescription;

  /// Field label for an optional Matrix recovery passphrase
  ///
  /// In en, this message translates to:
  /// **'Optional passphrase'**
  String get chatSecurityOptionalPassphraseLabel;

  /// Generic cancel button for Matrix security dialogs
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get chatSecurityDialogCancelButton;

  /// Continue button for the Matrix security setup dialog
  ///
  /// In en, this message translates to:
  /// **'Continue'**
  String get chatSecurityDialogContinueButton;

  /// Dialog title for reconnecting Matrix encrypted chat
  ///
  /// In en, this message translates to:
  /// **'Reconnect encrypted chat'**
  String get chatSecurityRestoreDialogTitle;

  /// Dialog description for reconnecting Matrix encrypted chat
  ///
  /// In en, this message translates to:
  /// **'Enter the Matrix recovery key or recovery passphrase that was created when encrypted chat was first set up.'**
  String get chatSecurityRestoreDialogDescription;

  /// Dialog title for continuing Matrix verification with recovery material
  ///
  /// In en, this message translates to:
  /// **'Continue verification'**
  String get chatSecurityVerificationRecoveryDialogTitle;

  /// Dialog description for continuing Matrix verification with recovery material
  ///
  /// In en, this message translates to:
  /// **'Enter your Matrix recovery key or passphrase to continue this verification. This unlocks the secrets needed for verification rather than reconnecting the whole account.'**
  String get chatSecurityVerificationRecoveryDialogDescription;

  /// Field label for Matrix recovery material
  ///
  /// In en, this message translates to:
  /// **'Recovery key or passphrase'**
  String get chatSecurityRecoveryKeyFieldLabel;

  /// Button label confirming the Matrix recovery key was saved
  ///
  /// In en, this message translates to:
  /// **'I saved it'**
  String get chatSecurityRecoveryKeyDismissButton;

  /// Accessibility label prefix for Matrix SAS emoji
  ///
  /// In en, this message translates to:
  /// **'Security emoji'**
  String get chatSecurityEmojiSummaryLabel;

  /// Accessibility label for Matrix SAS numbers
  ///
  /// In en, this message translates to:
  /// **'Security numbers {value}'**
  String chatSecurityNumbersSummaryLabel(String value);

  /// Settings section title for feature-flagged feature-gated product surfaces
  ///
  /// In en, this message translates to:
  /// **'Preview surfaces'**
  String get settingsPreviewSurfacesTitle;

  /// Description for feature-flagged feature-gated surfaces
  ///
  /// In en, this message translates to:
  /// **'These feature-gated surfaces stay honest about what is active, blocked, or still waiting for backend contracts.'**
  String get settingsPreviewSurfacesDescription;

  /// Feature-flagged Guest Portal preview title
  ///
  /// In en, this message translates to:
  /// **'Guest Portal'**
  String get settingsGuestPortalPreviewTitle;

  /// Feature-flagged Guest Portal preview description
  ///
  /// In en, this message translates to:
  /// **'Guest invitations and constrained access will appear here without exposing member-only affordances.'**
  String get settingsGuestPortalPreviewDescription;

  /// Feature-flagged interop admin status preview title
  ///
  /// In en, this message translates to:
  /// **'External connections admin status'**
  String get settingsInteropAdminPreviewTitle;

  /// Feature-flagged interop admin preview description
  ///
  /// In en, this message translates to:
  /// **'External provider status will explain data movement and consent; provider secrets are never collected in this client.'**
  String get settingsInteropAdminPreviewDescription;

  /// Feature-flagged migration dry-run preview title
  ///
  /// In en, this message translates to:
  /// **'Migration dry-run report'**
  String get settingsMigrationDryRunPreviewTitle;

  /// Feature-flagged migration dry-run preview description
  ///
  /// In en, this message translates to:
  /// **'Admins will be able to review inventory, risks, scopes, and mappings before any import starts.'**
  String get settingsMigrationDryRunPreviewDescription;

  /// Section title for server configuration in settings
  ///
  /// In en, this message translates to:
  /// **'Server Configuration'**
  String get settingsServerConfigurationTitle;

  /// Section title for the shared workspace readiness summary in settings
  ///
  /// In en, this message translates to:
  /// **'Workspace Readiness'**
  String get settingsWorkspaceReadinessTitle;

  /// Description for the shared workspace readiness summary in settings
  ///
  /// In en, this message translates to:
  /// **'Shell access is tracked separately from each service connection so Weave can show degraded integrations honestly.'**
  String get settingsWorkspaceReadinessDescription;

  /// Error shown when the Weave backend API cannot be reached from the app
  ///
  /// In en, this message translates to:
  /// **'Backend API is unreachable. Check that the Weave stack is running and the configured backend URL is correct.'**
  String get settingsWorkspaceBackendUnreachable;

  /// Error shown when the Weave backend API rejects the current session
  ///
  /// In en, this message translates to:
  /// **'Backend API rejected the current session. Sign in again before retrying.'**
  String get settingsWorkspaceBackendUnauthorized;

  /// Error shown when the Weave backend API returns an unexpected error
  ///
  /// In en, this message translates to:
  /// **'Backend API returned an unexpected response. Check the Weave stack logs before retrying.'**
  String get settingsWorkspaceBackendServerError;

  /// Summary shown when shell access and mapped services are all ready
  ///
  /// In en, this message translates to:
  /// **'Shell access and the mapped services are ready.'**
  String get settingsWorkspaceSummaryConnected;

  /// Summary shown when shell access is available but one or more services are degraded
  ///
  /// In en, this message translates to:
  /// **'Shell access is ready, but one or more services still need attention.'**
  String get settingsWorkspaceSummaryDegraded;

  /// Summary shown when workspace shell access is blocked by missing setup
  ///
  /// In en, this message translates to:
  /// **'Finish setup before the workspace shell can become available.'**
  String get settingsWorkspaceSummaryNeedsSetup;

  /// Summary shown when workspace shell access needs another sign-in
  ///
  /// In en, this message translates to:
  /// **'Sign in again to restore workspace shell access.'**
  String get settingsWorkspaceSummaryNeedsSignIn;

  /// Row label for workspace shell access in the readiness summary
  ///
  /// In en, this message translates to:
  /// **'Shell access'**
  String get settingsWorkspaceShellAccessLabel;

  /// Row label for chat readiness in the workspace readiness summary
  ///
  /// In en, this message translates to:
  /// **'Chat'**
  String get settingsWorkspaceChatLabel;

  /// Row label for files readiness in the workspace readiness summary
  ///
  /// In en, this message translates to:
  /// **'Files'**
  String get settingsWorkspaceFilesLabel;

  /// Label used for readiness pills in the workspace summary
  ///
  /// In en, this message translates to:
  /// **'Readiness'**
  String get settingsWorkspaceCapabilityLabel;

  /// Label used for connection-state pills in the workspace summary
  ///
  /// In en, this message translates to:
  /// **'Connection'**
  String get settingsWorkspaceConnectionLabel;

  /// Label used for invalidation-reason pills in the workspace summary
  ///
  /// In en, this message translates to:
  /// **'Last change'**
  String get settingsWorkspaceLastChangeLabel;

  /// Label for Matrix E2EE validation status in workspace readiness
  ///
  /// In en, this message translates to:
  /// **'E2EE gate'**
  String get settingsWorkspaceMatrixE2eeGateLabel;

  /// Value when all Matrix E2EE validation gates are complete
  ///
  /// In en, this message translates to:
  /// **'Validated'**
  String get settingsWorkspaceMatrixE2eeValidated;

  /// Value when Matrix E2EE validation gates are not complete
  ///
  /// In en, this message translates to:
  /// **'Not validated'**
  String get settingsWorkspaceMatrixE2eeNotValidated;

  /// Label for whether backend diagnostics can read Matrix message bodies
  ///
  /// In en, this message translates to:
  /// **'Server-readable bodies'**
  String get settingsWorkspaceMatrixServerBodiesLabel;

  /// Value when encrypted Matrix message bodies remain opaque to backend diagnostics
  ///
  /// In en, this message translates to:
  /// **'No'**
  String get settingsWorkspaceMatrixServerBodiesOpaque;

  /// Value shown if backend diagnostics claim Matrix message bodies are server-readable
  ///
  /// In en, this message translates to:
  /// **'Review'**
  String get settingsWorkspaceMatrixServerBodiesReadable;

  /// Label for bot, assistant, or connector write policy in Matrix readiness
  ///
  /// In en, this message translates to:
  /// **'Agent writes'**
  String get settingsWorkspaceMatrixAgentWritesLabel;

  /// Value when Matrix bot/connector writes are blocked or fail closed
  ///
  /// In en, this message translates to:
  /// **'Blocked/fail-closed'**
  String get settingsWorkspaceMatrixAgentWritesBlocked;

  /// Value shown when Matrix bot/connector write policy is not clearly fail-closed
  ///
  /// In en, this message translates to:
  /// **'Review policy'**
  String get settingsWorkspaceMatrixAgentWritesReview;

  /// Readiness label for a ready capability
  ///
  /// In en, this message translates to:
  /// **'Ready'**
  String get settingsWorkspaceCapabilityReady;

  /// Readiness label for a degraded capability
  ///
  /// In en, this message translates to:
  /// **'Degraded'**
  String get settingsWorkspaceCapabilityDegraded;

  /// Readiness label for a blocked capability
  ///
  /// In en, this message translates to:
  /// **'Blocked'**
  String get settingsWorkspaceCapabilityBlocked;

  /// Readiness label for an unavailable capability
  ///
  /// In en, this message translates to:
  /// **'Unavailable'**
  String get settingsWorkspaceCapabilityUnavailable;

  /// Connection label for a connected integration
  ///
  /// In en, this message translates to:
  /// **'Connected'**
  String get settingsWorkspaceConnectionConnected;

  /// Connection label for a disconnected integration
  ///
  /// In en, this message translates to:
  /// **'Disconnected'**
  String get settingsWorkspaceConnectionDisconnected;

  /// Connection label for a degraded integration
  ///
  /// In en, this message translates to:
  /// **'Degraded'**
  String get settingsWorkspaceConnectionDegraded;

  /// Connection label for a misconfigured integration
  ///
  /// In en, this message translates to:
  /// **'Misconfigured'**
  String get settingsWorkspaceConnectionMisconfigured;

  /// Connection label for an integration that requires another sign-in
  ///
  /// In en, this message translates to:
  /// **'Needs sign-in'**
  String get settingsWorkspaceConnectionRequiresReauthentication;

  /// Connection label for an integration that is unavailable on the current platform
  ///
  /// In en, this message translates to:
  /// **'Unavailable on this platform'**
  String get settingsWorkspaceConnectionUnavailableOnPlatform;

  /// Invalidation label for auth configuration changes
  ///
  /// In en, this message translates to:
  /// **'Auth configuration changed'**
  String get settingsWorkspaceInvalidationAuthConfigurationChanged;

  /// Invalidation label for Matrix homeserver changes
  ///
  /// In en, this message translates to:
  /// **'Matrix homeserver changed'**
  String get settingsWorkspaceInvalidationMatrixHomeserverChanged;

  /// Invalidation label for Nextcloud base URL changes
  ///
  /// In en, this message translates to:
  /// **'Nextcloud base URL changed'**
  String get settingsWorkspaceInvalidationNextcloudBaseUrlChanged;

  /// Invalidation label for explicit sign-outs
  ///
  /// In en, this message translates to:
  /// **'Explicit sign-out'**
  String get settingsWorkspaceInvalidationExplicitSignOut;

  /// Invalidation label for restart-setup actions
  ///
  /// In en, this message translates to:
  /// **'Restarted setup'**
  String get settingsWorkspaceInvalidationRestartSetup;

  /// Invalidation label for backend API base URL changes
  ///
  /// In en, this message translates to:
  /// **'Backend API URL changed'**
  String get settingsWorkspaceInvalidationBackendApiBaseUrlChanged;

  /// Description for the settings server configuration section
  ///
  /// In en, this message translates to:
  /// **'Update the provider and service URLs Weave should use for your self-hosted environment.'**
  String get settingsServerConfigurationDescription;

  /// Label for the settings save button
  ///
  /// In en, this message translates to:
  /// **'Save Changes'**
  String get settingsSaveButton;

  /// Label used while the settings form is saving
  ///
  /// In en, this message translates to:
  /// **'Saving…'**
  String get settingsSaveInProgress;

  /// Section title for session management in settings
  ///
  /// In en, this message translates to:
  /// **'Session'**
  String get settingsSignOutTitle;

  /// Description for the sign-out section in settings
  ///
  /// In en, this message translates to:
  /// **'Sign out of the current server session and return to the sign-in gate.'**
  String get settingsSignOutDescription;

  /// Label for the settings sign-out button
  ///
  /// In en, this message translates to:
  /// **'Sign Out'**
  String get settingsSignOutButton;

  /// Label shown while sign-out is in progress
  ///
  /// In en, this message translates to:
  /// **'Signing out…'**
  String get settingsSignOutInProgress;

  /// Empty state message for the chat screen
  ///
  /// In en, this message translates to:
  /// **'No conversations yet'**
  String get chatEmptyMessage;

  /// Guidance shown below the chat empty-state title
  ///
  /// In en, this message translates to:
  /// **'Workspace rooms and direct messages will appear here when chat is ready.'**
  String get chatEmptyGuidance;

  /// Friendly error-state title shown when the chat list cannot load
  ///
  /// In en, this message translates to:
  /// **'Chat is not available right now'**
  String get chatErrorTitle;

  /// Fallback preview text for a conversation without a recent event
  ///
  /// In en, this message translates to:
  /// **'No recent messages'**
  String get chatConversationNoPreview;

  /// Fallback preview label for encrypted Matrix events
  ///
  /// In en, this message translates to:
  /// **'Encrypted message'**
  String get chatConversationEncryptedPreview;

  /// Fallback preview label for Matrix events that cannot be rendered yet
  ///
  /// In en, this message translates to:
  /// **'Unsupported message'**
  String get chatConversationUnsupportedPreview;

  /// Accessibility label for invited chat rooms
  ///
  /// In en, this message translates to:
  /// **'Invitation'**
  String get chatConversationInviteLabel;

  /// Accessibility label for direct conversations
  ///
  /// In en, this message translates to:
  /// **'Direct conversation'**
  String get chatConversationDirectMessageLabel;

  /// Recency badge for a chat conversation with activity in the last hour
  ///
  /// In en, this message translates to:
  /// **'Active now'**
  String get chatConversationRecentNow;

  /// Recency badge for a chat conversation with activity earlier today
  ///
  /// In en, this message translates to:
  /// **'Today'**
  String get chatConversationRecentToday;

  /// Recency badge for a chat conversation with activity yesterday
  ///
  /// In en, this message translates to:
  /// **'Yesterday'**
  String get chatConversationRecentYesterday;

  /// Recency badge for a chat conversation with activity in the last week
  ///
  /// In en, this message translates to:
  /// **'This week'**
  String get chatConversationRecentThisWeek;

  /// Accessibility label describing how many unread messages a conversation has
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0 {No unread messages} =1 {1 unread message} other {{count} unread messages}}'**
  String chatConversationUnreadCount(int count);

  /// Message shown while a room timeline is loading
  ///
  /// In en, this message translates to:
  /// **'Loading conversation…'**
  String get chatRoomLoadingLabel;

  /// Empty state message for a room without timeline events
  ///
  /// In en, this message translates to:
  /// **'No messages yet'**
  String get chatRoomEmptyMessage;

  /// Banner shown when a local unsent chat draft is restored
  ///
  /// In en, this message translates to:
  /// **'Draft restored from this device.'**
  String get chatRoomDraftRestoredMessage;

  /// Hint text for the room composer when sending is allowed
  ///
  /// In en, this message translates to:
  /// **'Write a message'**
  String get chatRoomComposerHint;

  /// Hint text for the room composer when sending is disabled
  ///
  /// In en, this message translates to:
  /// **'Messages are unavailable in this room right now'**
  String get chatRoomComposerDisabledHint;

  /// Primary button label for sending a room message
  ///
  /// In en, this message translates to:
  /// **'Send'**
  String get chatRoomSendButton;

  /// Button label shown while a room message is sending
  ///
  /// In en, this message translates to:
  /// **'Sending…'**
  String get chatRoomSendingButton;

  /// Action label for retrying a failed room message send
  ///
  /// In en, this message translates to:
  /// **'Retry send'**
  String get chatRoomRetrySendAction;

  /// Sender label used for locally pending outgoing messages
  ///
  /// In en, this message translates to:
  /// **'You'**
  String get chatRoomYouLabel;

  /// Status label shown on a message bubble while the message is sending
  ///
  /// In en, this message translates to:
  /// **'Sending…'**
  String get chatRoomMessageSendingStatus;

  /// Friendly status label shown on a message bubble when sending failed
  ///
  /// In en, this message translates to:
  /// **'Not sent'**
  String get chatRoomMessageFailedStatus;

  /// Fallback label for encrypted messages in the room timeline
  ///
  /// In en, this message translates to:
  /// **'Encrypted message'**
  String get chatRoomEncryptedMessageLabel;

  /// Fallback label for unsupported messages in the room timeline
  ///
  /// In en, this message translates to:
  /// **'Unsupported message'**
  String get chatRoomUnsupportedMessageLabel;

  /// Tooltip for the message actions menu
  ///
  /// In en, this message translates to:
  /// **'Message actions'**
  String get chatRoomMessageActionsLabel;

  /// Action label for archiving a message from the room timeline
  ///
  /// In en, this message translates to:
  /// **'Archive'**
  String get chatRoomArchiveAction;

  /// Dialog title shown before a chat message is archived
  ///
  /// In en, this message translates to:
  /// **'Archive message?'**
  String get chatRoomArchiveDialogTitle;

  /// Dialog body shown before a chat message is archived
  ///
  /// In en, this message translates to:
  /// **'This hides the message from your main timeline on this device. You can review or restore it from Archived messages.'**
  String get chatRoomArchiveDialogMessage;

  /// App bar action that opens the archived messages review view
  ///
  /// In en, this message translates to:
  /// **'Review archived messages'**
  String get chatRoomArchivedMessagesAction;

  /// Action that returns from archived messages to the active room timeline
  ///
  /// In en, this message translates to:
  /// **'Back to active timeline'**
  String get chatRoomActiveTimelineAction;

  /// Heading shown above the archived messages review view
  ///
  /// In en, this message translates to:
  /// **'Archived messages'**
  String get chatRoomArchivedReviewTitle;

  /// Description shown above the archived messages review view
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0 {Archived messages from this room appear here, separate from the active timeline.} =1 {1 archived message is shown separately from the active timeline.} other {{count} archived messages are shown separately from the active timeline.}}'**
  String chatRoomArchivedReviewDescription(int count);

  /// Empty state shown when the archived messages review view has no messages
  ///
  /// In en, this message translates to:
  /// **'No archived messages yet.'**
  String get chatRoomArchivedReviewEmptyMessage;

  /// Label shown on messages in the archived messages review view
  ///
  /// In en, this message translates to:
  /// **'Archived'**
  String get chatRoomArchivedMessageLabel;

  /// Action label for restoring an archived message to the active room timeline
  ///
  /// In en, this message translates to:
  /// **'Restore to timeline'**
  String get chatRoomRestoreAction;

  /// Snackbar confirmation after restoring an archived message
  ///
  /// In en, this message translates to:
  /// **'Message restored to the active timeline.'**
  String get chatRoomRestoreSuccessMessage;

  /// Snackbar error shown when restoring an archived message fails
  ///
  /// In en, this message translates to:
  /// **'This message could not be restored right now.'**
  String get chatRoomRestoreFailureMessage;

  /// Snackbar confirmation after archiving a message
  ///
  /// In en, this message translates to:
  /// **'Message archived.'**
  String get chatRoomArchiveSuccessMessage;

  /// Snackbar error shown when archiving a message fails
  ///
  /// In en, this message translates to:
  /// **'This message could not be archived right now.'**
  String get chatRoomArchiveFailureMessage;

  /// Empty state shown when all loaded messages have been archived from the main timeline
  ///
  /// In en, this message translates to:
  /// **'Archived messages are hidden from this timeline.'**
  String get chatRoomArchivedEmptyMessage;

  /// Empty state message for the files screen
  ///
  /// In en, this message translates to:
  /// **'No files yet'**
  String get filesEmptyMessage;

  /// Guidance shown below the files empty-state title
  ///
  /// In en, this message translates to:
  /// **'Upload a file or create a folder when you are ready to add workspace files.'**
  String get filesEmptyGuidance;

  /// Friendly empty-state title shown when files require a connection
  ///
  /// In en, this message translates to:
  /// **'Files are not connected'**
  String get filesDisconnectedTitle;

  /// Friendly empty-state title shown when files are misconfigured
  ///
  /// In en, this message translates to:
  /// **'Files need setup'**
  String get filesSetupNeededTitle;

  /// Friendly error-state title shown when the files session is invalid
  ///
  /// In en, this message translates to:
  /// **'Files need to reconnect'**
  String get filesSessionExpiredTitle;

  /// Friendly error-state title shown when files fail to load
  ///
  /// In en, this message translates to:
  /// **'Files could not be loaded'**
  String get filesLoadErrorTitle;

  /// Friendly recovery guidance shown when files fail before detailed state is available
  ///
  /// In en, this message translates to:
  /// **'Try again. If this keeps happening, check the workspace files status in setup or diagnostics.'**
  String get filesErrorGuidance;

  /// Empty state message for the calendar screen
  ///
  /// In en, this message translates to:
  /// **'No events yet'**
  String get calendarEmptyMessage;

  /// Legacy hidden Deck empty state message retained for compatibility
  ///
  /// In en, this message translates to:
  /// **'No boards in this active preview yet'**
  String get deckEmptyMessage;

  /// Label for the detected device language display
  ///
  /// In en, this message translates to:
  /// **'Device Language'**
  String get deviceLanguageLabel;

  /// Section title for choosing an OIDC provider
  ///
  /// In en, this message translates to:
  /// **'OIDC Provider'**
  String get serverConfigurationProviderLabel;

  /// Label for the provider selection field
  ///
  /// In en, this message translates to:
  /// **'Provider type'**
  String get serverConfigurationProviderFieldLabel;

  /// Label for the Authentik provider option
  ///
  /// In en, this message translates to:
  /// **'Authentik'**
  String get oidcProviderAuthentik;

  /// Label for the Keycloak provider option
  ///
  /// In en, this message translates to:
  /// **'Keycloak'**
  String get oidcProviderKeycloak;

  /// Label for the issuer URL field
  ///
  /// In en, this message translates to:
  /// **'OIDC Issuer URL'**
  String get serverConfigurationIssuerLabel;

  /// Helper text for the issuer URL field
  ///
  /// In en, this message translates to:
  /// **'This must be the absolute issuer URL for your OIDC provider.'**
  String get serverConfigurationIssuerHelper;

  /// Label for the OIDC client ID field
  ///
  /// In en, this message translates to:
  /// **'OIDC Client ID'**
  String get serverConfigurationClientIdLabel;

  /// Helper text for the OIDC client ID field
  ///
  /// In en, this message translates to:
  /// **'Enter the public/native client ID registered for Weave on this issuer.'**
  String get serverConfigurationClientIdHelper;

  /// Section title for derived service endpoints
  ///
  /// In en, this message translates to:
  /// **'Service Endpoints'**
  String get serverConfigurationServicesLabel;

  /// Helper text for the service endpoints section
  ///
  /// In en, this message translates to:
  /// **'Defaults for Matrix, Nextcloud, and the backend API are derived from the issuer host. Edit them if your services live elsewhere.'**
  String get serverConfigurationServicesHelper;

  /// Label for the Matrix homeserver URL field
  ///
  /// In en, this message translates to:
  /// **'Matrix Homeserver URL'**
  String get serverConfigurationMatrixLabel;

  /// Label for the Nextcloud base URL field
  ///
  /// In en, this message translates to:
  /// **'Nextcloud Base URL'**
  String get serverConfigurationNextcloudLabel;

  /// Label for the backend API base URL field
  ///
  /// In en, this message translates to:
  /// **'Backend API Base URL'**
  String get serverConfigurationBackendApiLabel;

  /// Helper text showing the derived default for a service endpoint
  ///
  /// In en, this message translates to:
  /// **'Derived default: {value}'**
  String serverConfigurationDerivedHint(String value);

  /// Title for the OIDC client registration help card
  ///
  /// In en, this message translates to:
  /// **'Register Weave as a native/public client'**
  String get oidcRegistrationHelpTitle;

  /// General description for the OIDC client registration help card
  ///
  /// In en, this message translates to:
  /// **'Use Authorization Code + PKCE with the system browser, and allow the Weave redirect URIs below on the provider-side client registration.'**
  String get oidcRegistrationHelpDescription;

  /// Warning that Weave should not use a client secret
  ///
  /// In en, this message translates to:
  /// **'Do not create or paste a client secret here. Weave uses a public native-client flow.'**
  String get oidcRegistrationHelpNoSecret;

  /// Provider-specific OIDC registration guidance for Authentik
  ///
  /// In en, this message translates to:
  /// **'In Authentik, create an OAuth2/OpenID Connect provider for Weave, add these redirect URIs to the provider, and ensure the client is configured for Authorization Code flow with `offline_access` available if you want refresh tokens.'**
  String get oidcRegistrationHelpAuthentikSteps;

  /// Provider-specific OIDC registration guidance for Keycloak
  ///
  /// In en, this message translates to:
  /// **'In Keycloak, create a public OpenID Connect client for Weave, add these redirect URIs and post-logout redirect URIs, and enable Standard Flow with PKCE (S256) so Weave can sign in without a client secret.'**
  String get oidcRegistrationHelpKeycloakSteps;

  /// Title shown above the redirect URI values
  ///
  /// In en, this message translates to:
  /// **'Register these redirect URIs'**
  String get oidcRegistrationHelpRedirectsTitle;

  /// Text showing the sign-in redirect URI
  ///
  /// In en, this message translates to:
  /// **'Sign-in redirect: {value}'**
  String oidcRegistrationHelpRedirectValue(String value);

  /// Text showing the post-logout redirect URI
  ///
  /// In en, this message translates to:
  /// **'Post-logout redirect: {value}'**
  String oidcRegistrationHelpPostLogoutRedirectValue(String value);

  /// App bar title for the sign-in screen
  ///
  /// In en, this message translates to:
  /// **'Sign In'**
  String get signInScreenTitle;

  /// Main heading on the sign-in screen
  ///
  /// In en, this message translates to:
  /// **'Sign in to continue'**
  String get signInTitle;

  /// Description text on the sign-in screen
  ///
  /// In en, this message translates to:
  /// **'Weave is configured. Use your provider account in the system browser to open the authenticated app shell.'**
  String get signInDescription;

  /// Title for the sign-in configuration summary card
  ///
  /// In en, this message translates to:
  /// **'Current sign-in configuration'**
  String get signInConfigurationTitle;

  /// Summary line showing the provider label on the sign-in screen
  ///
  /// In en, this message translates to:
  /// **'Provider: {value}'**
  String signInConfigurationProvider(String value);

  /// Summary line showing the issuer URL on the sign-in screen
  ///
  /// In en, this message translates to:
  /// **'Issuer: {value}'**
  String signInConfigurationIssuer(String value);

  /// Summary line showing the client ID on the sign-in screen
  ///
  /// In en, this message translates to:
  /// **'Client ID: {value}'**
  String signInConfigurationClientId(String value);

  /// Primary sign-in button label
  ///
  /// In en, this message translates to:
  /// **'Sign In'**
  String get signInButton;

  /// Label shown while sign-in is in progress
  ///
  /// In en, this message translates to:
  /// **'Signing in…'**
  String get signInInProgress;

  /// Secondary action label to return to setup from the sign-in screen
  ///
  /// In en, this message translates to:
  /// **'Back to Setup'**
  String get signInBackToSetupButton;

  /// Heading shown when auth configuration is incomplete
  ///
  /// In en, this message translates to:
  /// **'Finish setup to sign in'**
  String get signInMissingConfigurationTitle;

  /// Description shown when auth configuration is incomplete
  ///
  /// In en, this message translates to:
  /// **'Weave still needs a valid issuer URL and client ID before it can open the browser sign-in flow.'**
  String get signInMissingConfigurationDescription;

  /// Settings section title for the authenticated Weave profile
  ///
  /// In en, this message translates to:
  /// **'Weave profile'**
  String get profileSectionTitle;

  /// Settings description for the profile card
  ///
  /// In en, this message translates to:
  /// **'This profile comes from the Weave backend identity facade and is shared by product modules.'**
  String get profileSectionDescription;

  /// Profile card error message
  ///
  /// In en, this message translates to:
  /// **'The Weave profile could not be loaded right now.'**
  String get profileLoadFailure;

  /// Profile card message when there is no authenticated session
  ///
  /// In en, this message translates to:
  /// **'Sign in to view your Weave profile.'**
  String get profileSignedOutMessage;

  /// Profile display name field label
  ///
  /// In en, this message translates to:
  /// **'Display name'**
  String get profileDisplayNameLabel;

  /// Profile username field label
  ///
  /// In en, this message translates to:
  /// **'Username'**
  String get profileUsernameLabel;

  /// Profile email field label
  ///
  /// In en, this message translates to:
  /// **'Email'**
  String get profileEmailLabel;

  /// Profile email verification field label
  ///
  /// In en, this message translates to:
  /// **'Email verified'**
  String get profileEmailVerifiedLabel;

  /// Yes value for verified email
  ///
  /// In en, this message translates to:
  /// **'Yes'**
  String get profileEmailVerifiedYes;

  /// No value for unverified email
  ///
  /// In en, this message translates to:
  /// **'No'**
  String get profileEmailVerifiedNo;

  /// Profile locale field label
  ///
  /// In en, this message translates to:
  /// **'Locale'**
  String get profileLocaleLabel;

  /// Profile timezone field label
  ///
  /// In en, this message translates to:
  /// **'Timezone'**
  String get profileTimezoneLabel;

  /// Profile roles field label
  ///
  /// In en, this message translates to:
  /// **'Roles'**
  String get profileRolesLabel;

  /// Profile groups field label
  ///
  /// In en, this message translates to:
  /// **'Groups'**
  String get profileGroupsLabel;

  /// Deprecated message explaining why profile editing was not yet enabled
  ///
  /// In en, this message translates to:
  /// **'Profile editing is prepared in the app, but saving changes is blocked until the backend exposes PATCH /api/profile.'**
  String get profileEditingBlockedMessage;

  /// Title for the editable profile form
  ///
  /// In en, this message translates to:
  /// **'Edit profile'**
  String get profileEditSectionTitle;

  /// Description for the editable profile form
  ///
  /// In en, this message translates to:
  /// **'Save changes through the Weave backend profile facade so every product module sees the same profile.'**
  String get profileEditSectionDescription;

  /// Helper text for the profile display name field
  ///
  /// In en, this message translates to:
  /// **'Shown to workspace members in Weave surfaces.'**
  String get profileDisplayNameHelper;

  /// Helper text for the profile locale field
  ///
  /// In en, this message translates to:
  /// **'Use a locale code such as en or de.'**
  String get profileLocaleHelper;

  /// Helper text for the profile timezone field
  ///
  /// In en, this message translates to:
  /// **'Use an IANA timezone such as Europe/Berlin.'**
  String get profileTimezoneHelper;

  /// Validation error for empty editable profile fields
  ///
  /// In en, this message translates to:
  /// **'This field is required.'**
  String get profileEditRequiredFieldError;

  /// Button label for saving profile edits
  ///
  /// In en, this message translates to:
  /// **'Save profile'**
  String get profileEditSaveButton;

  /// Busy button label while saving profile edits
  ///
  /// In en, this message translates to:
  /// **'Saving profile…'**
  String get profileEditSavingButton;

  /// Live-region success message after saving profile edits
  ///
  /// In en, this message translates to:
  /// **'Profile saved.'**
  String get profileEditSavedMessage;

  /// Row label for calendar readiness in the workspace readiness summary
  ///
  /// In en, this message translates to:
  /// **'Calendar'**
  String get settingsWorkspaceCalendarLabel;

  /// Row label for boards readiness in the workspace readiness summary
  ///
  /// In en, this message translates to:
  /// **'Boards'**
  String get settingsWorkspaceBoardsLabel;

  /// Title for the workspace-scoped calendar banner
  ///
  /// In en, this message translates to:
  /// **'Workspace calendar'**
  String get calendarWorkspaceScopeTitle;

  /// Description explaining that the current calendar surface is workspace-scoped rather than private-personal scoped
  ///
  /// In en, this message translates to:
  /// **'This first Calendar slice is the workspace scope of Weave shared scheduling. Team and channel calendars are the next product scopes; private personal calendars are out of scope.'**
  String get calendarWorkspaceScopeDescription;

  /// Description for non-workspace calendar scope metadata returned by the backend
  ///
  /// In en, this message translates to:
  /// **'Events are shown from {scopeLabel}.'**
  String calendarGenericScopeDescription(String scopeLabel);

  /// Title for the external calendar client setup card
  ///
  /// In en, this message translates to:
  /// **'Use Calendar in other apps'**
  String get calendarClientSetupTitle;

  /// Description for the external calendar client setup card
  ///
  /// In en, this message translates to:
  /// **'Weave can hand native clients secret-free setup details. Weave still owns the product calendar UI.'**
  String get calendarClientSetupDescription;

  /// Semantic label for the external calendar setup icon
  ///
  /// In en, this message translates to:
  /// **'External calendar setup'**
  String get calendarClientSetupIconSemantic;

  /// Loading message for external calendar setup options
  ///
  /// In en, this message translates to:
  /// **'Loading setup options…'**
  String get calendarClientSetupLoading;

  /// Error message when external calendar setup options cannot be loaded
  ///
  /// In en, this message translates to:
  /// **'Calendar setup options are unavailable right now.'**
  String get calendarClientSetupUnavailable;

  /// Loading message while checking whether the backend reports Calendar as available
  ///
  /// In en, this message translates to:
  /// **'Checking Calendar availability…'**
  String get calendarCapabilityLoading;

  /// Error shown when Calendar capability status cannot be loaded
  ///
  /// In en, this message translates to:
  /// **'Calendar availability could not be checked right now.'**
  String get calendarCapabilityError;

  /// Title for the Calendar module unavailable state
  ///
  /// In en, this message translates to:
  /// **'Calendar is unavailable'**
  String get calendarUnavailableTitle;

  /// Description for the Calendar module unavailable state
  ///
  /// In en, this message translates to:
  /// **'Backend readiness is {readiness}. Event changes stay disabled until the Weave backend reports Calendar ready.'**
  String calendarUnavailableDescription(String readiness);

  /// Label for the external CalDAV username
  ///
  /// In en, this message translates to:
  /// **'Username'**
  String get calendarClientSetupUsernameLabel;

  /// Label for the CalDAV discovery URL
  ///
  /// In en, this message translates to:
  /// **'CalDAV discovery URL'**
  String get calendarClientSetupDiscoveryUrlLabel;

  /// Label for the CalDAV principal URL
  ///
  /// In en, this message translates to:
  /// **'Principal URL'**
  String get calendarClientSetupPrincipalUrlLabel;

  /// Heading for the external calendar credential safety policy
  ///
  /// In en, this message translates to:
  /// **'Credential safety'**
  String get calendarClientSetupCredentialPolicyTitle;

  /// Heading for the calendar external client access model
  ///
  /// In en, this message translates to:
  /// **'Access model'**
  String get calendarClientSetupAccessModelTitle;

  /// Status text when private personal calendars are available
  ///
  /// In en, this message translates to:
  /// **'Private personal calendars out of scope'**
  String get calendarClientSetupPrivateCalendarsAvailable;

  /// Status text when private personal calendars are blocked
  ///
  /// In en, this message translates to:
  /// **'Private personal calendars out of scope'**
  String get calendarClientSetupPrivateCalendarsBlocked;

  /// Calendar external credential model label
  ///
  /// In en, this message translates to:
  /// **'External credential model: {model}'**
  String calendarClientSetupExternalCredentialModel(String model);

  /// Heading for calendar credential readiness details
  ///
  /// In en, this message translates to:
  /// **'Credential readiness'**
  String get calendarClientSetupCredentialReadinessTitle;

  /// Calendar credential readiness status
  ///
  /// In en, this message translates to:
  /// **'Status: {status}'**
  String calendarClientSetupCredentialReadinessStatus(String status);

  /// Blocked state explanation for Apple calendar profiles
  ///
  /// In en, this message translates to:
  /// **'Apple profiles stay disabled until profiles are signed and safe credentials exist.'**
  String get calendarClientSetupAppleProfileBlocked;

  /// Blocked state explanation for calendar subscriptions
  ///
  /// In en, this message translates to:
  /// **'Webcal/ICS subscriptions stay disabled until revocable read-only tokens exist.'**
  String get calendarClientSetupSubscriptionsBlocked;

  /// Safe credential boundary statement for calendar client setup
  ///
  /// In en, this message translates to:
  /// **'Backend actor credentials are not exposed to client setup artifacts.'**
  String get calendarClientSetupCredentialsSafe;

  /// Unsafe credential boundary warning for calendar client setup
  ///
  /// In en, this message translates to:
  /// **'Setup is blocked because backend actor credentials would be exposed.'**
  String get calendarClientSetupCredentialsUnsafe;

  /// Heading for external calendar platform setup options
  ///
  /// In en, this message translates to:
  /// **'Platform setup'**
  String get calendarClientSetupPlatformsTitle;

  /// Status label for an available external calendar setup option
  ///
  /// In en, this message translates to:
  /// **'available'**
  String get calendarClientSetupAvailableStatus;

  /// Status label for a planned external calendar setup option
  ///
  /// In en, this message translates to:
  /// **'planned'**
  String get calendarClientSetupPlannedStatus;

  /// Fallback explanation for a planned external calendar setup option
  ///
  /// In en, this message translates to:
  /// **'This setup path is feature-gated until revocation, provisioning, and platform profile tests are complete.'**
  String get calendarClientSetupPlannedFallback;

  /// External calendar setup option summary
  ///
  /// In en, this message translates to:
  /// **'{platform} via {method}: {status}'**
  String calendarClientSetupOptionTitle(
    String platform,
    String method,
    String status,
  );

  /// Tooltip for copying an external calendar setup value
  ///
  /// In en, this message translates to:
  /// **'Copy {label}'**
  String calendarClientSetupCopyTooltip(String label);

  /// Snackbar after copying an external calendar setup value
  ///
  /// In en, this message translates to:
  /// **'Calendar setup value copied.'**
  String get calendarClientSetupCopied;

  /// Button label for opening the create calendar event form
  ///
  /// In en, this message translates to:
  /// **'Create event'**
  String get calendarCreateButton;

  /// Dialog title for creating a calendar event
  ///
  /// In en, this message translates to:
  /// **'Create calendar event'**
  String get calendarCreateDialogTitle;

  /// Dialog title for editing a calendar event
  ///
  /// In en, this message translates to:
  /// **'Edit calendar event'**
  String get calendarEditDialogTitle;

  /// Calendar event title field label
  ///
  /// In en, this message translates to:
  /// **'Title'**
  String get calendarTitleFieldLabel;

  /// Calendar event description field label
  ///
  /// In en, this message translates to:
  /// **'Description'**
  String get calendarDescriptionFieldLabel;

  /// Calendar event location field label
  ///
  /// In en, this message translates to:
  /// **'Location'**
  String get calendarLocationFieldLabel;

  /// Validation message when a calendar event title is missing
  ///
  /// In en, this message translates to:
  /// **'Enter an event title.'**
  String get calendarTitleRequired;

  /// Button label for closing the calendar create dialog
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get calendarCancelButton;

  /// Button label for saving a calendar event
  ///
  /// In en, this message translates to:
  /// **'Save event'**
  String get calendarSaveButton;

  /// Tooltip for deleting a calendar event
  ///
  /// In en, this message translates to:
  /// **'Delete {title}'**
  String calendarDeleteEventTooltip(String title);

  /// Tooltip for editing a calendar event
  ///
  /// In en, this message translates to:
  /// **'Edit {title}'**
  String calendarEditEventTooltip(String title);

  /// Tooltip for opening a calendar event detail view
  ///
  /// In en, this message translates to:
  /// **'View {title}'**
  String calendarViewEventTooltip(String title);

  /// Semantic label for a calendar event row
  ///
  /// In en, this message translates to:
  /// **'{title}, starts {startsAt}, ends {endsAt}'**
  String calendarEventSemantic(String title, String startsAt, String endsAt);

  /// Title for the calendar event details dialog
  ///
  /// In en, this message translates to:
  /// **'Calendar event details'**
  String get calendarDetailsDialogTitle;

  /// Loading message while a calendar event is read from the backend
  ///
  /// In en, this message translates to:
  /// **'Loading event details…'**
  String get calendarDetailsLoading;

  /// Error shown when reading a calendar event from the backend fails
  ///
  /// In en, this message translates to:
  /// **'Calendar event details are unavailable right now.'**
  String get calendarDetailsError;

  /// Label for the calendar event time range in the details dialog
  ///
  /// In en, this message translates to:
  /// **'Time'**
  String get calendarDetailsTimeLabel;

  /// Label for the calendar scope in the event details dialog
  ///
  /// In en, this message translates to:
  /// **'Calendar scope'**
  String get calendarDetailsScopeLabel;

  /// Label for the calendar event location in the details dialog
  ///
  /// In en, this message translates to:
  /// **'Location'**
  String get calendarDetailsLocationLabel;

  /// Label for the calendar event description in the details dialog
  ///
  /// In en, this message translates to:
  /// **'Description'**
  String get calendarDetailsDescriptionLabel;

  /// Button label for closing the calendar details dialog
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get calendarCloseButton;

  /// Snackbar message after a calendar event is created
  ///
  /// In en, this message translates to:
  /// **'Calendar event created.'**
  String get calendarCreateSuccess;

  /// Snackbar message after a calendar event is updated
  ///
  /// In en, this message translates to:
  /// **'Calendar event updated.'**
  String get calendarUpdateSuccess;

  /// Snackbar message after a calendar event is deleted
  ///
  /// In en, this message translates to:
  /// **'Calendar event deleted.'**
  String get calendarDeleteSuccess;

  /// Snackbar message when a calendar operation fails
  ///
  /// In en, this message translates to:
  /// **'The calendar could not save that change right now.'**
  String get calendarOperationFailure;

  /// Title for the feature-gated boards/tasks preview screen
  ///
  /// In en, this message translates to:
  /// **'Boards preview'**
  String get boardsPreviewScreenTitle;

  /// Semantic label for the boards preview icon
  ///
  /// In en, this message translates to:
  /// **'Boards preview'**
  String get boardsPreviewIconSemantic;

  /// Title for the banner that marks boards/tasks as active gated scope
  ///
  /// In en, this message translates to:
  /// **'Active boards/tasks preview'**
  String get boardsPreviewBoundaryTitle;

  /// Description explaining that boards/tasks are active scope but not provider-connected
  ///
  /// In en, this message translates to:
  /// **'This active preview shows the intended Weave-owned board model and accessible task movement alternatives. It remains feature-gated and is not connected to Vikunja, Deck, or another provider yet.'**
  String get boardsPreviewBoundaryDescription;

  /// Semantic summary for the active-preview boundary banner
  ///
  /// In en, this message translates to:
  /// **'Active boards/tasks preview. Feature-gated provider-neutral Weave model with keyboard and screen-reader alternatives; no live provider is connected yet.'**
  String get boardsPreviewBoundarySemantic;

  /// Chip label stating boards/tasks are active preview scope
  ///
  /// In en, this message translates to:
  /// **'Active preview'**
  String get boardsPreviewActivePreviewChip;

  /// Chip label stating the boards model is provider-neutral
  ///
  /// In en, this message translates to:
  /// **'Provider-neutral model'**
  String get boardsPreviewProviderNeutralChip;

  /// Chip label stating board operations have non-drag alternatives
  ///
  /// In en, this message translates to:
  /// **'No drag required'**
  String get boardsPreviewKeyboardChip;

  /// Number of preview board columns
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{No columns} one{1 column} other{{count} columns}}'**
  String boardsPreviewColumnCount(int count);

  /// Number of preview tasks
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{No tasks} one{1 task} other{{count} tasks}}'**
  String boardsPreviewTaskCount(int count);

  /// Summary chip for accessible non-drag movement
  ///
  /// In en, this message translates to:
  /// **'Move menu instead of drag-only'**
  String get boardsPreviewNonDragMovement;

  /// Semantic label for the board preview summary
  ///
  /// In en, this message translates to:
  /// **'Board {boardName}, {columnCount, plural, one{1 column} other{{columnCount} columns}}, {taskCount, plural, one{1 task} other{{taskCount} tasks}}.'**
  String boardsPreviewBoardSemantic(
    String boardName,
    int columnCount,
    int taskCount,
  );

  /// Semantic label for a board column
  ///
  /// In en, this message translates to:
  /// **'Column {columnName}, status {status}, {taskCount, plural, one{1 task} other{{taskCount} tasks}}.'**
  String boardsPreviewColumnSemantic(
    String columnName,
    String status,
    int taskCount,
  );

  /// Visible task count summary for a column
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{No tasks in this column} one{1 task in this column} other{{count} tasks in this column}}'**
  String boardsPreviewColumnTaskSummary(int count);

  /// Visible task count and WIP limit summary for a column
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{No tasks} one{1 task} other{{count} tasks}} · WIP limit {limit}'**
  String boardsPreviewColumnWipSummary(int count, int limit);

  /// Semantic label for a board task card
  ///
  /// In en, this message translates to:
  /// **'Task {taskTitle}. Column {columnName}. Status {status}. Assignee {assignee}. Due {due}. Priority {priority}.'**
  String boardsPreviewTaskSemantic(
    String taskTitle,
    String columnName,
    String status,
    String assignee,
    String due,
    String priority,
  );

  /// Tooltip for the task action menu
  ///
  /// In en, this message translates to:
  /// **'Task actions for {taskTitle}'**
  String boardsPreviewTaskActionsTooltip(String taskTitle);

  /// Preview task action for non-drag column movement
  ///
  /// In en, this message translates to:
  /// **'Move to another column'**
  String get boardsPreviewMoveTaskAction;

  /// Preview task action for marking a task done
  ///
  /// In en, this message translates to:
  /// **'Mark done'**
  String get boardsPreviewMarkDoneAction;

  /// Preview task action for marking a task blocked
  ///
  /// In en, this message translates to:
  /// **'Mark blocked'**
  String get boardsPreviewBlockTaskAction;

  /// Snackbar shown when a preview task action is selected
  ///
  /// In en, this message translates to:
  /// **'Preview only — no task was changed.'**
  String get boardsPreviewActionPreviewOnly;

  /// Board task status label
  ///
  /// In en, this message translates to:
  /// **'Not started'**
  String get boardsPreviewStatusNotStarted;

  /// Board task status label
  ///
  /// In en, this message translates to:
  /// **'In progress'**
  String get boardsPreviewStatusInProgress;

  /// Board task status label
  ///
  /// In en, this message translates to:
  /// **'Blocked'**
  String get boardsPreviewStatusBlocked;

  /// Board task status label
  ///
  /// In en, this message translates to:
  /// **'Done'**
  String get boardsPreviewStatusDone;

  /// Semantic label for task/column status pills
  ///
  /// In en, this message translates to:
  /// **'Status: {status}'**
  String boardsPreviewStatusSemantic(String status);

  /// Chip label indicating the preview snapshot came from the backend facade
  ///
  /// In en, this message translates to:
  /// **'Backend facade fed'**
  String get boardsPreviewBackendFedChip;

  /// Chip label indicating the backend provider runtime is blocked/unavailable
  ///
  /// In en, this message translates to:
  /// **'Provider runtime blocked'**
  String get boardsPreviewProviderBlockedChip;

  /// Chip label indicating the preview is still using local fixtures
  ///
  /// In en, this message translates to:
  /// **'Static fixture preview'**
  String get boardsPreviewStaticFixtureChip;

  /// Visible provider capability summary
  ///
  /// In en, this message translates to:
  /// **'Provider: {provider}'**
  String boardsPreviewProviderCapabilitySummary(String provider);

  /// Capability label when accessible non-drag backend actions are available
  ///
  /// In en, this message translates to:
  /// **'Backend non-drag actions ready'**
  String get boardsPreviewCapabilityNonDragReady;

  /// Capability label when accessible non-drag backend actions are unavailable
  ///
  /// In en, this message translates to:
  /// **'Backend non-drag actions blocked'**
  String get boardsPreviewCapabilityNonDragBlocked;

  /// Provider label for the hidden local backend facade
  ///
  /// In en, this message translates to:
  /// **'in-memory backend facade'**
  String get boardsPreviewProviderInMemory;

  /// Provider label for Vikunja
  ///
  /// In en, this message translates to:
  /// **'Vikunja adapter'**
  String get boardsPreviewProviderVikunja;

  /// Provider label for OpenProject
  ///
  /// In en, this message translates to:
  /// **'OpenProject adapter'**
  String get boardsPreviewProviderOpenProject;

  /// Provider label for Nextcloud Deck
  ///
  /// In en, this message translates to:
  /// **'Nextcloud Deck adapter'**
  String get boardsPreviewProviderNextcloudDeck;

  /// Provider label when only static fixtures are available
  ///
  /// In en, this message translates to:
  /// **'no backend provider'**
  String get boardsPreviewProviderNone;

  /// Provider label when the backend facade is unavailable
  ///
  /// In en, this message translates to:
  /// **'backend unavailable'**
  String get boardsPreviewProviderUnavailable;

  /// Provider label for an unrecognized backend provider
  ///
  /// In en, this message translates to:
  /// **'unknown provider'**
  String get boardsPreviewProviderUnknown;

  /// Snackbar after a backend move task action succeeds
  ///
  /// In en, this message translates to:
  /// **'Task moved through the backend facade.'**
  String get boardsPreviewActionMoved;

  /// Snackbar after a backend complete task action succeeds
  ///
  /// In en, this message translates to:
  /// **'Task marked done through the backend facade.'**
  String get boardsPreviewActionCompleted;

  /// Snackbar after a backend Boards action fails
  ///
  /// In en, this message translates to:
  /// **'The backend facade could not save that Boards preview action.'**
  String get boardsPreviewActionFailed;

  /// Snackbar when moving to the next preview column is not possible
  ///
  /// In en, this message translates to:
  /// **'This task is already in the last preview column.'**
  String get boardsPreviewActionNoNextColumn;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['de', 'en'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'de':
      return AppLocalizationsDe();
    case 'en':
      return AppLocalizationsEn();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
