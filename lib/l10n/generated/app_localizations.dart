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

  /// Title for the chat overview/home surface
  ///
  /// In en, this message translates to:
  /// **'Weave Home'**
  String get chatOverviewTitle;

  /// Description for the chat overview/home surface
  ///
  /// In en, this message translates to:
  /// **'Your personal messages, favorites, channels, and AI chats are grouped here so the workspace starts from intent instead of a flat room list.'**
  String get chatOverviewDescription;

  /// Title for the favorites section in the chat overview
  ///
  /// In en, this message translates to:
  /// **'Favorites'**
  String get chatFavoritesSectionTitle;

  /// Description for the favorites section in the chat overview
  ///
  /// In en, this message translates to:
  /// **'Pinned people, channels, and AI chats you want to reach first.'**
  String get chatFavoritesSectionDescription;

  /// Empty state for the favorites section in the chat overview
  ///
  /// In en, this message translates to:
  /// **'No favorites yet. When favorites sync is available, important direct messages, channels, and AI chats will stay here.'**
  String get chatFavoritesSectionEmpty;

  /// Title for the personal messages section in the chat overview
  ///
  /// In en, this message translates to:
  /// **'Personal messages'**
  String get chatPersonalMessagesSectionTitle;

  /// Description for the personal messages section in the chat overview
  ///
  /// In en, this message translates to:
  /// **'Direct conversations with people in your workspace.'**
  String get chatPersonalMessagesSectionDescription;

  /// Empty state for the personal messages section in the chat overview
  ///
  /// In en, this message translates to:
  /// **'No personal messages are available yet.'**
  String get chatPersonalMessagesSectionEmpty;

  /// Title for the channel section in the chat overview
  ///
  /// In en, this message translates to:
  /// **'Channels'**
  String get chatChannelsSectionTitle;

  /// Description for the channel section in the chat overview
  ///
  /// In en, this message translates to:
  /// **'Team and topic rooms for shared work.'**
  String get chatChannelsSectionDescription;

  /// Empty state for the channel section in the chat overview
  ///
  /// In en, this message translates to:
  /// **'No channels are available yet.'**
  String get chatChannelsSectionEmpty;

  /// Title for the AI chats section in the chat overview
  ///
  /// In en, this message translates to:
  /// **'AI chats'**
  String get chatAiChatsSectionTitle;

  /// Description for the AI chats section in the chat overview
  ///
  /// In en, this message translates to:
  /// **'Specialized assistant and agent chats live in their own area.'**
  String get chatAiChatsSectionDescription;

  /// Empty state for the AI chats section in the chat overview
  ///
  /// In en, this message translates to:
  /// **'No AI chats are connected yet. Future specialized agents will appear here instead of being mixed into personal messages.'**
  String get chatAiChatsSectionEmpty;

  /// Title for the governed agent chat preview panel
  ///
  /// In en, this message translates to:
  /// **'Agent chats are governed by your workspace'**
  String get chatAgentGovernanceTitle;

  /// Description for the governed agent chat preview panel
  ///
  /// In en, this message translates to:
  /// **'Agents can help inside Weave only after an owner or admin enables a package, chooses scopes, and keeps consent and audit visible.'**
  String get chatAgentGovernanceDescription;

  /// Title for the agent context pack explanation card
  ///
  /// In en, this message translates to:
  /// **'Context pack before action'**
  String get chatAgentContextPackTitle;

  /// Description for the agent context pack explanation card
  ///
  /// In en, this message translates to:
  /// **'When an agent is available, Weave will show what context is sent for this request before the agent uses it.'**
  String get chatAgentContextPackDescription;

  /// Bullet explaining scoped agent context
  ///
  /// In en, this message translates to:
  /// **'Context is scoped to a selected chat, file, calendar event, board, or explicit workspace source.'**
  String get chatAgentContextPackScopedBullet;

  /// Bullet explaining consent and permission hints for agent context
  ///
  /// In en, this message translates to:
  /// **'You will see permission hints before starting or approving an agent action.'**
  String get chatAgentContextPackConsentBullet;

  /// Bullet explaining that agents are not surveillance-aware
  ///
  /// In en, this message translates to:
  /// **'Agents do not continuously read rooms in the background.'**
  String get chatAgentContextPackNoSurveillanceBullet;

  /// Audit and approval note for the governed agent chat preview panel
  ///
  /// In en, this message translates to:
  /// **'Audit placeholders are part of this preview: agent creation, context access, tool/action execution, approval, and revocation must be recorded before runtime promotion.'**
  String get chatAgentGovernanceAuditNote;

  /// Agent availability label for a preview-only agent
  ///
  /// In en, this message translates to:
  /// **'Preview only'**
  String get chatAgentAvailabilityPreview;

  /// Agent availability label when setup must be done by an admin
  ///
  /// In en, this message translates to:
  /// **'Admin setup required'**
  String get chatAgentAvailabilityAdminSetup;

  /// Agent availability label when policy blocks an agent
  ///
  /// In en, this message translates to:
  /// **'Blocked by policy'**
  String get chatAgentAvailabilityBlocked;

  /// Title for the personal assistant preview tile
  ///
  /// In en, this message translates to:
  /// **'Personal assistant'**
  String get chatAgentPersonalAssistantTitle;

  /// Description for the personal assistant preview tile
  ///
  /// In en, this message translates to:
  /// **'A future private assistant chat for drafting, summaries, and reminders inside Weave.'**
  String get chatAgentPersonalAssistantDescription;

  /// Title for the channel agent preview tile
  ///
  /// In en, this message translates to:
  /// **'Channel agent'**
  String get chatAgentChannelAgentTitle;

  /// Description for the channel agent preview tile
  ///
  /// In en, this message translates to:
  /// **'A future helper for a channel or project space, governed by an admin-approved package.'**
  String get chatAgentChannelAgentDescription;

  /// Scope explanation for personal assistant preview
  ///
  /// In en, this message translates to:
  /// **'Uses only context you choose for the current request; workspace policy decides which skills are available.'**
  String get chatAgentPersonalScope;

  /// Boundary explanation for personal assistant preview
  ///
  /// In en, this message translates to:
  /// **'No continuous room reading; a context pack is assembled only after you start or approve a request.'**
  String get chatAgentPersonalBoundary;

  /// Audit explanation for personal assistant preview
  ///
  /// In en, this message translates to:
  /// **'Creation, context access, tool use, and permission changes will be auditable before runtime use.'**
  String get chatAgentPersonalAudit;

  /// Scope explanation for channel agent preview
  ///
  /// In en, this message translates to:
  /// **'An owner or admin must enable the package and choose allowed chat, files, calendar, and board scopes.'**
  String get chatAgentChannelScope;

  /// Boundary explanation for channel agent preview
  ///
  /// In en, this message translates to:
  /// **'The agent sees named spaces and explicit context packs, not every message in the workspace.'**
  String get chatAgentChannelBoundary;

  /// Audit explanation for channel agent preview
  ///
  /// In en, this message translates to:
  /// **'Approvals, revocations, and action attempts stay visible to admins without exposing secrets to the app.'**
  String get chatAgentChannelAudit;

  /// Disabled action label for preview agent chats
  ///
  /// In en, this message translates to:
  /// **'Unavailable until enabled'**
  String get chatAgentStartDisabledButton;

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

  /// Title for the channel workspace summary card
  ///
  /// In en, this message translates to:
  /// **'{channelName} workspace'**
  String channelWorkspaceSummaryTitle(String channelName);

  /// Description for the channel workspace summary card
  ///
  /// In en, this message translates to:
  /// **'This channel is a work room with tabs for chat, files, boards, calendar, and meetings where the workspace has enabled them.'**
  String get channelWorkspaceSummaryDescription;

  /// Governance note for channel workspace tabs
  ///
  /// In en, this message translates to:
  /// **'Context stays explicit: no hidden continuous room reading and no ordinary member access to admin setup.'**
  String get channelWorkspaceGovernanceNote;

  /// Semantic label for the channel workspace tab list
  ///
  /// In en, this message translates to:
  /// **'Channel workspace tabs for {channelName}'**
  String channelWorkspaceTabsSemanticLabel(String channelName);

  /// Channel workspace chat tab label
  ///
  /// In en, this message translates to:
  /// **'Chat'**
  String get channelWorkspaceChatTab;

  /// Channel workspace files tab label
  ///
  /// In en, this message translates to:
  /// **'Files'**
  String get channelWorkspaceFilesTab;

  /// Channel workspace boards tab label
  ///
  /// In en, this message translates to:
  /// **'Boards'**
  String get channelWorkspaceBoardsTab;

  /// Channel workspace calendar tab label
  ///
  /// In en, this message translates to:
  /// **'Calendar'**
  String get channelWorkspaceCalendarTab;

  /// Channel workspace meetings tab label
  ///
  /// In en, this message translates to:
  /// **'Meetings'**
  String get channelWorkspaceMeetingsTab;

  /// Title for the chat surface in a channel workspace
  ///
  /// In en, this message translates to:
  /// **'Channel chat'**
  String get channelWorkspaceChatTitle;

  /// Title for the files surface in a channel workspace
  ///
  /// In en, this message translates to:
  /// **'Channel files'**
  String get channelWorkspaceFilesTitle;

  /// Title for the boards/tasks surface in a channel workspace
  ///
  /// In en, this message translates to:
  /// **'Channel boards and tasks'**
  String get channelWorkspaceBoardsTitle;

  /// Title for the calendar surface in a channel workspace
  ///
  /// In en, this message translates to:
  /// **'Channel calendar'**
  String get channelWorkspaceCalendarTitle;

  /// Title for the meetings preview surface in a channel workspace
  ///
  /// In en, this message translates to:
  /// **'Channel meeting preview'**
  String get channelWorkspaceMeetingsTitle;

  /// Description for the channel chat surface
  ///
  /// In en, this message translates to:
  /// **'Messages remain the default live context for this channel.'**
  String get channelWorkspaceChatDescription;

  /// Description for the channel files preview placeholder
  ///
  /// In en, this message translates to:
  /// **'Channel file linking is a preview seam. Files will attach through the Weave files facade instead of raw provider paths once the backend contract is ready.'**
  String get channelWorkspaceFilesDescription;

  /// Description for the channel boards/tasks preview placeholder
  ///
  /// In en, this message translates to:
  /// **'Linked boards and tasks are shown as an honest preview until provider-neutral board state is connected to this channel context.'**
  String get channelWorkspaceBoardsDescription;

  /// Description for the gated channel calendar placeholder
  ///
  /// In en, this message translates to:
  /// **'Channel events stay gated until calendar scope capability is available for this workspace.'**
  String get channelWorkspaceCalendarDescription;

  /// Description for the gated channel meetings preview placeholder
  ///
  /// In en, this message translates to:
  /// **'Meetings attach to this channel\'s calendar, agenda, decisions, tasks, files, and follow-up evidence. LiveKit is the provider contract; join and start remain gated until backend tokens and encryption evidence are configured.'**
  String get channelWorkspaceMeetingsDescription;

  /// Title explaining that channel meetings are fail-closed until backend and encryption evidence exist
  ///
  /// In en, this message translates to:
  /// **'LiveKit readiness is fail-closed'**
  String get channelWorkspaceMeetingsCapabilityTitle;

  /// Body explaining honest fail-closed channel meeting capability state
  ///
  /// In en, this message translates to:
  /// **'LiveKit room creation and join tokens stay behind the backend facade. Join and start stay disabled until provider status, signaling/media paths, captions, recordings, and metadata boundaries have evidence. This preview does not claim that encrypted LiveKit calls are available.'**
  String get channelWorkspaceMeetingsCapabilityBody;

  /// Title for the explicit context rule in the channel meetings preview
  ///
  /// In en, this message translates to:
  /// **'Explicit meeting context only'**
  String get channelWorkspaceMeetingsPrivacyTitle;

  /// Privacy/context note for the channel meetings preview
  ///
  /// In en, this message translates to:
  /// **'Only context explicitly selected from {channelName} is prepared for a meeting. Weave does not continuously read, record, or transcribe the room in the background.'**
  String channelWorkspaceMeetingsPrivacyBody(String channelName);

  /// Status chip for recording/transcription off in channel meetings preview
  ///
  /// In en, this message translates to:
  /// **'Recording and transcription off'**
  String get channelWorkspaceMeetingsRecordingOff;

  /// Title for the channel meeting context pack preview
  ///
  /// In en, this message translates to:
  /// **'Context pack for this meeting'**
  String get channelWorkspaceMeetingsContextTitle;

  /// Body for the channel meeting context pack preview
  ///
  /// In en, this message translates to:
  /// **'A meeting pack is explicit and reviewable before anyone joins.'**
  String get channelWorkspaceMeetingsContextBody;

  /// Meeting context chip for agenda
  ///
  /// In en, this message translates to:
  /// **'Agenda'**
  String get channelWorkspaceMeetingsContextAgenda;

  /// Meeting context chip for files
  ///
  /// In en, this message translates to:
  /// **'Files'**
  String get channelWorkspaceMeetingsContextFiles;

  /// Meeting context chip for decisions
  ///
  /// In en, this message translates to:
  /// **'Decisions'**
  String get channelWorkspaceMeetingsContextDecisions;

  /// Meeting context chip for tasks
  ///
  /// In en, this message translates to:
  /// **'Tasks'**
  String get channelWorkspaceMeetingsContextTasks;

  /// Meeting context chip for follow-up evidence
  ///
  /// In en, this message translates to:
  /// **'Follow-up evidence'**
  String get channelWorkspaceMeetingsContextEvidence;

  /// Disabled join button in channel meetings preview
  ///
  /// In en, this message translates to:
  /// **'Join meeting'**
  String get channelWorkspaceMeetingsJoinButton;

  /// Disabled start button in channel meetings preview
  ///
  /// In en, this message translates to:
  /// **'Start meeting'**
  String get channelWorkspaceMeetingsStartButton;

  /// Disabled reason for meeting controls when backend capability is absent
  ///
  /// In en, this message translates to:
  /// **'Meeting provider capability is not available yet.'**
  String get channelWorkspaceMeetingsBackendUnavailableReason;

  /// Status label for an available channel workspace surface
  ///
  /// In en, this message translates to:
  /// **'Available'**
  String get channelWorkspaceStatusAvailable;

  /// Status label for a preview-only channel workspace surface
  ///
  /// In en, this message translates to:
  /// **'Preview contract'**
  String get channelWorkspaceStatusPreview;

  /// Status label for a gated channel workspace surface
  ///
  /// In en, this message translates to:
  /// **'Gated by capability'**
  String get channelWorkspaceStatusGated;

  /// Provider/domain seam label for a channel workspace surface
  ///
  /// In en, this message translates to:
  /// **'Provider seam: {contractId}'**
  String channelWorkspaceProviderContract(String contractId);

  /// Privacy/context note for a channel workspace surface
  ///
  /// In en, this message translates to:
  /// **'Only explicit context from {channelName} is shown here; Weave does not continuously read the room in the background.'**
  String channelWorkspaceExplicitContextNote(String channelName);

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

  /// Deprecated alias for the Weave Files connection card title
  ///
  /// In en, this message translates to:
  /// **'Weave Files'**
  String get filesNextcloudTitle;

  /// Section title for the Weave Files connection card
  ///
  /// In en, this message translates to:
  /// **'Weave Files'**
  String get filesProductTitle;

  /// Title for a Files card explaining the product/provider boundary
  ///
  /// In en, this message translates to:
  /// **'Weave product boundary'**
  String get filesProductBoundaryTitle;

  /// Body for a Files card explaining the product/provider boundary
  ///
  /// In en, this message translates to:
  /// **'Files actions use the Weave backend facade. Nextcloud remains the storage provider and admin/fallback surface; raw provider paths and credentials are not part of the normal Files UX.'**
  String get filesProductBoundaryBody;

  /// Button label used to start the Files connection flow
  ///
  /// In en, this message translates to:
  /// **'Connect Files'**
  String get filesConnectButton;

  /// Button label used to reconnect an invalid Files session
  ///
  /// In en, this message translates to:
  /// **'Reconnect Files'**
  String get filesReconnectButton;

  /// Button label used to disconnect the saved Files session
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

  /// Message shown when the Files screen is disconnected
  ///
  /// In en, this message translates to:
  /// **'Connect Weave Files to browse workspace files.'**
  String get filesDisconnectedMessage;

  /// Message shown when the saved Files session is no longer valid
  ///
  /// In en, this message translates to:
  /// **'Reconnect Files because the Weave session is no longer valid.'**
  String get filesInvalidSessionMessage;

  /// Message shown when the Files feature is missing required server setup
  ///
  /// In en, this message translates to:
  /// **'Finish Weave server setup before connecting files.'**
  String get filesMisconfiguredMessage;

  /// Status message shown when the Files feature is connected through Weave
  ///
  /// In en, this message translates to:
  /// **'Connected as {accountLabel}'**
  String filesConnectionConnected(String accountLabel);

  /// Status message shown when Files are not connected
  ///
  /// In en, this message translates to:
  /// **'Files are not connected for this Weave session.'**
  String get filesConnectionDisconnected;

  /// Status message shown when the Files session is invalid
  ///
  /// In en, this message translates to:
  /// **'The Weave Files session needs attention.'**
  String get filesConnectionInvalid;

  /// Status message shown when Weave Files server setup is incomplete
  ///
  /// In en, this message translates to:
  /// **'Server setup is incomplete for Weave Files.'**
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

  /// Settings section title for personal theme selection
  ///
  /// In en, this message translates to:
  /// **'Appearance'**
  String get settingsThemeTitle;

  /// Settings description explaining personal theme selection and workspace defaults
  ///
  /// In en, this message translates to:
  /// **'Choose the visual style for this profile. Workspace brand defaults stay separate, so your personal choice is not overwritten by admin setup.'**
  String get settingsThemeDescription;

  /// Theme option title for system/default mode
  ///
  /// In en, this message translates to:
  /// **'Use device setting'**
  String get settingsThemeSystemTitle;

  /// Theme option description for system/default mode
  ///
  /// In en, this message translates to:
  /// **'Follow your device light or dark appearance.'**
  String get settingsThemeSystemDescription;

  /// Theme option title for light mode
  ///
  /// In en, this message translates to:
  /// **'Light'**
  String get settingsThemeLightTitle;

  /// Theme option description for light mode
  ///
  /// In en, this message translates to:
  /// **'Use a bright professional palette.'**
  String get settingsThemeLightDescription;

  /// Theme option title for dark mode
  ///
  /// In en, this message translates to:
  /// **'Dark'**
  String get settingsThemeDarkTitle;

  /// Theme option description for dark mode
  ///
  /// In en, this message translates to:
  /// **'Use a darker palette for low-light work.'**
  String get settingsThemeDarkDescription;

  /// Theme option title for high-contrast mode
  ///
  /// In en, this message translates to:
  /// **'High contrast'**
  String get settingsThemeHighContrastTitle;

  /// Theme option description for high-contrast mode
  ///
  /// In en, this message translates to:
  /// **'Use stronger contrast while still following your device light or dark appearance.'**
  String get settingsThemeHighContrastDescription;

  /// Loading state for theme preference settings
  ///
  /// In en, this message translates to:
  /// **'Loading appearance preferences…'**
  String get settingsThemeLoading;

  /// Error shown when theme preferences cannot load or save
  ///
  /// In en, this message translates to:
  /// **'Appearance preferences could not be saved. Try changing the setting again.'**
  String get settingsThemeError;

  /// Title for the settings entry point to in-app help
  ///
  /// In en, this message translates to:
  /// **'Help and user handbook'**
  String get settingsHelpTitle;

  /// Description for the settings entry point to in-app help
  ///
  /// In en, this message translates to:
  /// **'Open practical guidance for using Weave, recovering from issues, and understanding privacy basics.'**
  String get settingsHelpDescription;

  /// Title for the in-app help screen app bar
  ///
  /// In en, this message translates to:
  /// **'Help'**
  String get helpScreenTitle;

  /// Hero title for the in-app user handbook
  ///
  /// In en, this message translates to:
  /// **'User handbook'**
  String get helpHandbookTitle;

  /// Introductory text for the in-app user handbook
  ///
  /// In en, this message translates to:
  /// **'This handbook explains the everyday Weave app in plain language. It is available offline with the app and will grow as more surfaces become ready.'**
  String get helpHandbookDescription;

  /// Help section title explaining the product
  ///
  /// In en, this message translates to:
  /// **'What Weave is'**
  String get helpWhatIsWeaveTitle;

  /// Help copy explaining what Weave is
  ///
  /// In en, this message translates to:
  /// **'Weave is a collaboration app for teams that want one accessible workspace without giving up data sovereignty. Chat, files, account settings, and future collaboration modules are presented through Weave while open services such as Matrix, Nextcloud, Keycloak, and the Weave backend work behind the scenes.'**
  String get helpWhatIsWeaveBody;

  /// Help section title for sign-in basics
  ///
  /// In en, this message translates to:
  /// **'Sign in basics'**
  String get helpSignInTitle;

  /// Help copy for sign-in basics and recovery
  ///
  /// In en, this message translates to:
  /// **'Use the workspace address provided by your admin, then sign in once with Weave SSO. You should not need separate Matrix or Nextcloud passwords for normal use. If sign-in loops or fails, check your connection, confirm the server address in Settings, and ask an admin whether your invite or account is active.'**
  String get helpSignInBody;

  /// Help section title for chat
  ///
  /// In en, this message translates to:
  /// **'Chat'**
  String get helpChatTitle;

  /// Help copy for chat basics
  ///
  /// In en, this message translates to:
  /// **'Chat is the daily place for rooms and messages. Open Chat from the main navigation, connect Matrix if asked, then choose a room. Weave keeps room and recovery states visible so you can see when chat is connected, waiting, degraded, or needs admin attention.'**
  String get helpChatBody;

  /// Help section title for files
  ///
  /// In en, this message translates to:
  /// **'Files'**
  String get helpFilesTitle;

  /// Help copy for files basics
  ///
  /// In en, this message translates to:
  /// **'Files lets you browse workspace documents through the Weave app. Open Files from the main navigation, move through folders, and retry if the folder could not refresh. The underlying storage is provided by your workspace services, but everyday browsing should stay inside Weave.'**
  String get helpFilesBody;

  /// Help section title for settings and account basics
  ///
  /// In en, this message translates to:
  /// **'Settings, account, and session'**
  String get helpSettingsTitle;

  /// Help copy for settings, account, and session basics
  ///
  /// In en, this message translates to:
  /// **'Settings shows your profile summary, workspace readiness, server configuration, Matrix security information, and sign-out control. Use it to check whether Chat, Files, Calendar, or other modules are ready, and sign out before handing a device to someone else.'**
  String get helpSettingsBody;

  /// Help section title for gated Calendar and Boards availability
  ///
  /// In en, this message translates to:
  /// **'Calendar and Boards availability'**
  String get helpCalendarBoardsTitle;

  /// Help copy honestly explaining gated Calendar and Boards availability
  ///
  /// In en, this message translates to:
  /// **'Calendar and Boards are active Weave product scope, but they may be hidden or marked unavailable until your workspace has the required backend contracts and feature gates enabled. If they are not visible in navigation, use Chat, Files, and Settings for now and watch workspace readiness for changes.'**
  String get helpCalendarBoardsBody;

  /// Help section title for troubleshooting and recovery
  ///
  /// In en, this message translates to:
  /// **'Troubleshooting and recovery'**
  String get helpTroubleshootingTitle;

  /// Help copy for troubleshooting and recovery states
  ///
  /// In en, this message translates to:
  /// **'When something does not load, use Retry first. If a stale chat room list or folder remains visible, Weave is preserving your place while refresh fails. Persistent setup, sign-in, Matrix, files, or backend errors should be shared with your admin together with the visible message and the server address from Settings.'**
  String get helpTroubleshootingBody;

  /// Help section title for privacy and security basics
  ///
  /// In en, this message translates to:
  /// **'Privacy and security basics'**
  String get helpPrivacySecurityTitle;

  /// Help copy for privacy and security basics
  ///
  /// In en, this message translates to:
  /// **'Your workspace controls its own services and data. Weave uses SSO for access and shows Matrix security status honestly. Do not assume chat is fully end-to-end encrypted unless Weave says the Matrix encryption, recovery, and device-trust gates are healthy. Keep recovery keys in a safe place and report lost devices to your admin.'**
  String get helpPrivacySecurityBody;

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

  /// Matrix E2EE diagnostics card title explaining backend and agent boundaries
  ///
  /// In en, this message translates to:
  /// **'Backend and agent boundary'**
  String get chatSecurityBoundaryCardTitle;

  /// Matrix E2EE diagnostics card status for agent or connector participation
  ///
  /// In en, this message translates to:
  /// **'Blocked until consent/audit'**
  String get chatSecurityBoundaryCardValue;

  /// Matrix E2EE diagnostics card body explaining server-readable metadata and bot connector limits
  ///
  /// In en, this message translates to:
  /// **'Encrypted message contents stay on Matrix devices. Backend diagnostics may use support-safe metadata such as room ID, encryption status, device trust, and timestamps, but not decrypted message bodies. Bots and connectors stay blocked from encrypted rooms until consent, audit, device-trust, and client-identity gates are implemented.'**
  String get chatSecurityBoundaryCardBody;

  /// Matrix E2EE recovery checklist card title
  ///
  /// In en, this message translates to:
  /// **'Device recovery checklist'**
  String get chatSecurityRecoveryGuidanceCardTitle;

  /// Matrix E2EE recovery checklist status when user action is required
  ///
  /// In en, this message translates to:
  /// **'Action required'**
  String get chatSecurityRecoveryGuidanceValueActionRequired;

  /// Matrix E2EE recovery checklist status when recovery and verification are healthy
  ///
  /// In en, this message translates to:
  /// **'Ready for device changes'**
  String get chatSecurityRecoveryGuidanceValueReady;

  /// Intro text for Matrix E2EE device recovery checklist
  ///
  /// In en, this message translates to:
  /// **'Before relying on encrypted chat across devices:'**
  String get chatSecurityRecoveryGuidanceIntro;

  /// Matrix E2EE recovery checklist item about saving recovery material
  ///
  /// In en, this message translates to:
  /// **'Save the recovery key or passphrase outside Weave, preferably in a password manager.'**
  String get chatSecurityRecoveryGuidanceSaveRecovery;

  /// Matrix E2EE recovery checklist item about device verification
  ///
  /// In en, this message translates to:
  /// **'Verify this device with another signed-in Matrix device when possible.'**
  String get chatSecurityRecoveryGuidanceVerifyDevice;

  /// Matrix E2EE recovery checklist item about new device recovery order
  ///
  /// In en, this message translates to:
  /// **'On a new or reinstalled device, use the recovery key first, then verify the device.'**
  String get chatSecurityRecoveryGuidanceNewDevice;

  /// Matrix E2EE recovery checklist item about lost device handling
  ///
  /// In en, this message translates to:
  /// **'If a device is lost, remove or distrust it from Matrix before trusting new encrypted rooms.'**
  String get chatSecurityRecoveryGuidanceLostDevice;

  /// Matrix E2EE recovery checklist item explaining server recovery limits
  ///
  /// In en, this message translates to:
  /// **'Weave servers can report safe metadata, but cannot recover encrypted message contents for you.'**
  String get chatSecurityRecoveryGuidanceServerCannotRecover;

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

  /// Title for provider stack readiness summary in settings
  ///
  /// In en, this message translates to:
  /// **'Provider stack readiness'**
  String get settingsProviderStackTitle;

  /// Accessibility label for provider readiness summary
  ///
  /// In en, this message translates to:
  /// **'Provider stack readiness. Backend-owned facades: {backendOwnedFacades}. Flutter provider calls: {flutterCalls}.'**
  String settingsProviderStackSemanticLabel(
    String backendOwnedFacades,
    String flutterCalls,
  );

  /// Provider stack description when backend facade and fail-closed rules are satisfied
  ///
  /// In en, this message translates to:
  /// **'Provider integrations stay fail-closed behind backend-owned facades. Flutter does not call Nextcloud, Matrix auth, OpenProject, Office, or other provider APIs directly.'**
  String get settingsProviderStackFailClosedDescription;

  /// Provider stack description when readiness needs review
  ///
  /// In en, this message translates to:
  /// **'Provider readiness needs review before enabling direct launch or workspace actions.'**
  String get settingsProviderStackNeedsReviewDescription;

  /// Pill label for backend-owned provider facades
  ///
  /// In en, this message translates to:
  /// **'Backend facades'**
  String get settingsProviderStackBackendFacadesLabel;

  /// Pill label for whether Flutter calls provider APIs directly
  ///
  /// In en, this message translates to:
  /// **'Flutter provider calls'**
  String get settingsProviderStackFlutterCallsLabel;

  /// Pill label for support-safe provider diagnostics
  ///
  /// In en, this message translates to:
  /// **'Support safety'**
  String get settingsProviderStackSupportSafetyLabel;

  /// Value when provider facades are backend-owned
  ///
  /// In en, this message translates to:
  /// **'Owned by backend'**
  String get settingsProviderStackOwned;

  /// Value when provider facades are missing
  ///
  /// In en, this message translates to:
  /// **'Missing'**
  String get settingsProviderStackMissing;

  /// Value when provider stack needs review
  ///
  /// In en, this message translates to:
  /// **'Needs review'**
  String get settingsProviderStackNeedsReview;

  /// Value when direct provider calls are blocked
  ///
  /// In en, this message translates to:
  /// **'Blocked'**
  String get settingsProviderStackBlocked;

  /// Value when provider diagnostics are support-safe/redacted
  ///
  /// In en, this message translates to:
  /// **'Redacted'**
  String get settingsProviderStackRedacted;

  /// Short yes value in provider stack semantics
  ///
  /// In en, this message translates to:
  /// **'Yes'**
  String get settingsProviderStackYes;

  /// Short no value in provider stack semantics
  ///
  /// In en, this message translates to:
  /// **'No'**
  String get settingsProviderStackNo;

  /// Semantics value when Flutter direct provider calls are allowed
  ///
  /// In en, this message translates to:
  /// **'Allowed'**
  String get settingsProviderStackFlutterCallsAllowed;

  /// Semantics value when Flutter direct provider calls are blocked
  ///
  /// In en, this message translates to:
  /// **'Blocked'**
  String get settingsProviderStackFlutterCallsBlocked;

  /// Provider status badge for fail-closed behavior
  ///
  /// In en, this message translates to:
  /// **'fail-closed'**
  String get settingsProviderStackFailClosedBadge;

  /// Provider status badge for read-only behavior
  ///
  /// In en, this message translates to:
  /// **'read-only'**
  String get settingsProviderStackReadOnlyBadge;

  /// Provider status badge for paid feature requirements
  ///
  /// In en, this message translates to:
  /// **'paid features required'**
  String get settingsProviderStackPaidFeaturesRequiredBadge;

  /// Provider module label for identity realm
  ///
  /// In en, this message translates to:
  /// **'Identity realm'**
  String get settingsProviderModuleIdentityRealm;

  /// Provider module label for source control
  ///
  /// In en, this message translates to:
  /// **'Source control'**
  String get settingsProviderModuleSourceControl;

  /// Provider module label for issue tracker
  ///
  /// In en, this message translates to:
  /// **'Issue tracker'**
  String get settingsProviderModuleIssueTracker;

  /// Provider module label for CI
  ///
  /// In en, this message translates to:
  /// **'CI'**
  String get settingsProviderModuleCi;

  /// Provider module label for releases
  ///
  /// In en, this message translates to:
  /// **'Release'**
  String get settingsProviderModuleRelease;

  /// Provider module label for Office
  ///
  /// In en, this message translates to:
  /// **'Office'**
  String get settingsProviderModuleOffice;

  /// Provider module label for files
  ///
  /// In en, this message translates to:
  /// **'Files'**
  String get settingsProviderModuleFiles;

  /// Provider module label for calendar
  ///
  /// In en, this message translates to:
  /// **'Calendar'**
  String get settingsProviderModuleCalendar;

  /// Provider module label for contacts
  ///
  /// In en, this message translates to:
  /// **'Contacts'**
  String get settingsProviderModuleContacts;

  /// Provider module label for forms
  ///
  /// In en, this message translates to:
  /// **'Forms'**
  String get settingsProviderModuleForms;

  /// Provider module label for Matrix/Synapse chat
  ///
  /// In en, this message translates to:
  /// **'Matrix chat'**
  String get settingsProviderModuleMatrix;

  /// Provider module label for Matrix Authentication Service
  ///
  /// In en, this message translates to:
  /// **'Matrix auth'**
  String get settingsProviderModuleMatrixAuth;

  /// Provider module label for meetings and video calls
  ///
  /// In en, this message translates to:
  /// **'Meetings'**
  String get settingsProviderModuleMeetings;

  /// Provider module label for boards
  ///
  /// In en, this message translates to:
  /// **'Boards'**
  String get settingsProviderModuleBoards;

  /// Fallback provider module label
  ///
  /// In en, this message translates to:
  /// **'Provider'**
  String get settingsProviderModuleProvider;

  /// Provider state label for disabled providers
  ///
  /// In en, this message translates to:
  /// **'disabled'**
  String get settingsProviderStateDisabled;

  /// Provider state label for unconfigured providers
  ///
  /// In en, this message translates to:
  /// **'unconfigured'**
  String get settingsProviderStateNotConfigured;

  /// Provider state label for configured providers
  ///
  /// In en, this message translates to:
  /// **'configured'**
  String get settingsProviderStateConfigured;

  /// Provider state label for ready providers
  ///
  /// In en, this message translates to:
  /// **'ready'**
  String get settingsProviderStateReady;

  /// Provider state label for degraded providers
  ///
  /// In en, this message translates to:
  /// **'degraded'**
  String get settingsProviderStateDegraded;

  /// Provider state label for unsupported providers
  ///
  /// In en, this message translates to:
  /// **'unsupported'**
  String get settingsProviderStateUnsupported;

  /// Provider state label for unknown provider state
  ///
  /// In en, this message translates to:
  /// **'unknown'**
  String get settingsProviderStateUnknown;

  /// Title for Office provider readiness summary
  ///
  /// In en, this message translates to:
  /// **'Office readiness'**
  String get settingsOfficeReadinessTitle;

  /// Accessibility label for Office readiness summary
  ///
  /// In en, this message translates to:
  /// **'Office readiness. Launch is {launchState}. Provider is {providerState}.'**
  String settingsOfficeReadinessSemanticLabel(
    String launchState,
    String providerState,
  );

  /// Office readiness description when launch is fail-closed
  ///
  /// In en, this message translates to:
  /// **'Office launch is fail-closed until a backend-owned provider adapter, session tokens, callbacks, and permissions are configured.'**
  String get settingsOfficeReadinessFailClosedDescription;

  /// Office readiness description when launch is available
  ///
  /// In en, this message translates to:
  /// **'Office launch is available through the backend facade.'**
  String get settingsOfficeReadinessAvailableDescription;

  /// Office launch availability value
  ///
  /// In en, this message translates to:
  /// **'available'**
  String get settingsOfficeReadinessAvailable;

  /// Office provider enabled value
  ///
  /// In en, this message translates to:
  /// **'enabled'**
  String get settingsOfficeReadinessEnabled;

  /// Office readiness value when no launch modes are available
  ///
  /// In en, this message translates to:
  /// **'no launch modes'**
  String get settingsOfficeReadinessNoLaunchModes;

  /// Enabled Office launch modes
  ///
  /// In en, this message translates to:
  /// **'modes: {modes}'**
  String settingsOfficeReadinessModes(String modes);

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

  /// Accessibility hint for channel conversation tiles that open the channel workspace
  ///
  /// In en, this message translates to:
  /// **'Opens channel workspace'**
  String get chatConversationOpensChannelWorkspaceLabel;

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

  /// Heading for the explicit context preview shown in a chat room
  ///
  /// In en, this message translates to:
  /// **'Context for this room'**
  String get chatRoomContextPackTitle;

  /// Description for the explicit context preview shown in a chat room
  ///
  /// In en, this message translates to:
  /// **'Weave will only include scoped context that you can see here, such as this room, selected files, linked tasks, and recent decisions.'**
  String get chatRoomContextPackDescription;

  /// Accessibility summary of included and available context sources in a room
  ///
  /// In en, this message translates to:
  /// **'{includedCount, plural, =0 {No sources included} =1 {1 source included} other {{includedCount} sources included}}. {availableCount, plural, =0 {No optional sources available} =1 {1 optional source available} other {{availableCount} optional sources available}}.'**
  String chatRoomContextPackCounts(int includedCount, int availableCount);

  /// Safety note for the explicit context preview in a chat room
  ///
  /// In en, this message translates to:
  /// **'No agent is reading this room in the background.'**
  String get chatRoomContextPackNoBackgroundReading;

  /// Context chip label for the currently opened chat room
  ///
  /// In en, this message translates to:
  /// **'Current room'**
  String get chatRoomContextCurrentRoomLabel;

  /// Context chip label for files explicitly selected by the user
  ///
  /// In en, this message translates to:
  /// **'Selected files'**
  String get chatRoomContextSelectedFilesLabel;

  /// Context chip label for tasks linked to a room
  ///
  /// In en, this message translates to:
  /// **'Linked tasks'**
  String get chatRoomContextLinkedTasksLabel;

  /// Context chip label for recent decisions linked to a room
  ///
  /// In en, this message translates to:
  /// **'Recent decisions'**
  String get chatRoomContextRecentDecisionsLabel;

  /// Accessibility status for context that is included in the preview
  ///
  /// In en, this message translates to:
  /// **'Included'**
  String get chatRoomContextIncludedStatus;

  /// Accessibility status for optional context that is not included yet
  ///
  /// In en, this message translates to:
  /// **'Available when selected'**
  String get chatRoomContextAvailableStatus;

  /// Heading for the room decision and evidence snapshot panel
  ///
  /// In en, this message translates to:
  /// **'Decisions, risks, questions, and evidence'**
  String get chatDecisionEvidencePanelTitle;

  /// Description for the explicit decision and evidence capture panel
  ///
  /// In en, this message translates to:
  /// **'Capture important messages explicitly so the room keeps the reason behind the work. Nothing here is created by hidden room scanning.'**
  String get chatDecisionEvidencePanelDescription;

  /// Safety note for explicit decision and evidence records
  ///
  /// In en, this message translates to:
  /// **'Records come from message actions you choose; no automatic continuous room reading is running.'**
  String get chatDecisionEvidenceNoBackgroundReading;

  /// Empty state for a room with no captured decision or evidence records
  ///
  /// In en, this message translates to:
  /// **'No records captured yet. Use a message action to capture a decision, risk, open question, or evidence with its source.'**
  String get chatDecisionEvidenceEmptyState;

  /// Count label for a decision/evidence kind
  ///
  /// In en, this message translates to:
  /// **'{label}: {count}'**
  String chatDecisionEvidenceCountLabel(String label, int count);

  /// Singular label for a captured decision
  ///
  /// In en, this message translates to:
  /// **'Decision'**
  String get chatDecisionEvidenceDecisionLabel;

  /// Plural label for captured decisions
  ///
  /// In en, this message translates to:
  /// **'Decisions'**
  String get chatDecisionEvidenceDecisionsLabel;

  /// Singular label for a captured risk
  ///
  /// In en, this message translates to:
  /// **'Risk'**
  String get chatDecisionEvidenceRiskLabel;

  /// Plural label for captured risks
  ///
  /// In en, this message translates to:
  /// **'Risks'**
  String get chatDecisionEvidenceRisksLabel;

  /// Singular label for a captured open question
  ///
  /// In en, this message translates to:
  /// **'Open question'**
  String get chatDecisionEvidenceOpenQuestionLabel;

  /// Plural label for captured open questions
  ///
  /// In en, this message translates to:
  /// **'Open questions'**
  String get chatDecisionEvidenceOpenQuestionsLabel;

  /// Singular label for captured evidence
  ///
  /// In en, this message translates to:
  /// **'Evidence'**
  String get chatDecisionEvidenceEvidenceLabel;

  /// Plural label for captured evidence
  ///
  /// In en, this message translates to:
  /// **'Evidence'**
  String get chatDecisionEvidenceEvidencePluralLabel;

  /// Owner label for records captured by the current user
  ///
  /// In en, this message translates to:
  /// **'You'**
  String get chatDecisionEvidenceOwnerYou;

  /// Lifecycle status for active decision/evidence records
  ///
  /// In en, this message translates to:
  /// **'Active'**
  String get chatDecisionEvidenceStatusActive;

  /// Lifecycle status for resolved decision/evidence records
  ///
  /// In en, this message translates to:
  /// **'Resolved'**
  String get chatDecisionEvidenceStatusResolved;

  /// Lifecycle status for archived decision/evidence records
  ///
  /// In en, this message translates to:
  /// **'Archived'**
  String get chatDecisionEvidenceStatusArchived;

  /// Metadata shown for a captured decision/evidence record
  ///
  /// In en, this message translates to:
  /// **'{status}. Captured by {owner}. Source: message from {sender}.'**
  String chatDecisionEvidenceRecordMeta(
    String status,
    String owner,
    String sender,
  );

  /// Short source label for a captured decision/evidence record
  ///
  /// In en, this message translates to:
  /// **'Source: message from {sender}'**
  String chatDecisionEvidenceSourceLabel(String sender);

  /// Snackbar shown after a message is captured as a decision/evidence record
  ///
  /// In en, this message translates to:
  /// **'Captured as {kind}. Source linked to this message.'**
  String chatDecisionEvidenceCapturedMessage(String kind);

  /// Summary shown when the panel hides additional records
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1 {1 more record} other {{count} more records}}'**
  String chatDecisionEvidenceMoreRecords(int count);

  /// Message menu action to capture a message as a decision
  ///
  /// In en, this message translates to:
  /// **'Capture as decision'**
  String get chatDecisionEvidenceCaptureDecisionAction;

  /// Message menu action to capture a message as a risk
  ///
  /// In en, this message translates to:
  /// **'Capture as risk'**
  String get chatDecisionEvidenceCaptureRiskAction;

  /// Message menu action to capture a message as an open question
  ///
  /// In en, this message translates to:
  /// **'Capture as open question'**
  String get chatDecisionEvidenceCaptureQuestionAction;

  /// Message menu action to capture a message as evidence
  ///
  /// In en, this message translates to:
  /// **'Capture as evidence'**
  String get chatDecisionEvidenceCaptureEvidenceAction;

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

  /// Label for the Weave Context/Space id in the event details dialog
  ///
  /// In en, this message translates to:
  /// **'Context'**
  String get calendarDetailsContextLabel;

  /// Label for meeting thread metadata in the event details dialog
  ///
  /// In en, this message translates to:
  /// **'Meeting thread'**
  String get calendarDetailsMeetingThreadLabel;

  /// Calendar details text when the backend exposes safe context metadata but not a Matrix thread id
  ///
  /// In en, this message translates to:
  /// **'Safe context metadata is available; chat thread linkage is not configured yet.'**
  String get calendarDetailsMeetingThreadPending;

  /// Label for safe attendee metadata in the calendar event details dialog
  ///
  /// In en, this message translates to:
  /// **'Attendees'**
  String get calendarDetailsAttendeesLabel;

  /// Label for safe provider metadata in the calendar event details dialog
  ///
  /// In en, this message translates to:
  /// **'Provider reference'**
  String get calendarDetailsProviderLabel;

  /// Calendar details text explaining that raw provider paths are not exposed
  ///
  /// In en, this message translates to:
  /// **'raw provider path hidden'**
  String get calendarDetailsProviderPathHidden;

  /// Label for the last known calendar event update timestamp in the details dialog
  ///
  /// In en, this message translates to:
  /// **'Updated'**
  String get calendarDetailsUpdatedLabel;

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

  /// Title for the owner/admin setup card in settings
  ///
  /// In en, this message translates to:
  /// **'Owner and admin setup'**
  String get settingsAdminSetupTitle;

  /// Description for owner/admin setup responsibilities
  ///
  /// In en, this message translates to:
  /// **'Workspace owners and admins manage OIDC, realm, organization, and service endpoints here. Members and guests only see sign-in and product settings.'**
  String get settingsAdminSetupDescription;

  /// Title confirming admin controls are visible
  ///
  /// In en, this message translates to:
  /// **'Admin controls unlocked'**
  String get settingsAdminPermissionTitle;

  /// Admin permission explanation with role list
  ///
  /// In en, this message translates to:
  /// **'Visible because your Weave roles are: {roles}. Backend APIs remain the authority for every write.'**
  String settingsAdminPermissionDescription(String roles);

  /// Screen-reader label for admin permission summary
  ///
  /// In en, this message translates to:
  /// **'Admin controls unlocked. Visible because your Weave roles are: {roles}. Backend APIs remain the authority for every write.'**
  String settingsAdminPermissionSemantic(String roles);

  /// Title for hidden admin setup boundary card
  ///
  /// In en, this message translates to:
  /// **'Workspace setup is admin-only'**
  String get settingsAdminBoundaryTitle;

  /// Explanation shown to non-admin users instead of server configuration controls
  ///
  /// In en, this message translates to:
  /// **'OIDC, realm, organization, and service endpoint setup is handled by workspace owners or admins. Normal users can keep using Weave without Matrix, Nextcloud, or realm details.'**
  String get settingsAdminBoundaryDescription;

  /// Loading label while profile roles are being resolved
  ///
  /// In en, this message translates to:
  /// **'Checking admin permissions…'**
  String get settingsAdminPermissionLoading;

  /// Title for the implicit context card on Weave Home
  ///
  /// In en, this message translates to:
  /// **'Context for this workspace'**
  String get chatContextCardTitle;

  /// Explanation for context-aware chat UX
  ///
  /// In en, this message translates to:
  /// **'Weave can prepare focused context from channels, decisions, and shared work when you ask for help. It does not show a database diagram or continuously read everything.'**
  String get chatContextCardDescription;

  /// Agent context privacy policy copy
  ///
  /// In en, this message translates to:
  /// **'Agents use scoped context on demand, show what context was used, and stay inside admin-defined boundaries.'**
  String get chatContextCardPolicy;

  /// Context hint title for channel history
  ///
  /// In en, this message translates to:
  /// **'Channel context'**
  String get chatContextChannelHintTitle;

  /// Context hint body for channel history
  ///
  /// In en, this message translates to:
  /// **'Recent room signals can become a small context card for the current task.'**
  String get chatContextChannelHintDescription;

  /// Context hint title for decisions/evidence
  ///
  /// In en, this message translates to:
  /// **'Decisions and evidence'**
  String get chatContextEvidenceHintTitle;

  /// Context hint body for decision/evidence layer
  ///
  /// In en, this message translates to:
  /// **'Decision notes and supporting links can be cited without exposing graph internals.'**
  String get chatContextEvidenceHintDescription;

  /// Context hint title for agent context packs
  ///
  /// In en, this message translates to:
  /// **'Agent context packs'**
  String get chatContextAgentHintTitle;

  /// Context hint body for scoped agent context packs
  ///
  /// In en, this message translates to:
  /// **'Assistants receive only the scoped pack for the request, mention, or schedule.'**
  String get chatContextAgentHintDescription;

  /// Title for first-run admin setup guidance
  ///
  /// In en, this message translates to:
  /// **'Owner/admin setup responsibilities'**
  String get firstRunAdminSetupTitle;

  /// First-run guidance for owner/admin users
  ///
  /// In en, this message translates to:
  /// **'Your role can administer workspace setup. Keep OIDC, realm, organization, invite, and service endpoint changes here or in Settings; normal users should only need one Weave sign-in.'**
  String get firstRunAdminSetupDescription;

  /// Settings card title for AI agent capability governance
  ///
  /// In en, this message translates to:
  /// **'AI agent capability governance'**
  String get agentCapabilityPolicyTitle;

  /// Admin-facing description for AI agent capability governance
  ///
  /// In en, this message translates to:
  /// **'Owners and admins decide which agent packages and connectors can be used. This preview stays off until permission, consent, and audit controls are connected.'**
  String get agentCapabilityPolicyAdminDescription;

  /// Member-facing description for AI agent capability governance
  ///
  /// In en, this message translates to:
  /// **'AI agent chats are not enabled for this workspace yet. You can keep using Weave normally; an owner or admin must turn this on first.'**
  String get agentCapabilityPolicyUserDescription;

  /// Fail-closed notice when policy cannot be trusted
  ///
  /// In en, this message translates to:
  /// **'Agent capabilities are blocked until Weave can confirm your role and the workspace policy.'**
  String get agentCapabilityPolicyFailClosedNotice;

  /// Disabled button label for future agent capability management
  ///
  /// In en, this message translates to:
  /// **'Management unavailable in this preview'**
  String get agentCapabilityPolicyManageDisabledButton;

  /// Hint for non-admin users
  ///
  /// In en, this message translates to:
  /// **'Need an agent for your team? Ask a workspace owner or admin to review agent capabilities when they are available.'**
  String get agentCapabilityPolicyAskAdminHint;

  /// Admin state hint for disabled agent capabilities
  ///
  /// In en, this message translates to:
  /// **'Current state: off by default. Future controls will require owner/admin review before users can start an agent.'**
  String get agentCapabilityPolicyAdminStateHint;

  /// Agent capability title for personal assistant
  ///
  /// In en, this message translates to:
  /// **'Personal assistant'**
  String get agentCapabilityPersonalAssistantTitle;

  /// Agent capability description for personal assistant
  ///
  /// In en, this message translates to:
  /// **'Will only use context you choose for a request, after your workspace enables the capability.'**
  String get agentCapabilityPersonalAssistantDescription;

  /// Agent capability title for channel agent
  ///
  /// In en, this message translates to:
  /// **'Channel agent'**
  String get agentCapabilityChannelAgentTitle;

  /// Agent capability description for channel agent
  ///
  /// In en, this message translates to:
  /// **'Requires an owner or admin to choose which channels, files, calendar items, or boards the agent may use.'**
  String get agentCapabilityChannelAgentDescription;

  /// Agent capability availability label for preview-only state
  ///
  /// In en, this message translates to:
  /// **'Preview only'**
  String get agentCapabilityAvailabilityPreviewOnly;

  /// Agent capability availability label when admin setup is required
  ///
  /// In en, this message translates to:
  /// **'Admin setup required'**
  String get agentCapabilityAvailabilityAdminSetupRequired;

  /// Agent capability availability label for blocked state
  ///
  /// In en, this message translates to:
  /// **'Blocked'**
  String get agentCapabilityAvailabilityBlocked;

  /// Error message for AI agent capability policy settings section
  ///
  /// In en, this message translates to:
  /// **'Agent capability policy is unavailable.'**
  String get agentCapabilityPolicyErrorTitle;

  /// Loading message for AI agent capability policy settings section
  ///
  /// In en, this message translates to:
  /// **'Checking agent capability policy…'**
  String get agentCapabilityPolicyLoading;

  /// Title for the context-driven workflow preview panel
  ///
  /// In en, this message translates to:
  /// **'Active workflows'**
  String get workflowPreviewTitle;

  /// Description for accessible workflow preview panel
  ///
  /// In en, this message translates to:
  /// **'A linear view of current steps, owners, blockers, and evidence. Diagrams can come later; this view must work with keyboard and screen readers first.'**
  String get workflowPreviewDescription;

  /// Chip saying workflows are shown linearly first
  ///
  /// In en, this message translates to:
  /// **'Linear view first'**
  String get workflowPreviewLinearViewChip;

  /// Chip saying workflow context is explicit
  ///
  /// In en, this message translates to:
  /// **'Explicit context only'**
  String get workflowPreviewExplicitContextChip;

  /// Chip saying workflow actions are governed
  ///
  /// In en, this message translates to:
  /// **'Governed actions'**
  String get workflowPreviewGovernedActionsChip;

  /// Safety note for workflow context collection
  ///
  /// In en, this message translates to:
  /// **'Workflow context is attached deliberately; Weave does not continuously read rooms in the background.'**
  String get workflowPreviewNoBackgroundReading;

  /// Screen-reader summary for workflow preview panel
  ///
  /// In en, this message translates to:
  /// **'{workflowCount, plural, =0{No active workflows} one{1 active workflow} other{{workflowCount} active workflows}}. {activeStepCount, plural, =0{No active steps} one{1 active step} other{{activeStepCount} active steps}}. {blockerCount, plural, =0{No blockers} one{1 blocker} other{{blockerCount} blockers}}.'**
  String workflowPreviewSemanticSummary(
    int workflowCount,
    int activeStepCount,
    int blockerCount,
  );

  /// Screen-reader summary for one workflow run
  ///
  /// In en, this message translates to:
  /// **'Workflow {title}. Context {context}. {stepCount, plural, one{1 step} other{{stepCount} steps}}. {blockerCount, plural, =0{No blockers} one{1 blocker} other{{blockerCount} blockers}}.'**
  String workflowPreviewRunSemantic(
    String title,
    String context,
    int stepCount,
    int blockerCount,
  );

  /// Workflow context label
  ///
  /// In en, this message translates to:
  /// **'Context: {context}'**
  String workflowPreviewContextLabel(String context);

  /// Workflow next action text
  ///
  /// In en, this message translates to:
  /// **'Next action: {stepTitle} — {action}'**
  String workflowPreviewNextAction(String stepTitle, String action);

  /// Screen-reader label for a workflow step
  ///
  /// In en, this message translates to:
  /// **'Step {title}. Type {kind}. Status {status}. Owner {owner}. Due {due}. Next action {nextAction}. Blocked: {blockers}. Evidence: {evidence}.'**
  String workflowPreviewStepSemantic(
    String title,
    String kind,
    String status,
    String owner,
    String due,
    String nextAction,
    String blockers,
    String evidence,
  );

  /// Workflow step kind label
  ///
  /// In en, this message translates to:
  /// **'Step'**
  String get workflowPreviewKindStep;

  /// Workflow gate kind label
  ///
  /// In en, this message translates to:
  /// **'Gate'**
  String get workflowPreviewKindGate;

  /// Workflow approval kind label
  ///
  /// In en, this message translates to:
  /// **'Approval'**
  String get workflowPreviewKindApproval;

  /// Workflow ready status
  ///
  /// In en, this message translates to:
  /// **'Ready'**
  String get workflowPreviewStatusReady;

  /// Workflow in-progress status
  ///
  /// In en, this message translates to:
  /// **'In progress'**
  String get workflowPreviewStatusInProgress;

  /// Workflow blocked status
  ///
  /// In en, this message translates to:
  /// **'Blocked'**
  String get workflowPreviewStatusBlocked;

  /// Workflow waiting-for-approval status
  ///
  /// In en, this message translates to:
  /// **'Waiting for approval'**
  String get workflowPreviewStatusWaiting;

  /// Workflow done status
  ///
  /// In en, this message translates to:
  /// **'Done'**
  String get workflowPreviewStatusDone;

  /// Workflow owner fact
  ///
  /// In en, this message translates to:
  /// **'Owner: {owner}'**
  String workflowPreviewOwner(String owner);

  /// Workflow due/next timing fact
  ///
  /// In en, this message translates to:
  /// **'Due: {due}'**
  String workflowPreviewDue(String due);

  /// Workflow step next action fact
  ///
  /// In en, this message translates to:
  /// **'Next action: {action}'**
  String workflowPreviewStepNextAction(String action);

  /// Workflow no-blockers label
  ///
  /// In en, this message translates to:
  /// **'No blockers'**
  String get workflowPreviewNoBlockers;

  /// Workflow blocker fact
  ///
  /// In en, this message translates to:
  /// **'Blocked: {blockers}'**
  String workflowPreviewBlockers(String blockers);

  /// Workflow evidence fact
  ///
  /// In en, this message translates to:
  /// **'Evidence: {evidence}'**
  String workflowPreviewEvidence(String evidence);

  /// Workflow approval-required note
  ///
  /// In en, this message translates to:
  /// **'Owner approval is required before this action can run.'**
  String get workflowPreviewApprovalRequired;

  /// Workflow governed-agent note
  ///
  /// In en, this message translates to:
  /// **'Agent help is dry-run only until admin policy and approval are connected.'**
  String get workflowPreviewAgentDryRunOnly;

  /// Disabled placeholder action for opening a workflow step
  ///
  /// In en, this message translates to:
  /// **'Open step'**
  String get workflowPreviewOpenStepButton;

  /// Disabled placeholder action for reviewing workflow evidence
  ///
  /// In en, this message translates to:
  /// **'Review evidence'**
  String get workflowPreviewReviewEvidenceButton;
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
