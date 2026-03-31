// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for German (`de`).
class AppLocalizationsDe extends AppLocalizations {
  AppLocalizationsDe([String locale = 'de']) : super(locale);

  @override
  String get appTitle => 'Weave';

  @override
  String get welcomeTitle => 'Willkommen bei Weave';

  @override
  String get welcomeSubtitle =>
      'Dein einheitlicher Kollaborations-Hub — Nachrichten, Dateien und Kalender an einem Ort.';

  @override
  String get continueButton => 'Los geht\'s';

  @override
  String get setupTitle => 'Einrichtung';

  @override
  String get setupProviderStepTitle => 'Server verbinden';

  @override
  String get setupProviderStepDescription =>
      'Wähle deinen OIDC-Anbieter und gib die Issuer-URL deiner selbst gehosteten Umgebung ein.';

  @override
  String get setupServicesStepTitle => 'Dienstendpunkte prüfen';

  @override
  String get setupServicesStepDescription =>
      'Weave leitet gängige Dienst-URLs aus dem Issuer-Host ab. Prüfe und ändere sie bei Bedarf vor dem Abschluss.';

  @override
  String get setupLanguageStepTitle => 'Deine Sprache';

  @override
  String get setupLanguageStepDescription =>
      'Weave verwendet deine Gerätesprache. Du kannst sie später in den Einstellungen ändern.';

  @override
  String get setupConfirmStepTitle => 'Alles bereit';

  @override
  String get setupConfirmStepDescription =>
      'Tippe auf Fertig, um Weave zu verwenden.';

  @override
  String get setupNextButton => 'Weiter';

  @override
  String get setupFinishButton => 'Fertig';

  @override
  String get setupBackButton => 'Zurück';

  @override
  String setupStepIndicator(int current, int total) {
    return 'Schritt $current von $total';
  }

  @override
  String get navChat => 'Chat';

  @override
  String get navFiles => 'Dateien';

  @override
  String get navCalendar => 'Kalender';

  @override
  String get navDeck => 'Deck';

  @override
  String get navSettings => 'Einstellungen';

  @override
  String get loadingLabel => 'Wird geladen…';

  @override
  String get bootstrapLoadingLabel => 'Weave wird vorbereitet…';

  @override
  String get emptyStateLabel => 'Noch nichts hier';

  @override
  String get errorStateLabel => 'Etwas ist schiefgelaufen';

  @override
  String get retryButton => 'Erneut versuchen';

  @override
  String get semanticBackButton => 'Zurück';

  @override
  String get semanticCloseButton => 'Schließen';

  @override
  String get semanticChatIcon => 'Chat-Nachrichten';

  @override
  String get semanticFilesIcon => 'Dateibrowser';

  @override
  String get semanticCalendarIcon => 'Kalendertermine';

  @override
  String get semanticDeckIcon => 'Deck-Boards';

  @override
  String get semanticSettingsIcon => 'App-Einstellungen';

  @override
  String get chatScreenTitle => 'Chat';

  @override
  String get chatLoadingLabel => 'Unterhaltungen werden geladen…';

  @override
  String get chatConnectingLabel => 'Verbinde mit Matrix…';

  @override
  String get chatConnectButton => 'Matrix verbinden';

  @override
  String get filesScreenTitle => 'Dateien';

  @override
  String get calendarScreenTitle => 'Kalender';

  @override
  String get deckScreenTitle => 'Deck';

  @override
  String get settingsScreenTitle => 'Einstellungen';

  @override
  String get settingsServerConfigurationTitle => 'Serverkonfiguration';

  @override
  String get settingsServerConfigurationDescription =>
      'Aktualisiere den Anbieter und die Dienst-URLs, die Weave für deine selbst gehostete Umgebung verwenden soll.';

  @override
  String get settingsSaveButton => 'Änderungen speichern';

  @override
  String get settingsSaveInProgress => 'Wird gespeichert…';

  @override
  String get settingsSignOutTitle => 'Sitzung';

  @override
  String get settingsSignOutDescription =>
      'Melde die aktuelle Serversitzung ab und kehre zur Anmeldeseite zurück.';

  @override
  String get settingsSignOutButton => 'Abmelden';

  @override
  String get settingsSignOutInProgress => 'Melde ab…';

  @override
  String get chatEmptyMessage => 'Noch keine Unterhaltungen';

  @override
  String get chatConversationNoPreview => 'Keine aktuellen Nachrichten';

  @override
  String get chatConversationEncryptedPreview => 'Verschlüsselte Nachricht';

  @override
  String get chatConversationUnsupportedPreview =>
      'Nicht unterstützte Nachricht';

