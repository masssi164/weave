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

  /// Button label that opens settings from the Matrix security banner
  ///
  /// In en, this message translates to:
  /// **'Open security settings'**
  String get chatSecurityOpenSettingsButton;

  String get chatSecurityBannerSetupMessage;
  String get chatSecurityBannerRecoveryMessage;
  String get chatSecurityBannerVerificationMessage;
  String get chatSecurityBannerMissingBackupMessage;
  String get chatSecuritySetupCardTitle;
  String get chatSecurityCurrentDeviceCardTitle;
  String get chatSecurityRecoveryCardTitle;
  String get chatSecurityRecoveryCardBody;
  String get chatSecurityEncryptedRoomsCardTitle;
  String get chatSecurityEncryptedRoomsCardBodyExisting;
  String get chatSecurityEncryptedRoomsCardBodyNone;
  String get chatSecurityStatusSignedOut;
  String get chatSecurityStatusSetupRequired;
  String get chatSecurityStatusSetupIncomplete;
  String get chatSecurityStatusRecoveryRequired;
  String get chatSecurityStatusHealthy;
  String get chatSecurityStatusUnavailable;
  String get chatSecurityStatusVerified;
  String get chatSecurityStatusUnverified;
  String get chatSecurityStatusBlocked;
  String get chatSecurityStatusMissing;
  String get chatSecurityStatusNeedsReconnect;
  String get chatSecurityStatusReady;
  String get chatSecurityEncryptedRoomsStatusNone;
  String get chatSecurityEncryptedRoomsStatusAttention;
  String get chatSecuritySetupDescriptionSignedOut;
  String get chatSecuritySetupDescriptionNotInitialized;
  String get chatSecuritySetupDescriptionPartiallyInitialized;
  String get chatSecuritySetupDescriptionRecoveryRequired;
  String get chatSecuritySetupDescriptionReady;
  String get chatSecuritySetupDescriptionUnavailable;
  String get chatSecurityCurrentDeviceDescriptionVerified;
  String get chatSecurityCurrentDeviceDescriptionUnverified;
  String get chatSecurityCurrentDeviceDescriptionBlocked;
  String get chatSecurityCurrentDeviceDescriptionUnavailable;
  String get chatSecurityActionsUnavailableSignedOut;
  String get chatSecurityWorkingButton;
  String get chatSecuritySetupButton;
  String get chatSecurityReconnectButton;
  String get chatSecurityVerifyDeviceButton;
  String get chatSecurityAcceptVerificationButton;
  String get chatSecurityDeclineVerificationButton;
  String get chatSecurityCompareEmojiButton;
  String get chatSecurityEmojiMatchButton;
  String get chatSecurityEmojiMismatchButton;
  String get chatSecurityDismissButton;
  String get chatSecurityNoActionNeeded;
  String get chatSecurityGenericFailure;
  String get chatSecurityNoticeSetupComplete;
  String get chatSecurityNoticeRecoveryRestored;
  String get chatSecurityNoticeVerificationRequestSent;
  String get chatSecurityNoticeVerificationCancelled;
  String get chatSecurityVerificationIncomingMessage;
  String get chatSecurityVerificationChooseMethodMessage;
  String get chatSecurityVerificationWaitingMessage;
  String get chatSecurityVerificationCompareMessage;
  String get chatSecurityVerificationDoneMessage;
  String get chatSecurityVerificationCancelledMessage;
  String get chatSecurityVerificationFailedMessage;
  String get chatSecuritySetupDialogTitle;
  String get chatSecuritySetupDialogDescription;
  String get chatSecurityOptionalPassphraseLabel;
  String get chatSecurityDialogCancelButton;
  String get chatSecurityDialogContinueButton;
  String get chatSecurityRestoreDialogTitle;
  String get chatSecurityRestoreDialogDescription;
  String get chatSecurityRecoveryKeyFieldLabel;
  String get chatSecurityRecoveryKeyDismissButton;
  String get chatSecurityEmojiSummaryLabel;
  String chatSecurityNumbersSummaryLabel(String value);

  /// Section title for server configuration in settings
  ///
  /// In en, this message translates to:
  /// **'Server Configuration'**
  String get settingsServerConfigurationTitle;

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
