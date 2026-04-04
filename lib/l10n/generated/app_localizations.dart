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
  /// **'Your unified collaboration hub — messaging, files, and calendar in one place.'**
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
  /// **'Weave derives common service URLs from the issuer host. Review and edit them before finishing setup.'**
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

  /// Label for the Deck navigation destination
  ///
  /// In en, this message translates to:
  /// **'Deck'**
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

  /// Semantic label for the deck icon
  ///
  /// In en, this message translates to:
  /// **'Deck boards'**
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

  /// Message shown while Matrix OAuth sign-in is in progress
  ///
  /// In en, this message translates to:
  /// **'Connecting to Matrix…'**
  String get chatConnectingLabel;

  /// Button label to start or retry Matrix sign-in
  ///
  /// In en, this message translates to:
  /// **'Connect Matrix'**
  String get chatConnectButton;

  /// Title for the files screen app bar
  ///
  /// In en, this message translates to:
  /// **'Files'**
  String get filesScreenTitle;

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

  /// Title for the deck screen app bar
  ///
  /// In en, this message translates to:
  /// **'Deck'**
  String get deckScreenTitle;

  /// Title for the settings screen app bar
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settingsScreenTitle;

  /// Subtle branded copy shown in the settings header card
  ///
  /// In en, this message translates to:
  /// **'Weave brings messaging, files, and calendar into one workspace while this screen manages the server connection behind it.'**
  String get settingsBrandSectionDescription;

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

  /// Accessibility label describing how many unread messages a conversation has
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0 {No unread messages} =1 {1 unread message} other {{count} unread messages}}'**
  String chatConversationUnreadCount(int count);

  /// Empty state message for the files screen
  ///
  /// In en, this message translates to:
  /// **'No files yet'**
  String get filesEmptyMessage;

  /// Empty state message for the calendar screen
  ///
  /// In en, this message translates to:
  /// **'No events yet'**
  String get calendarEmptyMessage;

  /// Empty state message for the deck screen
  ///
  /// In en, this message translates to:
  /// **'No boards yet'**
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
  /// **'Defaults are derived from the issuer host. Edit them if your services live elsewhere.'**
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