  @override
  String get chatConversationInviteLabel => 'Einladung';

  @override
  String get chatConversationDirectMessageLabel => 'Direkte Unterhaltung';

  @override
  String chatConversationUnreadCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ungelesene Nachrichten',
      one: '1 ungelesene Nachricht',
      zero: 'Keine ungelesenen Nachrichten',
    );
    return '$_temp0';
  }

  @override
  String get filesEmptyMessage => 'Noch keine Dateien';

  @override
  String get calendarEmptyMessage => 'Noch keine Termine';

  @override
  String get deckEmptyMessage => 'Noch keine Boards';

  @override
  String get deviceLanguageLabel => 'Gerätesprache';

  @override
  String get serverConfigurationProviderLabel => 'OIDC-Anbieter';

  @override
  String get serverConfigurationProviderFieldLabel => 'Anbietertyp';

  @override
  String get oidcProviderAuthentik => 'Authentik';

  @override
  String get oidcProviderKeycloak => 'Keycloak';

  @override
  String get serverConfigurationIssuerLabel => 'OIDC-Issuer-URL';

  @override
  String get serverConfigurationIssuerHelper =>
      'Dies muss die absolute Issuer-URL deines OIDC-Anbieters sein.';

  @override
  String get serverConfigurationClientIdLabel => 'OIDC-Client-ID';

  @override
  String get serverConfigurationClientIdHelper =>
      'Gib die public/native Client-ID ein, die für Weave bei diesem Issuer registriert ist.';

  @override
  String get serverConfigurationServicesLabel => 'Dienstendpunkte';

  @override
  String get serverConfigurationServicesHelper =>
      'Standardwerte werden aus dem Issuer-Host abgeleitet. Ändere sie, wenn deine Dienste anderswo liegen.';

  @override
  String get serverConfigurationMatrixLabel => 'Matrix-Homeserver-URL';

  @override
  String get serverConfigurationNextcloudLabel => 'Nextcloud-Basis-URL';

  @override
  String serverConfigurationDerivedHint(String value) {
    return 'Abgeleiteter Standard: $value';
  }

  @override
  String get oidcRegistrationHelpTitle =>
      'Weave als native/public Client registrieren';

  @override
  String get oidcRegistrationHelpDescription =>
      'Verwende Authorization Code + PKCE mit dem Systembrowser und hinterlege die folgenden Weave-Redirect-URIs in der Provider-Client-Registrierung.';

  @override
  String get oidcRegistrationHelpNoSecret =>
      'Lege hier kein Client-Secret an und füge keines ein. Weave verwendet einen öffentlichen Native-Client-Flow.';

  @override
  String get oidcRegistrationHelpAuthentikSteps =>
      'Erstelle in Authentik einen OAuth2/OpenID-Connect-Provider für Weave, trage diese Redirect-URIs in den Provider ein und stelle sicher, dass Authorization Code sowie bei Bedarf `offline_access` für Refresh-Tokens verfügbar sind.';

  @override
  String get oidcRegistrationHelpKeycloakSteps =>
      'Erstelle in Keycloak einen öffentlichen OpenID-Connect-Client für Weave, trage diese Redirect-URIs und Post-Logout-Redirect-URIs ein und aktiviere Standard Flow mit PKCE (S256), damit Weave ohne Client-Secret anmelden kann.';

  @override
  String get oidcRegistrationHelpRedirectsTitle =>
      'Diese Redirect-URIs registrieren';

  @override
  String oidcRegistrationHelpRedirectValue(String value) {
    return 'Anmelde-Redirect: $value';
  }

  @override
  String oidcRegistrationHelpPostLogoutRedirectValue(String value) {
    return 'Post-Logout-Redirect: $value';
  }

  @override
  String get signInScreenTitle => 'Anmelden';

  @override
  String get signInTitle => 'Zum Fortfahren anmelden';

  @override
  String get signInDescription =>
      'Weave ist konfiguriert. Verwende dein Provider-Konto im Systembrowser, um die authentifizierte App zu öffnen.';

  @override
  String get signInConfigurationTitle => 'Aktuelle Anmeldekonfiguration';

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
    return 'Client-ID: $value';
  }

  @override
  String get signInButton => 'Anmelden';

  @override
  String get signInInProgress => 'Melde an…';

  @override
  String get signInBackToSetupButton => 'Zurück zur Einrichtung';

  @override
  String get signInMissingConfigurationTitle =>
      'Einrichtung abschließen, um dich anzumelden';

  @override
  String get signInMissingConfigurationDescription =>
      'Weave benötigt noch eine gültige Issuer-URL und Client-ID, bevor der Browser-Anmeldefluss gestartet werden kann.';
}
