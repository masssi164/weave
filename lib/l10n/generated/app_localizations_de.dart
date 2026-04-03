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
  String get semanticWeaveLogo => 'Weave-Logo';

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
  String get settingsBrandSectionDescription =>
      'Weave bringt Nachrichten, Dateien und Kalender in einem Workspace zusammen, während auf diesem Bildschirm die Serververbindung dahinter verwaltet wird.';

  @override
  String get chatSecuritySectionTitle => 'Matrix-Sicherheit';

  @override
  String get chatSecuritySectionDescription =>
      'Weave behandelt Matrix-Verschlüsselung nur dann als gesund, wenn Secret Storage, Cross-Signing, Wiederherstellung und Gerätevertrauen vollständig eingerichtet sind.';

  @override
  String get chatSecurityRecoveryKeyTitle =>
      'Diesen Matrix-Wiederherstellungsschlüssel jetzt sichern';

  @override
  String get chatSecurityRecoveryKeyDescription =>
      'Weave verlässt sich für diesen Schlüssel nicht auf app-internen Speicher, weil sicherer Speicher nach Neuinstallation, Gerätewechsel oder manchen Wiederherstellungen verschwinden kann. Bewahren Sie ihn in Ihrem Passwortmanager oder an einem anderen sicheren Ort auf.';

  @override
  String get chatSecurityBannerTitle =>
      'Matrix-Sicherheit braucht Aufmerksamkeit';

  @override
  String get chatSecurityBannerSetupMessage =>
      'Verschlüsselte Matrix-Räume sind verfügbar, aber dieses Konto benötigt noch die anfängliche Sicherheitseinrichtung.';

  @override
  String get chatSecurityBannerRecoveryMessage =>
      'Dieses Gerät benötigt Ihren Matrix-Wiederherstellungsschlüssel, bevor ältere verschlüsselte Nachrichten wieder als vertrauenswürdig gelten können.';

  @override
  String get chatSecurityBannerVerificationMessage =>
      'Dieses Gerät oder Konto ist noch nicht vollständig verifiziert. Vergleichen Sie die Sicherheits-Emojis mit einem anderen angemeldeten Matrix-Gerät.';

  @override
  String get chatSecurityBannerMissingBackupMessage =>
      'Das Matrix-Schlüssel-Backup fehlt noch. Richten Sie es ein, bevor Sie sich auf die Wiederherstellung verschlüsselter Chats verlassen.';

  @override
  String get chatSecurityOpenSettingsButton =>
      'Sicherheitseinstellungen öffnen';

  @override
  String get chatSecuritySetupCardTitle => 'Einrichtung';

  @override
  String get chatSecurityCurrentDeviceCardTitle => 'Aktuelles Gerät';

  @override
  String get chatSecurityRecoveryCardTitle =>
      'Wiederherstellung und Schlüssel-Backup';

  @override
  String get chatSecurityRecoveryCardBody =>
      'Der Wiederherstellungsschlüssel wird benötigt, wenn dieses Gerät ersetzt, neu installiert wird oder lokale Kryptogeheimnisse verliert.';

  @override
  String get chatSecurityEncryptedRoomsCardTitle => 'Verschlüsselte Räume';

  @override
  String get chatSecurityEncryptedRoomsCardBodyExisting =>
      'Auf diesem Konto gibt es bereits verschlüsselte Räume. Warnungen bleiben sichtbar, bis Vertrauen und Wiederherstellung gesund sind.';

  @override
  String get chatSecurityEncryptedRoomsCardBodyNone =>
      'Es sind noch keine verschlüsselten Räume bekannt, aber der Sicherheitsstatus des Kontos wird hier trotzdem verfolgt.';

  @override
  String get chatSecurityStatusSignedOut => 'Matrix nicht verbunden';

  @override
  String get chatSecurityStatusSetupRequired => 'Einrichtung erforderlich';

  @override
  String get chatSecurityStatusSetupIncomplete => 'Einrichtung unvollständig';

  @override
  String get chatSecurityStatusRecoveryRequired =>
      'Wiederherstellung erforderlich';

  @override
  String get chatSecurityStatusHealthy => 'Gesund';

  @override
  String get chatSecurityStatusUnavailable => 'Nicht verfügbar';

  @override
  String get chatSecurityStatusVerified => 'Verifiziert';

  @override
  String get chatSecurityStatusUnverified => 'Nicht verifiziert';

  @override
  String get chatSecurityStatusBlocked => 'Blockiert';

  @override
  String get chatSecurityStatusMissing => 'Fehlt';

  @override
  String get chatSecurityStatusNeedsReconnect => 'Neu verbinden';

  @override
  String get chatSecurityStatusReady => 'Bereit';

  @override
  String get chatSecurityEncryptedRoomsStatusNone =>
      'Noch keine verschlüsselten Räume';

  @override
  String get chatSecurityEncryptedRoomsStatusAttention =>
      'Verschlüsselte Räume brauchen Aufmerksamkeit';

  @override
  String get chatSecuritySetupDescriptionSignedOut =>
      'Öffne Chat und verbinde Matrix, bevor du die Verschlüsselung verwaltest.';

  @override
  String get chatSecuritySetupDescriptionNotInitialized =>
      'Richte Secret Storage, Cross-Signing und Online-Schlüssel-Backup ein, bevor du verschlüsselten Räumen vertraust.';

  @override
  String get chatSecuritySetupDescriptionPartiallyInitialized =>
      'Einige Verschlüsselungsteile sind vorhanden, aber Wiederherstellung oder Cross-Signing sind noch unvollständig.';

  @override
  String get chatSecuritySetupDescriptionRecoveryRequired =>
      'Dieses Konto wurde schon eingerichtet, aber dieses Gerät benötigt den Wiederherstellungsschlüssel oder die Passphrase, um sich sicher wieder zu verbinden.';

  @override
  String get chatSecuritySetupDescriptionReady =>
      'Dieses Gerät kann die aktuelle Matrix-Kryptoidentität und Wiederherstellung verwenden.';

  @override
  String get chatSecuritySetupDescriptionUnavailable =>
      'Matrix-Verschlüsselung ist auf dieser Plattform nicht verfügbar.';

  @override
  String get chatSecurityCurrentDeviceDescriptionVerified =>
      'Ein anderes vertrauenswürdiges Matrix-Gerät hat diese Sitzung verifiziert.';

  @override
  String get chatSecurityCurrentDeviceDescriptionUnverified =>
      'Vergleiche Sicherheits-Emojis oder Zahlen mit einem anderen angemeldeten Matrix-Gerät.';

  @override
  String get chatSecurityCurrentDeviceDescriptionBlocked =>
      'Dieses Gerät ist blockiert oder seine Vertrauenskette ist beschädigt.';

  @override
  String get chatSecurityCurrentDeviceDescriptionUnavailable =>
      'Der aktuelle Geräteschlüssel ist noch nicht verfügbar.';

  @override
  String get chatSecurityActionsUnavailableSignedOut =>
      'Matrix-Sicherheitsaktionen werden verfügbar, sobald die Matrix-Sitzung verbunden ist.';

  @override
  String get chatSecurityWorkingButton => 'Wird ausgeführt…';

  @override
  String get chatSecuritySetupButton => 'Verschlüsselten Chat einrichten';

  @override
  String get chatSecurityReconnectButton =>
      'Mit Wiederherstellungsschlüssel neu verbinden';

  @override
  String get chatSecurityVerifyDeviceButton => 'Dieses Gerät verifizieren';

  @override
  String get chatSecurityAcceptVerificationButton =>
      'Verifizierung akzeptieren';

  @override
  String get chatSecurityDeclineVerificationButton => 'Ablehnen';

  @override
  String get chatSecurityCompareEmojiButton => 'Sicherheits-Emojis vergleichen';

  @override
  String get chatSecurityUnlockVerificationButton =>
      'Verifizierung mit Wiederherstellungsschlüssel fortsetzen';

  @override
  String get chatSecurityEmojiMatchButton => 'Emojis stimmen überein';

  @override
  String get chatSecurityEmojiMismatchButton => 'Sie stimmen nicht überein';

  @override
  String get chatSecurityDismissButton => 'Schließen';

  @override
  String get chatSecurityNoActionNeeded =>
      'Zurzeit ist keine Aktion erforderlich.';

  @override
  String get chatSecurityGenericFailure =>
      'Die Matrix-Sicherheit kann im Moment nicht aktualisiert werden.';

  @override
  String get chatSecurityNoticeSetupComplete =>
      'Verschlüsselter Chat ist jetzt eingerichtet. Speichern Sie Ihren Wiederherstellungsschlüssel, bevor Sie diesen Bildschirm schließen.';

  @override
  String get chatSecurityNoticeRecoveryRestored =>
      'Verschlüsselter Chat wurde für dieses Gerät wieder verbunden.';

  @override
  String get chatSecurityNoticeVerificationRequestSent =>
      'Verifizierungsanfrage gesendet. Fahren Sie auf Ihrem anderen Matrix-Gerät fort.';

  @override
  String get chatSecurityNoticeVerificationCancelled =>
      'Verifizierung abgebrochen.';

  @override
  String get chatSecurityVerificationIncomingMessage =>
      'Ein anderes Gerät möchte diese Sitzung verifizieren.';

  @override
  String get chatSecurityVerificationChooseMethodMessage =>
      'Wählen Sie eine Verifizierungsmethode, um beide Geräte zu vergleichen.';

  @override
  String get chatSecurityVerificationWaitingMessage =>
      'Es wird gewartet, bis das andere Gerät mit der Verifizierung fortfährt.';

  @override
  String get chatSecurityVerificationRecoveryMessage =>
      'Diese Verifizierung benötigt Ihren Matrix-Wiederherstellungsschlüssel oder Ihre Passphrase, bevor sie fortgesetzt werden kann.';

  @override
  String get chatSecurityVerificationRecoveryHelp =>
      'Entsperren Sie den vorhandenen Matrix Secret Storage, damit dieses Gerät die Verifizierung sicher abschließen kann.';

  @override
  String get chatSecurityVerificationCompareMessage =>
      'Vergleichen Sie die Sicherheits-Emojis oder Zahlen auf beiden Geräten.';

  @override
  String get chatSecurityVerificationDoneMessage =>
      'Dieses Gerät ist jetzt verifiziert.';

  @override
  String get chatSecurityVerificationCancelledMessage =>
      'Die Verifizierung wurde abgebrochen, bevor sie abgeschlossen war.';

  @override
  String get chatSecurityVerificationFailedMessage =>
      'Die Verifizierung konnte nicht abgeschlossen werden.';

  @override
  String get chatSecuritySetupDialogTitle => 'Verschlüsselten Chat einrichten';

  @override
  String get chatSecuritySetupDialogDescription =>
      'Sie können den Matrix-Wiederherstellungsschlüssel optional mit einer merkbaren Passphrase schützen. Lassen Sie das Feld leer, um stattdessen einen generierten Wiederherstellungsschlüssel zu verwenden.';

  @override
  String get chatSecurityOptionalPassphraseLabel => 'Optionale Passphrase';

  @override
  String get chatSecurityDialogCancelButton => 'Abbrechen';

  @override
  String get chatSecurityDialogContinueButton => 'Weiter';

  @override
  String get chatSecurityRestoreDialogTitle =>
      'Verschlüsselten Chat wieder verbinden';

  @override
  String get chatSecurityRestoreDialogDescription =>
      'Geben Sie den Matrix-Wiederherstellungsschlüssel oder die Wiederherstellungs-Passphrase ein, die bei der ersten Einrichtung des verschlüsselten Chats erstellt wurde.';

  @override
  String get chatSecurityVerificationRecoveryDialogTitle =>
      'Verifizierung fortsetzen';

  @override
  String get chatSecurityVerificationRecoveryDialogDescription =>
      'Geben Sie Ihren Matrix-Wiederherstellungsschlüssel oder Ihre Passphrase ein, um diese Verifizierung fortzusetzen. Dadurch werden nur die für die Verifizierung benötigten Geheimnisse entsperrt und nicht das gesamte Konto neu verbunden.';

  @override
  String get chatSecurityRecoveryKeyFieldLabel =>
      'Wiederherstellungsschlüssel oder Passphrase';

  @override
  String get chatSecurityRecoveryKeyDismissButton => 'Ich habe ihn gespeichert';

  @override
  String get chatSecurityEmojiSummaryLabel => 'Sicherheits-Emojis';

  @override
  String chatSecurityNumbersSummaryLabel(String value) {
    return 'Sicherheitszahlen $value';
  }

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
