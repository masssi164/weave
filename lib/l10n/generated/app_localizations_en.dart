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
      'Your unified collaboration hub — messaging, files, and calendar in one place.';

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
      'Weave derives common service URLs from the issuer host. Review and edit them before finishing setup.';

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
  String get navDeck => 'Deck';

  @override
  String get navSettings => 'Settings';

  @override
  String get loadingLabel => 'Loading…';

  @override
  String get bootstrapLoadingLabel => 'Preparing Weave…';

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
  String get semanticDeckIcon => 'Deck boards';

  @override
  String get semanticSettingsIcon => 'Application settings';

  @override
  String get chatScreenTitle => 'Chat';

  @override
  String get chatLoadingLabel => 'Loading conversations…';

  @override
  String get chatConnectingLabel => 'Connecting to Matrix…';

  @override
  String get chatConnectButton => 'Connect Matrix';

  @override
  String get filesScreenTitle => 'Files';

  @override
  String get calendarScreenTitle => 'Calendar';

  @override
  String get deckScreenTitle => 'Deck';

  @override
  String get settingsScreenTitle => 'Settings';

  @override
  String get settingsServerConfigurationTitle => 'Server Configuration';

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
  String get filesEmptyMessage => 'No files yet';

  @override
  String get calendarEmptyMessage => 'No events yet';

  @override
  String get deckEmptyMessage => 'No boards yet';

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
      'Defaults are derived from the issuer host. Edit them if your services live elsewhere.';

  @override
  String get serverConfigurationMatrixLabel => 'Matrix Homeserver URL';

  @override
  String get serverConfigurationNextcloudLabel => 'Nextcloud Base URL';

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
}
