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
  String get setupProviderStepTitle => 'Provider-Kategorien konfigurieren';

  @override
  String get setupProviderStepDescription =>
      'Die Admin-Einrichtung beginnt mit der Kategorie Identität/IDM und hält Chat, Dateien, Kalender, Boards/Aufgaben, Besprechungen/Anrufe, Dokumente/Zusammenarbeit und Weaver als Provider-Kategorien sichtbar, bevor Mitglieder beitreten.';

  @override
  String get setupServicesStepTitle => 'Dienstendpunkte prüfen';

  @override
  String get setupServicesStepDescription =>
      'Prüfe die aktuellen Dogfood-Service-Endpunkte, die aus dem Identitäts-Issuer abgeleitet wurden. Diese Provider-URLs bleiben Admin-/Operator-Konfiguration, nicht normale Mitglieder-Einrichtung.';

  @override
  String get providerCategorySummaryTitle => 'Provider-Kategorien';

  @override
  String get providerCategorySummaryDescription =>
      'Weave betrachtet zuerst Zusammenarbeits-Kategorien. Die Provider-Namen unten sind aktuelle Dogfood-Auswahlen für Admins/Operatoren, keine mitgliederseitigen Produktnamen.';

  @override
  String get providerCategorySummarySemanticLabel =>
      'Provider-Kategorien. Aktuelle Dogfood-Provider-Auswahlen werden nur für Einrichtung und Workspace Health gezeigt.';

  @override
  String get providerCategoryStatusCurrentDefault => 'Aktuelle Dogfood-Auswahl';

  @override
  String get providerCategoryStatusAdminSetupRequired =>
      'Admin-Einrichtung erforderlich';

  @override
  String get providerCategoryStatusDisabledByDefault =>
      'Standardmäßig deaktiviert';

  @override
  String get providerCategoryIdentityTitle => 'Identität/IDM';

  @override
  String get providerCategoryIdentityDetail =>
      'Keycloak/Auth ist die aktuelle Dogfood-Auswahl; Entra ID, Authentik oder eine andere OIDC/SAML-Quelle kann auf diese Kategorie abgebildet werden.';

  @override
  String get providerCategoryChatTitle => 'Chat';

  @override
  String get providerCategoryChatDetail =>
      'Matrix/Chat ist die aktuelle Dogfood-Auswahl hinter der Weave-Chat-Oberfläche.';

  @override
  String get providerCategoryFilesTitle => 'Dateien';

  @override
  String get providerCategoryFilesDetail =>
      'Nextcloud/Files ist die aktuelle Dogfood-Speicherauswahl hinter der Weave-Datei-Fassade.';

  @override
  String get providerCategoryCalendarTitle => 'Kalender';

  @override
  String get providerCategoryCalendarDetail =>
      'Nextcloud/Calendar-Anbindung ist die aktuelle Dogfood-Auswahl hinter der Weave-Kalender-Fassade.';

  @override
  String get providerCategoryBoardsTitle => 'Boards/Aufgaben';

  @override
  String get providerCategoryBoardsDetail =>
      'OpenProject-Boards-Validierung ist der aktuelle providergestützte Pfad; Aufgabenänderungen durch Mitglieder bleiben durch Autorisierung, Audit und Rollback-Evidenz begrenzt.';

  @override
  String get providerCategoryMeetingsTitle => 'Besprechungen/Anrufe';

  @override
  String get providerCategoryMeetingsDetail =>
      'LiveKit-Meetings-Bereitschaft wird hinter der Token-Fassade verfolgt, bevor Mitglieder Anrufe starten oder beitreten können.';

  @override
  String get providerCategoryDocumentsTitle => 'Dokumente/Zusammenarbeit';

  @override
  String get providerCategoryDocumentsDetail =>
      'Dokument-Zusammenarbeit ist eine Provider-Adapter-Kategorie und bleibt Admin-Einrichtung erforderlich, bis ein backend-eigener Startpfad konfiguriert ist.';

  @override
  String get providerCategoryWeaverTitle => 'Weaver';

  @override
  String get providerCategoryWeaverDetail =>
      'Weaver bleibt standardmäßig deaktiviert, bis regulierte persönliche PA-Richtlinie, Whitelisting, Einwilligung und Audit akzeptiert sind.';

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
  String get navDeck => 'Boards-Workspace';

  @override
  String get navSettings => 'Einstellungen';

  @override
  String get loadingLabel => 'Wird geladen…';

  @override
  String get bootstrapLoadingLabel => 'Weave wird vorbereitet…';

  @override
  String get bootstrapLoadingHint =>
      'Arbeitsbereichsdienste werden geprüft und die Shell vorbereitet.';

  @override
  String get shellErrorTitle => 'Weave konnte nicht vorbereitet werden';

  @override
  String get shellErrorGuidance =>
      'Versuche es erneut. Wenn das weiterhin passiert, prüfe, ob deine Arbeitsbereichsdienste erreichbar sind.';

  @override
  String get shellRecentActivityTitle => 'Letzte Aktivität';

  @override
  String get shellRecentActivityDescription =>
      'Schnellzugriff auf aktuelle Räume und Dateiänderungen.';

  @override
  String get shellRecentActivitySemanticLabel =>
      'Schnellzugriffe für letzte Aktivität';

  @override
  String get shellRecentRoomsTitle => 'Räume';

  @override
  String get shellRecentFilesTitle => 'Dateien';

  @override
  String get shellRecentRoomsLoading => 'Aktuelle Räume werden geladen…';

  @override
  String get shellRecentRoomsEmpty => 'Noch keine aktuellen Räume.';

  @override
  String get shellRecentRoomsUnavailable =>
      'Aktuelle Räume sind verfügbar, sobald Chat verbunden ist.';

  @override
  String get shellRecentFilesLoading =>
      'Aktuelle Dateiänderungen werden geladen…';

  @override
  String get shellRecentFilesEmpty => 'Noch keine aktuellen Dateiänderungen.';

  @override
  String get shellRecentFilesError =>
      'Aktuelle Dateiänderungen konnten nicht geladen werden.';

  @override
  String get shellRecentFilesUnavailable =>
      'Aktuelle Dateien sind verfügbar, sobald Dateien verbunden sind.';

  @override
  String get shellRecentActivityUnknownRecency => 'aktuell';

  @override
  String get shellRecentActivityNow => 'jetzt';

  @override
  String shellRecentActivityMinutesAgo(int minutes) {
    return 'vor $minutes Min.';
  }

  @override
  String get shellRecentActivityToday => 'heute';

  @override
  String get shellRecentActivityYesterday => 'gestern';

  @override
  String shellRecentRoomItemSemantic(
    String roomName,
    String preview,
    String recency,
  ) {
    return 'Raum $roomName öffnen. Letzte Aktivität: $preview. $recency.';
  }

  @override
  String shellRecentFileItemSemantic(
    String itemType,
    String itemName,
    String path,
    String recency,
  ) {
    return '$itemType $itemName in $path öffnen. Geändert $recency.';
  }

  @override
  String get shellRecentFileFolderType => 'Ordner';

  @override
  String get shellRecentFileFileType => 'Datei';

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
  String get semanticDeckIcon => 'Boards-Workspace';

  @override
  String get semanticSettingsIcon => 'App-Einstellungen';

  @override
  String get semanticWeaveLogo => 'Weave-Logo';

  @override
  String get firstRunAppBarTitle => 'Erststart-Status';

  @override
  String get firstRunLoadingLabel => 'Weave-Arbeitsbereich wird geprüft…';

  @override
  String get firstRunLoadingHint =>
      'Profil, Rolle und Modulbereitschaft werden vom Weave-Backend geladen.';

  @override
  String get firstRunLoadFailure =>
      'Der Erststart-Status konnte nicht vom Weave-Backend geladen werden.';

  @override
  String get firstRunSignedOutMessage =>
      'Melde dich an, um deinen Weave-Erststart-Status zu sehen.';

  @override
  String get firstRunReadyTitle => 'Dein Weave-Arbeitsbereich ist bereit';

  @override
  String get firstRunNeedsAttentionTitle =>
      'Dein Weave-Arbeitsbereich wird vorbereitet';

  @override
  String get firstRunDescription =>
      'Du hast dich einmal per Weave-SSO angemeldet. Weave prüft Profil und Zusammenarbeitsmodule; separate Matrix- oder Nextcloud-Zugangsdaten sind nicht nötig.';

  @override
  String get firstRunIdentitySectionTitle => 'Deine Weave-Identität';

  @override
  String get firstRunIdentitySectionDescription =>
      'Dieses Profil und diese Rolle kommen nach SSO aus dem Weave-Backend-Vertrag.';

  @override
  String get firstRunDisplayNameLabel => 'Name';

  @override
  String get firstRunUsernameLabel => 'Benutzername';

  @override
  String get firstRunEmailLabel => 'E-Mail';

  @override
  String get firstRunRoleLabel => 'Rolle';

  @override
  String get firstRunInviteStatusLabel => 'Einladung';

  @override
  String get firstRunModuleSectionTitle => 'Modulbereitschaft';

  @override
  String get firstRunProfileModuleTitle => 'Profil';

  @override
  String get firstRunChatModuleTitle => 'Chat';

  @override
  String get firstRunFilesModuleTitle => 'Dateien';

  @override
  String get firstRunCalendarModuleTitle => 'Kalender';

  @override
  String get firstRunStateReady => 'Bereit';

  @override
  String get firstRunStatePending => 'Ausstehend';

  @override
  String get firstRunStateUnavailable => 'Nicht verfügbar';

  @override
  String get firstRunStateDegraded => 'Eingeschränkt';

  @override
  String get firstRunStateActionNeeded => 'Aktion erforderlich';

  @override
  String get firstRunNextStepsTitle => 'Nächste Schritte';

  @override
  String get firstRunRefreshButton => 'Status aktualisieren';

  @override
  String get firstRunContinueButton => 'Weiter zum Chat';

  @override
  String get chatProvisioningReadyTitle => 'Chat ist bereit';

  @override
  String get chatProvisioningPendingTitle =>
      'Chaträume werden noch vorbereitet';

  @override
  String get chatProvisioningDegradedTitle =>
      'Chat ist eingeschränkt verfügbar';

  @override
  String get chatProvisioningActionNeededTitle =>
      'Chat-Einrichtung benötigt Admin-Hilfe';

  @override
  String get chatProvisioningRetryButton => 'Status erneut prüfen';

  @override
  String get chatProvisioningReadyGuidance =>
      'Chat ist für diesen Workspace bereit.';

  @override
  String get chatProvisioningPendingGuidance =>
      'Chat wird noch vorbereitet. Lass diese Ansicht offen oder versuche es gleich erneut.';

  @override
  String get chatProvisioningDegradedGuidance =>
      'Chat ist möglicherweise eingeschränkt verfügbar, aber die Einrichtung braucht einen Admin-Gesundheitscheck.';

  @override
  String get chatProvisioningUnavailableGuidance =>
      'Chat ist in diesem Workspace noch nicht verfügbar.';

  @override
  String get chatProvisioningFailedGuidance =>
      'Chat konnte für diesen Workspace nicht vorbereitet werden.';

  @override
  String get chatProvisioningAdminAction =>
      'Bitte einen Workspace-Admin, support-sichere Diagnosen zu prüfen.';

  @override
  String get chatErrorCancelledGuidance =>
      'Die Chat-Anmeldung wurde abgebrochen. Verbinde Chat, um es erneut zu versuchen.';

  @override
  String get chatErrorAdminGuidance =>
      'Die Chat-Einrichtung braucht Admin-Aufmerksamkeit. Bitte einen Workspace-Admin, support-sichere Diagnosen zu prüfen.';

  @override
  String get chatErrorSessionRequiredGuidance =>
      'Chat ist für diese Workspace-Sitzung nicht verbunden. Verbinde Chat, um fortzufahren.';

  @override
  String get chatErrorUnsupportedPlatformGuidance =>
      'Chat ist auf diesem Gerät noch nicht verfügbar.';

  @override
  String get chatErrorRetryGuidance =>
      'Chat konnte gerade nicht geladen werden. Versuche es erneut, sobald die Verbindung zurück ist.';

  @override
  String get chatScreenTitle => 'Chat';

  @override
  String get chatOverviewTitle => 'Weave Home';

  @override
  String get chatOverviewDescription =>
      'Deine persönlichen Nachrichten, Favoriten, Kanäle und KI-Chats sind hier gruppiert, damit der Workspace nach Absicht startet statt als flache Raumliste.';

  @override
  String get chatFavoritesSectionTitle => 'Favoriten';

  @override
  String get chatFavoritesSectionDescription =>
      'Angepinnte Personen, Kanäle und KI-Chats, die du zuerst erreichen möchtest.';

  @override
  String get chatFavoritesSectionEmpty =>
      'Noch keine Favoriten. Wichtige Direktnachrichten, Kanäle und KI-Chats, die als Favoriten markiert sind, bleiben hier.';

  @override
  String get chatPersonalMessagesSectionTitle => 'Persönliche Nachrichten';

  @override
  String get chatPersonalMessagesSectionDescription =>
      'Direkte Unterhaltungen mit Personen in deinem Workspace.';

  @override
  String get chatPersonalMessagesSectionEmpty =>
      'Noch keine persönlichen Nachrichten verfügbar.';

  @override
  String get chatChannelsSectionTitle => 'Kanäle';

  @override
  String get chatChannelsSectionDescription =>
      'Team- und Themenräume für gemeinsame Arbeit.';

  @override
  String get chatChannelsSectionEmpty => 'Noch keine Kanäle verfügbar.';

  @override
  String get chatAiChatsSectionTitle => 'KI-Chats';

  @override
  String get chatAiChatsSectionDescription =>
      'Spezialisierte Assistenten- und Agentenchats haben ihren eigenen Bereich.';

  @override
  String get chatAiChatsSectionEmpty =>
      'KI-Chats sind für diesen Arbeitsbereich nicht aktiviert. Owner oder Admins können gesteuerte Assistenten aktivieren, sobald Policy-, Einwilligungs- und Audit-Kontrollen bereit sind.';

  @override
  String get chatAgentGovernanceTitle =>
      'Agentenchats werden von deinem Workspace gesteuert';

  @override
  String get chatAgentGovernanceDescription =>
      'Agenten können in Weave erst helfen, wenn Owner oder Admins ein Paket aktivieren, Bereiche auswählen und Einwilligung sowie Audit sichtbar bleiben.';

  @override
  String get chatAgentContextPackTitle => 'Kontextpaket vor der Aktion';

  @override
  String get chatAgentContextPackDescription =>
      'Wenn ein Agent verfügbar ist, zeigt Weave vorab, welcher Kontext für diese Anfrage gesendet wird.';

  @override
  String get chatAgentContextPackScopedBullet =>
      'Kontext ist auf ausgewählte Chats, Dateien, Kalendertermine, Boards oder ausdrücklich gewählte Workspace-Quellen begrenzt.';

  @override
  String get chatAgentContextPackConsentBullet =>
      'Vor dem Start oder der Freigabe einer Agentenaktion siehst du Hinweise zu Berechtigungen.';

  @override
  String get chatAgentContextPackNoSurveillanceBullet =>
      'Agenten lesen Räume nicht dauerhaft im Hintergrund mit.';

  @override
  String get chatAgentGovernanceAuditNote =>
      'Agentenerstellung, Kontextzugriff, Tool-/Aktionsausführung, Freigabe und Widerruf müssen vor einer Laufzeitfreigabe auditierbar sein.';

  @override
  String get chatAgentAvailabilityPreview => 'Durch Policy deaktiviert';

  @override
  String get chatAgentAvailabilityAdminSetup => 'Admin-Einrichtung nötig';

  @override
  String get chatAgentAvailabilityBlocked => 'Durch Policy blockiert';

  @override
  String get chatAgentPersonalAssistantTitle => 'Persönlicher Assistent';

  @override
  String get chatAgentPersonalAssistantDescription =>
      'Ein privater Assistentenchat für Entwürfe, Zusammenfassungen und Erinnerungen kann erst aktiviert werden, wenn Workspace-Policy, Einwilligung und Audit-Kontrollen bereit sind.';

  @override
  String get chatAgentChannelAgentTitle => 'Kanal-Agent';

  @override
  String get chatAgentChannelAgentDescription =>
      'Ein Helfer für einen Kanal oder Projektraum kann nur über ein von Admins freigegebenes Paket aktiviert werden.';

  @override
  String get chatAgentPersonalScope =>
      'Nutzt nur Kontext, den du für die aktuelle Anfrage auswählst; Workspace-Policies entscheiden, welche Fähigkeiten verfügbar sind.';

  @override
  String get chatAgentPersonalBoundary =>
      'Kein dauerhaftes Mitlesen von Räumen; ein Kontextpaket wird erst nach deinem Start oder deiner Freigabe zusammengestellt.';

  @override
  String get chatAgentPersonalAudit =>
      'Erstellung, Kontextzugriff, Tool-Nutzung und Berechtigungsänderungen werden vor Laufzeitnutzung auditierbar.';

  @override
  String get chatAgentChannelScope =>
      'Owner oder Admins müssen das Paket aktivieren und erlaubte Chat-, Datei-, Kalender- und Board-Bereiche auswählen.';

  @override
  String get chatAgentChannelBoundary =>
      'Der Agent sieht benannte Bereiche und explizite Kontextpakete, nicht jede Nachricht im Workspace.';

  @override
  String get chatAgentChannelAudit =>
      'Freigaben, Widerrufe und Aktionsversuche bleiben für Admins sichtbar, ohne Secrets in der App offenzulegen.';

  @override
  String get chatAgentStartDisabledButton => 'Nicht verfügbar, bis aktiviert';

  @override
  String get chatLoadingLabel => 'Unterhaltungen werden geladen…';

  @override
  String get chatLoadingHint =>
      'Deine aktuellen Räume und der letzte Gesprächsstand werden geladen.';

  @override
  String get chatConnectingLabel => 'Verbinde mit Chat…';

  @override
  String get chatConnectingHint =>
      'Die sichere Weave-Chat-Sitzung wird geöffnet und die erste Unterhaltungsliste synchronisiert.';

  @override
  String get chatConnectButton => 'Chat verbinden';

  @override
  String get chatRefreshingRoomsLabel => 'Chaträume werden aktualisiert';

  @override
  String get chatStaleRoomsTitle => 'Zuletzt bekannte Räume werden angezeigt';

  @override
  String get chatStaleRoomsGuidance =>
      'Chat konnte gerade nicht aktualisiert werden. Deine Unterhaltungsliste bleibt erhalten, damit du den Überblick behältst und es erneut versuchen kannst, sobald die Verbindung zurück ist.';

  @override
  String get chatStaleRoomsRetryButton => 'Räume aktualisieren';

  @override
  String channelWorkspaceSummaryTitle(String channelName) {
    return 'Arbeitsraum $channelName';
  }

  @override
  String get channelWorkspaceSummaryDescription =>
      'Dieser Channel ist ein Arbeitsraum mit Tabs für Chat, Dateien, Boards, Kalender und Meetings, wenn der Arbeitsbereich sie aktiviert hat.';

  @override
  String get channelWorkspaceGovernanceNote =>
      'Kontext bleibt ausdrücklich: kein verstecktes dauerhaftes Mitlesen und kein Admin-Setup für normale Mitglieder.';

  @override
  String channelWorkspaceTabsSemanticLabel(String channelName) {
    return 'Channel-Arbeitsraum-Tabs für $channelName';
  }

  @override
  String get channelWorkspaceChatTab => 'Chat';

  @override
  String get channelWorkspaceFilesTab => 'Dateien';

  @override
  String get channelWorkspaceBoardsTab => 'Boards';

  @override
  String get channelWorkspaceCalendarTab => 'Kalender';

  @override
  String get channelWorkspaceMeetingsTab => 'Meetings';

  @override
  String get channelWorkspaceChatTitle => 'Channel-Chat';

  @override
  String get channelWorkspaceFilesTitle => 'Channel-Dateien';

  @override
  String get channelWorkspaceBoardsTitle => 'Channel-Boards und Aufgaben';

  @override
  String get channelWorkspaceCalendarTitle => 'Channel-Kalender';

  @override
  String get channelWorkspaceMeetingsTitle => 'Channel-Meetings';

  @override
  String get channelWorkspaceChatDescription =>
      'Nachrichten bleiben der standardmäßige Live-Kontext für diesen Channel.';

  @override
  String get channelWorkspaceFilesDescription =>
      'Channel-Dateien sind für diesen Workspace noch nicht aktiviert. Bitte einen Workspace-Owner oder Admin, die Einrichtung abzuschließen; der Chat bleibt verfügbar.';

  @override
  String get channelWorkspaceBoardsDescription =>
      'Channel-Boards und Aufgaben sind für diesen Workspace noch nicht aktiviert. Bitte einen Workspace-Owner oder Admin, die Einrichtung abzuschließen; der Chat bleibt verfügbar.';

  @override
  String get channelWorkspaceCalendarDescription =>
      'Channel-Termine bleiben gesperrt, bis die Kalender-Scope-Fähigkeit für diesen Arbeitsbereich verfügbar ist.';

  @override
  String get channelWorkspaceMeetingsDescription =>
      'Meetings werden mit Kalender, Agenda, Entscheidungen, Aufgaben, Dateien und Folge-Nachweisen dieses Channels verknüpft, nachdem ein Workspace-Owner oder Admin die Meeting-Fähigkeit aktiviert hat.';

  @override
  String get channelWorkspaceMeetingsCapabilityTitle =>
      'Meeting-Bereitschaft ist fail-closed';

  @override
  String get channelWorkspaceMeetingsCapabilityBody =>
      'Beitreten und Starten bleiben deaktiviert, bis Meeting-Status, Medienpfade, Untertitel, Aufzeichnungen, Medienverschlüsselung und Metadatengrenzen im Workspace-Health-Nachweis belegt sind.';

  @override
  String get channelWorkspaceMeetingsPrivacyTitle =>
      'Nur ausdrücklicher Meeting-Kontext';

  @override
  String channelWorkspaceMeetingsPrivacyBody(String channelName) {
    return 'Nur ausdrücklich aus $channelName ausgewählter Kontext wird für ein Meeting vorbereitet. Weave liest, zeichnet oder transkribiert den Raum nicht dauerhaft im Hintergrund.';
  }

  @override
  String get channelWorkspaceMeetingsRecordingOff =>
      'Aufzeichnung und Transkription aus';

  @override
  String get channelWorkspaceMeetingsContextTitle =>
      'Kontextpaket für dieses Meeting';

  @override
  String get channelWorkspaceMeetingsContextBody =>
      'Ein Meeting-Paket ist ausdrücklich und vor dem Beitritt prüfbar.';

  @override
  String get channelWorkspaceMeetingsContextAgenda => 'Agenda';

  @override
  String get channelWorkspaceMeetingsContextFiles => 'Dateien';

  @override
  String get channelWorkspaceMeetingsContextDecisions => 'Entscheidungen';

  @override
  String get channelWorkspaceMeetingsContextTasks => 'Aufgaben';

  @override
  String get channelWorkspaceMeetingsContextEvidence => 'Folge-Nachweise';

  @override
  String get channelWorkspaceMeetingsJoinButton => 'Meeting beitreten';

  @override
  String get channelWorkspaceMeetingsStartButton => 'Meeting starten';

  @override
  String get channelWorkspaceMeetingsBackendUnavailableReason =>
      'Die Meeting-Fähigkeit ist noch nicht aktiviert.';

  @override
  String get channelWorkspaceStatusAvailable => 'Verfügbar';

  @override
  String get channelWorkspaceStatusGated => 'Nicht verfügbar, bis aktiviert';

  @override
  String channelWorkspaceExplicitContextNote(String channelName) {
    return 'Hier wird nur ausdrücklicher Kontext aus $channelName gezeigt; Weave liest den Raum nicht dauerhaft im Hintergrund mit.';
  }

  @override
  String get filesScreenTitle => 'Dateien';

  @override
  String get filesLoadingLabel => 'Dateien werden geladen…';

  @override
  String get filesLoadingHint =>
      'Der aktuelle Ordner wird aktualisiert und auf Änderungen geprüft.';

  @override
  String get filesStaleDirectoryTitle => 'Letzten bekannten Ordner anzeigen';

  @override
  String get filesStaleDirectoryGuidance =>
      'Weave konnte Dateien gerade nicht aktualisieren. Die letzte Ordnerliste bleibt sichtbar, damit du deinen Platz behältst und es erneut versuchen kannst, sobald die Verbindung wieder da ist.';

  @override
  String get filesStaleDirectoryRetryButton => 'Ordner aktualisieren';

  @override
  String get filesNextcloudTitle => 'Weave-Dateien';

  @override
  String get filesProductTitle => 'Weave-Dateien';

  @override
  String get filesProductBoundaryTitle => 'Weave-Produktgrenze';

  @override
  String get filesProductBoundaryBody =>
      'Dateiaktionen nutzen die Weave-Backend-Fassade. Nextcloud bleibt Speicheranbieter sowie Admin-/Fallback-Oberfläche; rohe Anbieterpfade und Zugangsdaten gehören nicht zur normalen Dateien-UX.';

  @override
  String get filesConnectButton => 'Dateien verbinden';

  @override
  String get filesReconnectButton => 'Dateien neu verbinden';

  @override
  String get filesDisconnectButton => 'Trennen';

  @override
  String get filesRefreshButton => 'Aktualisieren';

  @override
  String get filesUpButton => 'Nach oben';

  @override
  String get filesRootBreadcrumb => 'Root';

  @override
  String filesOpenFolderSemantic(String name) {
    return 'Ordner öffnen: $name';
  }

  @override
  String filesCurrentFolderSemantic(String name) {
    return 'Aktueller Ordner: $name';
  }

  @override
  String filesDirectorySummary(int folderCount, int fileCount) {
    String _temp0 = intl.Intl.pluralLogic(
      folderCount,
      locale: localeName,
      other: '$folderCount Ordner',
      one: '1 Ordner',
      zero: 'Keine Ordner',
    );
    String _temp1 = intl.Intl.pluralLogic(
      fileCount,
      locale: localeName,
      other: '$fileCount Dateien',
      one: '1 Datei',
      zero: 'keine Dateien',
    );
    return '$_temp0 • $_temp1';
  }

  @override
  String get filesDisconnectedMessage =>
      'Verbinde Weave-Dateien, um Arbeitsbereichsdateien zu durchsuchen.';

  @override
  String get filesInvalidSessionMessage =>
      'Verbinde Dateien neu, weil die Weave-Sitzung nicht mehr gültig ist.';

  @override
  String get filesMisconfiguredMessage =>
      'Schließe zuerst die Weave-Servereinrichtung ab, bevor du Dateien verbindest.';

  @override
  String filesConnectionConnected(String accountLabel) {
    return 'Verbunden als $accountLabel';
  }

  @override
  String get filesConnectionDisconnected =>
      'Dateien sind für diese Weave-Sitzung nicht verbunden.';

  @override
  String get filesConnectionInvalid =>
      'Die Weave-Dateisitzung braucht Aufmerksamkeit.';

  @override
  String get filesConnectionMisconfigured =>
      'Die Server-Einrichtung für Weave-Dateien ist unvollständig.';

  @override
  String get filesOpenParentSemantic => 'Übergeordneten Ordner öffnen';

  @override
  String get filesRefreshCurrentFolderSemantic =>
      'Aktuellen Ordner aktualisieren';

  @override
  String get filesUploadButton => 'Hochladen';

  @override
  String get filesUploadCurrentFolderSemantic =>
      'Datei in den aktuellen Ordner hochladen';

  @override
  String get filesCreateFolderButton => 'Neuer Ordner';

  @override
  String get filesCreateFolderCurrentFolderSemantic =>
      'Ordner im aktuellen Ordner erstellen';

  @override
  String get filesCreateFolderDialogTitle => 'Ordner erstellen';

  @override
  String get filesCreateFolderNameLabel => 'Ordnername';

  @override
  String get filesCreateFolderNameHint => 'z. B. Projektdokumente';

  @override
  String get filesCreateFolderConfirmButton => 'Erstellen';

  @override
  String get filesCancelButton => 'Abbrechen';

  @override
  String get filesDeleteButton => 'Löschen';

  @override
  String filesExportEntrySemantic(String name) {
    return '$name in native Dateien exportieren';
  }

  @override
  String filesExportProgressMessage(String name) {
    return '$name wird exportiert…';
  }

  @override
  String get filesExportProgressUnknownMessage => 'Datei wird exportiert…';

  @override
  String filesExportCompletedMessage(String name, String destination) {
    return '$name wurde nach $destination exportiert.';
  }

  @override
  String get filesExportCompletedUnknownMessage =>
      'Datei wurde in native Dateien exportiert.';

  @override
  String get filesExportUserVisibleFallback =>
      'einen benutzersichtbaren Dateien-Ort';

  @override
  String filesDeleteEntrySemantic(String name) {
    return '$name löschen';
  }

  @override
  String filesDeleteEntryDialogTitle(String name) {
    return '$name löschen?';
  }

  @override
  String get filesDeleteEntryDialogMessage =>
      'Dadurch wird es aus Weave-Dateien für alle mit Zugriff entfernt. Dies kann nicht rückgängig gemacht werden.';

  @override
  String get filesCreateFolderProgressUnknownMessage => 'Ordner wird erstellt…';

  @override
  String filesCreateFolderProgressMessage(String folderName) {
    return 'Ordner $folderName wird erstellt…';
  }

  @override
  String get filesCreateFolderCompletedUnknownMessage => 'Ordner erstellt.';

  @override
  String filesCreateFolderCompletedMessage(String folderName) {
    return 'Ordner $folderName erstellt.';
  }

  @override
  String get filesDeleteProgressUnknownMessage => 'Element wird gelöscht…';

  @override
  String filesDeleteProgressMessage(String name) {
    return '$name wird gelöscht…';
  }

  @override
  String get filesDeleteCompletedUnknownMessage => 'Element gelöscht.';

  @override
  String filesDeleteCompletedMessage(String name) {
    return '$name gelöscht.';
  }

  @override
  String get filesEntryActionFailedMessage => 'Dateiaktion fehlgeschlagen.';

  @override
  String get filesUploadPickingMessage => 'Datei zum Hochladen auswählen…';

  @override
  String get filesUploadProgressUnknownMessage => 'Datei wird hochgeladen…';

  @override
  String filesUploadProgressIndeterminateMessage(String fileName) {
    return '$fileName wird hochgeladen…';
  }

  @override
  String filesUploadProgressMessage(String fileName, int percent) {
    return '$fileName wird hochgeladen: $percent%';
  }

  @override
  String filesUploadProgressSemantic(String fileName, int percent) {
    return 'Upload-Fortschritt für $fileName: $percent Prozent';
  }

  @override
  String get filesUploadCompletedUnknownMessage => 'Upload abgeschlossen.';

  @override
  String filesUploadCompletedMessage(String fileName) {
    return '$fileName hochgeladen.';
  }

  @override
  String get filesUploadFailedUnknownMessage => 'Upload fehlgeschlagen.';

  @override
  String filesUploadFailedMessage(String fileName) {
    return 'Upload für $fileName fehlgeschlagen.';
  }

  @override
  String filesFolderSemantic(String name) {
    return '$name, Ordner';
  }

  @override
  String filesFileSemantic(String name) {
    return '$name, Datei';
  }

  @override
  String get calendarScreenTitle => 'Kalender';

  @override
  String get deckScreenTitle => 'Boards-Workspace';

  @override
  String get settingsScreenTitle => 'Einstellungen';

  @override
  String get settingsBrandSectionDescription =>
      'Weave bringt Nachrichten, Dateien und Kalender in einem Workspace zusammen, während auf diesem Bildschirm die Serververbindung dahinter verwaltet wird.';

  @override
  String get settingsThemeTitle => 'Darstellung';

  @override
  String get settingsThemeDescription =>
      'Wähle den visuellen Stil für dieses Profil. Workspace-Branding bleibt getrennt, damit deine persönliche Auswahl nicht von der Admin-Einrichtung überschrieben wird.';

  @override
  String get settingsThemeSystemTitle => 'Geräteeinstellung nutzen';

  @override
  String get settingsThemeSystemDescription =>
      'Folgt der hellen oder dunklen Darstellung deines Geräts.';

  @override
  String get settingsThemeLightTitle => 'Hell';

  @override
  String get settingsThemeLightDescription =>
      'Nutzt eine helle professionelle Farbpalette.';

  @override
  String get settingsThemeDarkTitle => 'Dunkel';

  @override
  String get settingsThemeDarkDescription =>
      'Nutzt eine dunklere Farbpalette für Arbeit bei wenig Licht.';

  @override
  String get settingsThemeHighContrastTitle => 'Hoher Kontrast';

  @override
  String get settingsThemeHighContrastDescription =>
      'Nutzt stärkere Kontraste und folgt weiter der hellen oder dunklen Geräteeinstellung.';

  @override
  String get settingsThemeLoading =>
      'Darstellungseinstellungen werden geladen…';

  @override
  String get settingsThemeError =>
      'Darstellungseinstellungen konnten nicht gespeichert werden. Versuche, die Einstellung erneut zu ändern.';

  @override
  String get settingsHelpTitle => 'Hilfe und Benutzerhandbuch';

  @override
  String get settingsHelpDescription =>
      'Öffne praktische Hilfe zur Nutzung von Weave, zur Wiederherstellung bei Problemen und zu Datenschutz-Grundlagen.';

  @override
  String get helpScreenTitle => 'Hilfe';

  @override
  String get helpHandbookTitle => 'Benutzerhandbuch';

  @override
  String get helpHandbookDescription =>
      'Dieses Handbuch erklärt die tägliche Weave-App in klarer Sprache. Es ist offline mit der App verfügbar und wächst, wenn weitere Bereiche bereit werden.';

  @override
  String get helpEmbeddedManualTitle => 'Eingebettetes Benutzerhandbuch';

  @override
  String get helpEmbeddedManualDescription =>
      'Die Hilfe bettet das MkDocs-Benutzerhandbuch als eingeschränkte Produktoberfläche ein. Sie folgt den Weave-Design-Tokens, hält Überschriften per Tastatur erreichbar und benötigt keine Provider-Zugangsdaten oder rohen Dienst-URLs.';

  @override
  String get helpEmbeddedManualPathLabel => 'Handbuchquelle:';

  @override
  String get helpEmbeddedManualPermissionLabel =>
      'Eingeschränkte Einbettung: kein breiter Skript-, Kamera-, Mikrofon- oder Provider-Zugriff';

  @override
  String get helpEmbeddedManualUnavailableLabel =>
      'Wenn gebaute Dokumentation nicht verfügbar ist, zeigt Weave diesen support-sicheren Fallback statt Dateisystem- oder Provider-Fehlern.';

  @override
  String get helpWhatIsWeaveTitle => 'Was Weave ist';

  @override
  String get helpWhatIsWeaveBody =>
      'Weave ist eine Zusammenarbeits-App für Teams, die einen barrierearmen Workspace nutzen möchten, ohne Datensouveränität aufzugeben. Chat, Dateien, Kontoeinstellungen und weitere Zusammenarbeitsmodule erscheinen in Weave, während offene Dienste wie Matrix, Nextcloud, Keycloak und das Weave-Backend im Hintergrund arbeiten.';

  @override
  String get helpSignInTitle => 'Anmelden: Grundlagen';

  @override
  String get helpSignInBody =>
      'Nutze die Workspace-Adresse deines Admins und melde dich dann einmal mit Weave-SSO an. Für die normale Nutzung solltest du keine separaten Matrix- oder Nextcloud-Passwörter brauchen. Wenn die Anmeldung scheitert oder in einer Schleife hängt, prüfe deine Verbindung, bestätige die Serveradresse in den Einstellungen und frage einen Admin, ob deine Einladung oder dein Konto aktiv ist.';

  @override
  String get helpChatTitle => 'Chat';

  @override
  String get helpChatBody =>
      'Chat ist der tägliche Ort für Unterhaltungen und Nachrichten. Öffne Chat über die Hauptnavigation und wähle dann eine Unterhaltung. Weave zeigt Unterhaltungs- und Wiederherstellungszustände sichtbar an, damit du erkennst, ob Chat verbunden ist, wartet, beeinträchtigt ist oder Admin-Aufmerksamkeit braucht.';

  @override
  String get helpFilesTitle => 'Dateien';

  @override
  String get helpFilesBody =>
      'Dateien lässt dich Workspace-Dokumente in der Weave-App durchsuchen. Öffne Dateien über die Hauptnavigation, wechsle durch Ordner und versuche es erneut, wenn ein Ordner nicht aktualisiert werden konnte. Der Speicher liegt in den Diensten deines Workspaces, aber das alltägliche Durchsuchen soll in Weave bleiben.';

  @override
  String get helpSettingsTitle => 'Einstellungen, Konto und Sitzung';

  @override
  String get helpSettingsBody =>
      'Einstellungen zeigt deine Profilübersicht, den Workspace-Status, die Serverkonfiguration, Matrix-Sicherheitsinformationen und die Abmeldeaktion. Nutze diesen Bereich, um zu prüfen, ob Chat, Dateien, Kalender oder andere Module bereit sind, und melde dich ab, bevor du ein Gerät an jemand anderen weitergibst.';

  @override
  String get helpCalendarBoardsTitle => 'Verfügbarkeit von Kalender und Boards';

  @override
  String get helpCalendarBoardsBody =>
      'Kalender und Boards gehören zum aktiven Weave-Produktscope, können aber ausgeblendet oder als nicht verfügbar markiert sein, bis dein Workspace die nötigen Backend-Verträge und Feature-Gates aktiviert hat. Wenn sie nicht in der Navigation erscheinen, nutze vorerst Chat, Dateien und Einstellungen und achte auf Änderungen im Workspace-Status.';

  @override
  String get helpTroubleshootingTitle => 'Fehlersuche und Wiederherstellung';

  @override
  String get helpTroubleshootingBody =>
      'Wenn etwas nicht lädt, nutze zuerst Erneut versuchen. Wenn eine alte Chat-Raumliste oder ein alter Ordner sichtbar bleibt, bewahrt Weave deinen Kontext, während die Aktualisierung fehlschlägt. Dauerhafte Einrichtungs-, Anmelde-, Matrix-, Datei- oder Backend-Fehler solltest du deinem Admin zusammen mit der sichtbaren Meldung und der Serveradresse aus den Einstellungen melden.';

  @override
  String get helpPrivacySecurityTitle =>
      'Datenschutz und Sicherheit: Grundlagen';

  @override
  String get helpPrivacySecurityBody =>
      'Dein Workspace kontrolliert seine eigenen Dienste und Daten. Weave nutzt SSO für den Zugriff und zeigt den Matrix-Sicherheitsstatus ehrlich an. Gehe nicht davon aus, dass Chat vollständig Ende-zu-Ende-verschlüsselt ist, solange Weave nicht meldet, dass Matrix-Verschlüsselung, Wiederherstellung und Gerätevertrauen gesund sind. Bewahre Wiederherstellungsschlüssel sicher auf und melde verlorene Geräte deinem Admin.';

  @override
  String get settingsShellModulesTitle => 'Shell-Module';

  @override
  String get settingsShellModulesDescription =>
      'Wähle, welche Workspace-Shell-Module sichtbar bleiben. Die Navigation bleibt verfügbar, auch wenn ein Modul ausgeblendet ist.';

  @override
  String get settingsShellWorkspaceStatusToggleTitle =>
      'Workspace-Statusübersicht';

  @override
  String get settingsShellWorkspaceStatusToggleDescription =>
      'Zeigt Dienstbereitschaft und Wiederherstellungsabkürzungen oberhalb der unteren Navigation an.';

  @override
  String settingsShellMoveModuleUp(String moduleName) {
    return '$moduleName nach oben verschieben';
  }

  @override
  String settingsShellMoveModuleDown(String moduleName) {
    return '$moduleName nach unten verschieben';
  }

  @override
  String get settingsShellRecentActivityToggleTitle =>
      'Schnellzugriffe für letzte Aktivität';

  @override
  String get settingsShellRecentActivityToggleDescription =>
      'Zeigt aktuelle Räume und Dateiänderungen oberhalb der unteren Navigation an.';

  @override
  String get settingsShellModulesLoading =>
      'Shell-Modul-Einstellungen werden geladen…';

  @override
  String get settingsShellModulesError =>
      'Shell-Modul-Einstellungen konnten nicht gespeichert werden. Versuche, die Einstellung erneut zu ändern.';

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
  String get chatSecurityBoundaryCardTitle => 'Backend- und Agent-Grenze';

  @override
  String get chatSecurityBoundaryCardValue => 'Bis Consent/Audit blockiert';

  @override
  String get chatSecurityBoundaryCardBody =>
      'Verschlüsselte Nachrichteninhalte bleiben auf Matrix-Geräten. Backend-Diagnosen dürfen support-sichere Metadaten wie Raum-ID, Verschlüsselungsstatus, Gerätevertrauen und Zeitstempel verwenden, aber keine entschlüsselten Nachrichteninhalte. Bots und Connectoren bleiben in verschlüsselten Räumen blockiert, bis Consent-, Audit-, Gerätevertrauens- und Client-Identitäts-Gates umgesetzt sind.';

  @override
  String get chatSecurityRecoveryGuidanceCardTitle =>
      'Checkliste zur Gerätewiederherstellung';

  @override
  String get chatSecurityRecoveryGuidanceValueActionRequired =>
      'Aktion erforderlich';

  @override
  String get chatSecurityRecoveryGuidanceValueReady =>
      'Bereit für Gerätewechsel';

  @override
  String get chatSecurityRecoveryGuidanceIntro =>
      'Bevor verschlüsselter Chat geräteübergreifend verlässlich genutzt wird:';

  @override
  String get chatSecurityRecoveryGuidanceSaveRecovery =>
      'Speichere den Wiederherstellungsschlüssel oder die Passphrase außerhalb von Weave, am besten in einem Passwortmanager.';

  @override
  String get chatSecurityRecoveryGuidanceVerifyDevice =>
      'Verifiziere dieses Gerät möglichst mit einem anderen angemeldeten Matrix-Gerät.';

  @override
  String get chatSecurityRecoveryGuidanceNewDevice =>
      'Nutze auf einem neuen oder neu installierten Gerät zuerst den Wiederherstellungsschlüssel und verifiziere danach das Gerät.';

  @override
  String get chatSecurityRecoveryGuidanceLostDevice =>
      'Wenn ein Gerät verloren geht, entferne es oder entziehe ihm das Vertrauen in Matrix, bevor du neuen verschlüsselten Räumen vertraust.';

  @override
  String get chatSecurityRecoveryGuidanceServerCannotRecover =>
      'Weave-Server können sichere Metadaten melden, aber keine verschlüsselten Nachrichteninhalte für dich wiederherstellen.';

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
  String get settingsPreviewSurfacesTitle => 'Vorschau-Oberflächen';

  @override
  String get settingsPreviewSurfacesDescription =>
      'Diese feature-gegateten Bereiche bleiben ehrlich darüber, was aktiv, blockiert oder noch von Backend-Verträgen abhängig ist.';

  @override
  String get settingsGuestPortalPreviewTitle => 'Gastportal';

  @override
  String get settingsGuestPortalPreviewDescription =>
      'Gasteinladungen und eingeschränkter Zugriff erscheinen hier, ohne Mitgliederfunktionen offenzulegen.';

  @override
  String get settingsInteropAdminPreviewTitle =>
      'Adminstatus für externe Verbindungen';

  @override
  String get settingsInteropAdminPreviewDescription =>
      'Der Status externer Anbieter erklärt Datenbewegung und Einwilligung; Anbieter-Secrets werden in diesem Client nie erfasst.';

  @override
  String get settingsMigrationDryRunPreviewTitle =>
      'Migrationsbericht als Trockenlauf';

  @override
  String get settingsMigrationDryRunPreviewDescription =>
      'Admins können Inventar, Risiken, Berechtigungsbereiche und Zuordnungen prüfen, bevor ein Import startet.';

  @override
  String get settingsServerConfigurationTitle => 'Serverkonfiguration';

  @override
  String get settingsWorkspaceReadinessTitle => 'Workspace-Status';

  @override
  String get settingsWorkspaceReadinessDescription =>
      'Der Zugriff auf die App-Shell wird getrennt von den einzelnen Dienstverbindungen verfolgt, damit Weave beeinträchtigte Integrationen transparent anzeigen kann.';

  @override
  String get settingsWorkspaceBackendUnreachable =>
      'Backend-API ist nicht erreichbar. Prüfe, ob der Weave-Stack läuft und die konfigurierte Backend-URL korrekt ist.';

  @override
  String get settingsWorkspaceBackendUnauthorized =>
      'Backend-API hat die aktuelle Sitzung abgewiesen. Bitte erneut anmelden.';

  @override
  String get settingsWorkspaceBackendServerError =>
      'Backend-API hat eine unerwartete Antwort zurückgegeben. Prüfe die Weave-Stack-Logs.';

  @override
  String get settingsWorkspaceSummaryConnected =>
      'Shell-Zugriff und die zugeordneten Dienste sind bereit.';

  @override
  String get settingsWorkspaceSummaryDegraded =>
      'Der Shell-Zugriff ist bereit, aber ein oder mehrere Dienste benötigen noch Aufmerksamkeit.';

  @override
  String get settingsWorkspaceSummaryNeedsSetup =>
      'Schließe die Einrichtung ab, bevor die Workspace-Shell verfügbar werden kann.';

  @override
  String get settingsWorkspaceSummaryNeedsSignIn =>
      'Melde dich erneut an, um den Zugriff auf die Workspace-Shell wiederherzustellen.';

  @override
  String get settingsWorkspaceShellAccessLabel => 'Shell-Zugriff';

  @override
  String get settingsWorkspaceChatLabel => 'Chat';

  @override
  String get settingsWorkspaceFilesLabel => 'Dateien';

  @override
  String get settingsWorkspaceCapabilityLabel => 'Bereitschaft';

  @override
  String get settingsWorkspaceConnectionLabel => 'Verbindung';

  @override
  String get settingsWorkspaceLastChangeLabel => 'Letzte Änderung';

  @override
  String get settingsWorkspaceMatrixE2eeGateLabel => 'E2EE-Gate';

  @override
  String get settingsWorkspaceMatrixE2eeValidated => 'Validiert';

  @override
  String get settingsWorkspaceMatrixE2eeNotValidated => 'Nicht validiert';

  @override
  String get settingsWorkspaceMatrixServerBodiesLabel =>
      'Server-lesbare Inhalte';

  @override
  String get settingsWorkspaceMatrixServerBodiesOpaque => 'Nein';

  @override
  String get settingsWorkspaceMatrixServerBodiesReadable => 'Prüfen';

  @override
  String get settingsWorkspaceMatrixAgentWritesLabel => 'Agent-Schreibzugriff';

  @override
  String get settingsWorkspaceMatrixAgentWritesBlocked =>
      'Blockiert/fail-closed';

  @override
  String get settingsWorkspaceMatrixAgentWritesReview => 'Policy prüfen';

  @override
  String get settingsWorkspaceCapabilityReady => 'Bereit';

  @override
  String get settingsWorkspaceCapabilityDegraded => 'Beeinträchtigt';

  @override
  String get settingsWorkspaceCapabilityBlocked => 'Blockiert';

  @override
  String get settingsWorkspaceCapabilityUnavailable => 'Nicht verfügbar';

  @override
  String get settingsWorkspaceConnectionConnected => 'Verbunden';

  @override
  String get settingsWorkspaceConnectionDisconnected => 'Nicht verbunden';

  @override
  String get settingsWorkspaceConnectionDegraded => 'Beeinträchtigt';

  @override
  String get settingsWorkspaceConnectionMisconfigured => 'Fehlkonfiguriert';

  @override
  String get settingsWorkspaceConnectionRequiresReauthentication =>
      'Anmeldung nötig';

  @override
  String get settingsWorkspaceConnectionUnavailableOnPlatform =>
      'Auf dieser Plattform nicht verfügbar';

  @override
  String get settingsWorkspaceInvalidationAuthConfigurationChanged =>
      'Anmeldekonfiguration geändert';

  @override
  String get settingsWorkspaceInvalidationMatrixHomeserverChanged =>
      'Matrix-Homeserver geändert';

  @override
  String get settingsWorkspaceInvalidationNextcloudBaseUrlChanged =>
      'Nextcloud-Basis-URL geändert';

  @override
  String get settingsWorkspaceInvalidationExplicitSignOut =>
      'Manuell abgemeldet';

  @override
  String get settingsWorkspaceInvalidationRestartSetup =>
      'Einrichtung neu gestartet';

  @override
  String get settingsWorkspaceInvalidationBackendApiBaseUrlChanged =>
      'Backend-API-URL geändert';

  @override
  String get settingsProviderStackTitle => 'Provider-Stack-Bereitschaft';

  @override
  String settingsProviderStackSemanticLabel(
    String backendOwnedFacades,
    String flutterCalls,
  ) {
    return 'Bereitschaft des Provider-Stacks. Backend-Fassaden: $backendOwnedFacades. Flutter-Provider-Aufrufe: $flutterCalls.';
  }

  @override
  String get settingsProviderStackFailClosedDescription =>
      'Provider-Integrationen bleiben fail-closed hinter Backend-eigenen Fassaden. Flutter ruft Nextcloud, Matrix-Auth, OpenProject, Office oder andere Provider-APIs nicht direkt auf.';

  @override
  String get settingsProviderStackNeedsReviewDescription =>
      'Die Provider-Bereitschaft muss geprüft werden, bevor direkte Starts oder Workspace-Aktionen aktiviert werden.';

  @override
  String get settingsProviderStackBackendFacadesLabel => 'Backend-Fassaden';

  @override
  String get settingsProviderStackFlutterCallsLabel =>
      'Flutter-Provider-Aufrufe';

  @override
  String get settingsProviderStackSupportSafetyLabel => 'Support-Sicherheit';

  @override
  String get settingsProviderStackOwned => 'Backend-eigen';

  @override
  String get settingsProviderStackMissing => 'Fehlt';

  @override
  String get settingsProviderStackNeedsReview => 'Prüfung nötig';

  @override
  String get settingsProviderStackBlocked => 'Blockiert';

  @override
  String get settingsProviderStackRedacted => 'Redigiert';

  @override
  String get settingsProviderStackYes => 'Ja';

  @override
  String get settingsProviderStackNo => 'Nein';

  @override
  String get settingsProviderStackFlutterCallsAllowed => 'Erlaubt';

  @override
  String get settingsProviderStackFlutterCallsBlocked => 'Blockiert';

  @override
  String get settingsProviderStackFailClosedBadge => 'fail-closed';

  @override
  String get settingsProviderStackReadOnlyBadge => 'nur lesend';

  @override
  String get settingsProviderStackPaidFeaturesRequiredBadge =>
      'kostenpflichtige Features nötig';

  @override
  String get settingsProviderModuleIdentityRealm => 'Identity-Realm';

  @override
  String get settingsProviderModuleSourceControl => 'Quellcodeverwaltung';

  @override
  String get settingsProviderModuleIssueTracker => 'Issue-Tracker';

  @override
  String get settingsProviderModuleCi => 'CI';

  @override
  String get settingsProviderModuleRelease => 'Release';

  @override
  String get settingsProviderModuleOffice => 'Office';

  @override
  String get settingsProviderModuleFiles => 'Dateien';

  @override
  String get settingsProviderModuleCalendar => 'Kalender';

  @override
  String get settingsProviderModuleContacts => 'Kontakte';

  @override
  String get settingsProviderModuleForms => 'Formulare';

  @override
  String get settingsProviderModuleMatrix => 'Matrix-Chat';

  @override
  String get settingsProviderModuleMatrixAuth => 'Matrix-Auth';

  @override
  String get settingsProviderModuleMeetings => 'Meetings';

  @override
  String get settingsProviderModuleBoards => 'Boards';

  @override
  String get settingsProviderModuleProvider => 'Provider';

  @override
  String get settingsProviderStateDisabled => 'deaktiviert';

  @override
  String get settingsProviderStateNotConfigured => 'unkonfiguriert';

  @override
  String get settingsProviderStateConfigured => 'konfiguriert';

  @override
  String get settingsProviderStateReady => 'bereit';

  @override
  String get settingsProviderStateDegraded => 'eingeschränkt';

  @override
  String get settingsProviderStateUnsupported => 'nicht unterstützt';

  @override
  String get settingsProviderStateUnknown => 'unbekannt';

  @override
  String get settingsOfficeReadinessTitle => 'Office-Bereitschaft';

  @override
  String settingsOfficeReadinessSemanticLabel(
    String launchState,
    String providerState,
  ) {
    return 'Office-Bereitschaft. Start ist $launchState. Provider ist $providerState.';
  }

  @override
  String get settingsOfficeReadinessFailClosedDescription =>
      'Office-Start bleibt fail-closed, bis ein Backend-eigener Provider-Adapter, Session-Tokens, Callbacks und Berechtigungen konfiguriert sind.';

  @override
  String get settingsOfficeReadinessAvailableDescription =>
      'Office-Start ist über die Backend-Fassade verfügbar.';

  @override
  String get settingsOfficeReadinessAvailable => 'verfügbar';

  @override
  String get settingsOfficeReadinessEnabled => 'aktiviert';

  @override
  String get settingsOfficeReadinessNoLaunchModes => 'keine Startmodi';

  @override
  String settingsOfficeReadinessModes(String modes) {
    return 'Modi: $modes';
  }

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
  String get chatEmptyGuidance =>
      'Arbeitsbereichsräume und Direktnachrichten erscheinen hier, sobald Chat bereit ist.';

  @override
  String get chatErrorTitle => 'Chat ist gerade nicht verfügbar';

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
  String get chatConversationOpensChannelWorkspaceLabel =>
      'Öffnet den Channel-Arbeitsraum';

  @override
  String get chatConversationRecentNow => 'Gerade aktiv';

  @override
  String get chatConversationRecentToday => 'Heute';

  @override
  String get chatConversationRecentYesterday => 'Gestern';

  @override
  String get chatConversationRecentThisWeek => 'Diese Woche';

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
  String get chatRoomLoadingLabel => 'Konversation wird geladen…';

  @override
  String get chatRoomEmptyMessage => 'Noch keine Nachrichten';

  @override
  String get chatRoomDraftRestoredMessage =>
      'Entwurf von diesem Gerät wiederhergestellt.';

  @override
  String get chatRoomComposerHint => 'Nachricht schreiben';

  @override
  String get chatRoomComposerDisabledHint =>
      'Nachrichten sind in diesem Raum gerade nicht verfügbar';

  @override
  String get chatRoomSendButton => 'Senden';

  @override
  String get chatRoomSendingButton => 'Wird gesendet…';

  @override
  String get chatRoomRetrySendAction => 'Erneut senden';

  @override
  String get chatRoomLoadFailureMessage =>
      'Diese Unterhaltung konnte gerade nicht geladen werden.';

  @override
  String get chatRoomSendFailureMessage =>
      'Diese Nachricht konnte gerade nicht gesendet werden. Prüfe deine Verbindung und versuche es erneut.';

  @override
  String get chatRoomYouLabel => 'Du';

  @override
  String get chatRoomMessageSendingStatus => 'Wird gesendet…';

  @override
  String get chatRoomMessageFailedStatus => 'Nicht gesendet';

  @override
  String get chatRoomEncryptedMessageLabel => 'Verschlüsselte Nachricht';

  @override
  String get chatRoomUnsupportedMessageLabel => 'Nicht unterstützte Nachricht';

  @override
  String get chatRoomMessageActionsLabel => 'Nachrichtenaktionen';

  @override
  String get chatRoomArchiveAction => 'Archivieren';

  @override
  String get chatRoomArchiveDialogTitle => 'Nachricht archivieren?';

  @override
  String get chatRoomArchiveDialogMessage =>
      'Dadurch wird die Nachricht auf diesem Gerät aus deiner Hauptzeitleiste ausgeblendet. Du kannst sie unter Archivierte Nachrichten prüfen oder wiederherstellen.';

  @override
  String get chatRoomArchivedMessagesAction => 'Archivierte Nachrichten prüfen';

  @override
  String get chatRoomActiveTimelineAction => 'Zurück zur aktiven Zeitleiste';

  @override
  String get chatRoomArchivedReviewTitle => 'Archivierte Nachrichten';

  @override
  String chatRoomArchivedReviewDescription(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count archivierte Nachrichten werden getrennt von der aktiven Zeitleiste angezeigt.',
      one:
          '1 archivierte Nachricht wird getrennt von der aktiven Zeitleiste angezeigt.',
      zero:
          'Archivierte Nachrichten aus diesem Raum erscheinen hier getrennt von der aktiven Zeitleiste.',
    );
    return '$_temp0';
  }

  @override
  String get chatRoomArchivedReviewEmptyMessage =>
      'Noch keine archivierten Nachrichten.';

  @override
  String get chatRoomArchivedMessageLabel => 'Archiviert';

  @override
  String get chatRoomRestoreAction => 'In Zeitleiste wiederherstellen';

  @override
  String get chatRoomRestoreSuccessMessage =>
      'Nachricht in der aktiven Zeitleiste wiederhergestellt.';

  @override
  String get chatRoomRestoreFailureMessage =>
      'Diese Nachricht konnte gerade nicht wiederhergestellt werden.';

  @override
  String get chatRoomArchiveSuccessMessage => 'Nachricht archiviert.';

  @override
  String get chatRoomArchiveFailureMessage =>
      'Diese Nachricht konnte gerade nicht archiviert werden.';

  @override
  String get chatRoomArchivedEmptyMessage =>
      'Archivierte Nachrichten sind in dieser Zeitleiste ausgeblendet.';

  @override
  String get chatRoomContextPackTitle => 'Kontext für diesen Raum';

  @override
  String get chatRoomContextPackDescription =>
      'Weave nimmt nur begrenzten Kontext auf, den du hier sehen kannst, zum Beispiel diesen Raum, ausgewählte Dateien, verknüpfte Aufgaben und aktuelle Entscheidungen.';

  @override
  String chatRoomContextPackCounts(int includedCount, int availableCount) {
    String _temp0 = intl.Intl.pluralLogic(
      includedCount,
      locale: localeName,
      other: '$includedCount Quellen enthalten',
      one: '1 Quelle enthalten',
      zero: 'Keine Quellen enthalten',
    );
    String _temp1 = intl.Intl.pluralLogic(
      availableCount,
      locale: localeName,
      other: '$availableCount optionale Quellen verfügbar',
      one: '1 optionale Quelle verfügbar',
      zero: 'Keine optionalen Quellen verfügbar',
    );
    return '$_temp0. $_temp1.';
  }

  @override
  String get chatRoomContextPackNoBackgroundReading =>
      'Kein Agent liest diesen Raum im Hintergrund mit.';

  @override
  String get chatRoomContextCurrentRoomLabel => 'Aktueller Raum';

  @override
  String get chatRoomContextSelectedFilesLabel => 'Ausgewählte Dateien';

  @override
  String get chatRoomContextLinkedTasksLabel => 'Verknüpfte Aufgaben';

  @override
  String get chatRoomContextRecentDecisionsLabel => 'Aktuelle Entscheidungen';

  @override
  String get chatRoomContextIncludedStatus => 'Enthalten';

  @override
  String get chatRoomContextAvailableStatus => 'Verfügbar nach Auswahl';

  @override
  String get chatDecisionEvidencePanelTitle =>
      'Entscheidungen, Risiken, Fragen und Evidenz';

  @override
  String get chatDecisionEvidencePanelDescription =>
      'Erfasse wichtige Nachrichten bewusst, damit der Raum die Gründe hinter der Arbeit behält. Nichts hier entsteht durch verstecktes Mitlesen.';

  @override
  String get chatDecisionEvidenceNoBackgroundReading =>
      'Einträge entstehen aus Nachrichtenaktionen, die du auswählst; es läuft kein automatisches dauerhaftes Mitlesen.';

  @override
  String get chatDecisionEvidenceEmptyState =>
      'Noch keine Einträge erfasst. Nutze eine Nachrichtenaktion, um eine Entscheidung, ein Risiko, eine offene Frage oder Evidenz mit Quelle zu erfassen.';

  @override
  String chatDecisionEvidenceCountLabel(String label, int count) {
    return '$label: $count';
  }

  @override
  String get chatDecisionEvidenceDecisionLabel => 'Entscheidung';

  @override
  String get chatDecisionEvidenceDecisionsLabel => 'Entscheidungen';

  @override
  String get chatDecisionEvidenceRiskLabel => 'Risiko';

  @override
  String get chatDecisionEvidenceRisksLabel => 'Risiken';

  @override
  String get chatDecisionEvidenceOpenQuestionLabel => 'Offene Frage';

  @override
  String get chatDecisionEvidenceOpenQuestionsLabel => 'Offene Fragen';

  @override
  String get chatDecisionEvidenceEvidenceLabel => 'Evidenz';

  @override
  String get chatDecisionEvidenceEvidencePluralLabel => 'Evidenz';

  @override
  String get chatDecisionEvidenceOwnerYou => 'Du';

  @override
  String get chatDecisionEvidenceStatusActive => 'Aktiv';

  @override
  String get chatDecisionEvidenceStatusResolved => 'Erledigt';

  @override
  String get chatDecisionEvidenceStatusArchived => 'Archiviert';

  @override
  String chatDecisionEvidenceRecordMeta(
    String status,
    String owner,
    String sender,
  ) {
    return '$status. Erfasst von $owner. Quelle: Nachricht von $sender.';
  }

  @override
  String chatDecisionEvidenceSourceLabel(String sender) {
    return 'Quelle: Nachricht von $sender';
  }

  @override
  String chatDecisionEvidenceCapturedMessage(String kind) {
    return 'Als $kind erfasst. Quelle mit dieser Nachricht verknüpft.';
  }

  @override
  String chatDecisionEvidenceMoreRecords(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count weitere Einträge',
      one: '1 weiterer Eintrag',
    );
    return '$_temp0';
  }

  @override
  String get chatDecisionEvidenceCaptureDecisionAction =>
      'Als Entscheidung erfassen';

  @override
  String get chatDecisionEvidenceCaptureRiskAction => 'Als Risiko erfassen';

  @override
  String get chatDecisionEvidenceCaptureQuestionAction =>
      'Als offene Frage erfassen';

  @override
  String get chatDecisionEvidenceCaptureEvidenceAction =>
      'Als Evidenz erfassen';

  @override
  String get chatWeaverScoutPanelTitle => 'Weaver-Scout';

  @override
  String get chatWeaverScoutPanelDescription =>
      'Frage nur erlaubte Quellen nach Kanal-Kontext. Sprint-4-Weaver ist nur lesend und vorschlagsbasiert; er kann Raumdaten nicht still verändern.';

  @override
  String get chatWeaverScoutReadOnlyStatus => 'Nur lesend';

  @override
  String get chatWeaverScoutProposalOnlyStatus => 'Nur Vorschläge';

  @override
  String get chatWeaverScoutReceiptStatus => 'Beleg erforderlich';

  @override
  String get chatWeaverScoutCapabilitiesTitle => 'Was Weaver darf';

  @override
  String get chatWeaverScoutSourcesTitle => 'Erlaubte Quellen';

  @override
  String get chatWeaverScoutApprovalReceiptsRequired =>
      'Jeder künftige Schreibzugriff oder jede Teamraum-Änderung muss einen Freigabebeleg mit Akteur, angefragter Aktion, freigegebener Aktion, Ziel, Zeitstempel und Ergebniskategorie erzeugen.';

  @override
  String get chatWeaverScoutSummarizeCapability =>
      'Erlaubten Kontext zusammenfassen';

  @override
  String get chatWeaverScoutCiteSourcesCapability => 'Quellen zitieren';

  @override
  String get chatWeaverScoutProposeOnlyCapability => 'Nur Entwürfe vorschlagen';

  @override
  String get chatWeaverScoutApprovalReceiptCapability =>
      'Freigabebelege verlangen';

  @override
  String get filesEmptyMessage => 'Noch keine Dateien';

  @override
  String get filesEmptyGuidance =>
      'Lade eine Datei hoch oder erstelle einen Ordner, wenn du Arbeitsbereichsdateien hinzufügen möchtest.';

  @override
  String get filesDisconnectedTitle => 'Dateien sind nicht verbunden';

  @override
  String get filesSetupNeededTitle => 'Dateien müssen eingerichtet werden';

  @override
  String get filesSessionExpiredTitle => 'Dateien müssen neu verbunden werden';

  @override
  String get filesLoadErrorTitle => 'Dateien konnten nicht geladen werden';

  @override
  String get filesErrorGuidance =>
      'Versuche es erneut. Wenn das weiterhin passiert, prüfe den Dateien-Status in Einrichtung oder Diagnose.';

  @override
  String get calendarEmptyMessage => 'Noch keine Termine';

  @override
  String get deckEmptyMessage => 'Noch keine Boards verfügbar';

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
      'Standardwerte für Matrix, Nextcloud und die Backend-API werden aus dem Issuer-Host abgeleitet. Ändere sie, wenn deine Dienste anderswo liegen.';

  @override
  String get serverConfigurationMatrixLabel => 'Matrix-Homeserver-URL';

  @override
  String get serverConfigurationNextcloudLabel => 'Nextcloud-Basis-URL';

  @override
  String get serverConfigurationBackendApiLabel => 'Backend-API-Basis-URL';

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

  @override
  String get profileSectionTitle => 'Weave-Profil';

  @override
  String get profileSectionDescription =>
      'Dieses Profil kommt aus der Weave-Backend-Identitätsfassade und wird von Produktmodulen gemeinsam genutzt.';

  @override
  String get profileLoadFailure =>
      'Das Weave-Profil konnte gerade nicht geladen werden.';

  @override
  String get profileSignedOutMessage =>
      'Melde dich an, um dein Weave-Profil zu sehen.';

  @override
  String get profileDisplayNameLabel => 'Anzeigename';

  @override
  String get profileUsernameLabel => 'Benutzername';

  @override
  String get profileEmailLabel => 'E-Mail';

  @override
  String get profileEmailVerifiedLabel => 'E-Mail verifiziert';

  @override
  String get profileEmailVerifiedYes => 'Ja';

  @override
  String get profileEmailVerifiedNo => 'Nein';

  @override
  String get profileLocaleLabel => 'Sprache';

  @override
  String get profileTimezoneLabel => 'Zeitzone';

  @override
  String get profileRolesLabel => 'Rollen';

  @override
  String get profileGroupsLabel => 'Gruppen';

  @override
  String get profileEditingBlockedMessage =>
      'Profilbearbeitung ist in der App vorbereitet, aber Speichern ist blockiert, bis das Backend PATCH /api/profile bereitstellt.';

  @override
  String get profileEditSectionTitle => 'Profil bearbeiten';

  @override
  String get profileEditSectionDescription =>
      'Speichere Änderungen über die Weave-Backend-Profilfassade, damit alle Produktmodule dasselbe Profil verwenden.';

  @override
  String get profileDisplayNameHelper =>
      'Wird Workspace-Mitgliedern in Weave-Oberflächen angezeigt.';

  @override
  String get profileLocaleHelper => 'Nutze einen Sprachcode wie en oder de.';

  @override
  String get profileTimezoneHelper =>
      'Nutze eine IANA-Zeitzone wie Europe/Berlin.';

  @override
  String get profileEditRequiredFieldError => 'Dieses Feld ist erforderlich.';

  @override
  String get profileEditSaveButton => 'Profil speichern';

  @override
  String get profileEditSavingButton => 'Profil wird gespeichert…';

  @override
  String get profileEditSavedMessage => 'Profil gespeichert.';

  @override
  String get settingsWorkspaceCalendarLabel => 'Kalender';

  @override
  String get settingsWorkspaceBoardsLabel => 'Boards';

  @override
  String get calendarWorkspaceScopeTitle => 'Arbeitsbereichskalender';

  @override
  String get calendarWorkspaceScopeDescription =>
      'Dieser erste Kalender-Schnitt nutzt den gemeinsamen Weave-Arbeitsbereichskalender. Private Nutzerkalender bleiben deaktiviert, bis das Zugriffsmodell umgesetzt ist.';

  @override
  String calendarGenericScopeDescription(String scopeLabel) {
    return 'Termine werden aus $scopeLabel angezeigt.';
  }

  @override
  String get calendarClientSetupTitle => 'Kalender in anderen Apps nutzen';

  @override
  String get calendarClientSetupDescription =>
      'Weave kann nativen Clients geheime-freie Einrichtungsdaten übergeben. Die Weave-Kalenderoberfläche bleibt der Produktweg.';

  @override
  String get calendarClientSetupIconSemantic => 'Externe Kalendereinrichtung';

  @override
  String get calendarClientSetupLoading =>
      'Einrichtungsoptionen werden geladen…';

  @override
  String get calendarClientSetupUnavailable =>
      'Kalender-Einrichtungsoptionen sind gerade nicht verfügbar.';

  @override
  String get calendarCapabilityLoading =>
      'Kalender-Verfügbarkeit wird geprüft…';

  @override
  String get calendarCapabilityError =>
      'Die Kalender-Verfügbarkeit kann gerade nicht geprüft werden.';

  @override
  String get calendarUnavailableTitle => 'Kalender ist nicht verfügbar';

  @override
  String calendarUnavailableDescription(String readiness) {
    return 'Die Backend-Bereitschaft ist $readiness. Terminänderungen bleiben deaktiviert, bis das Weave-Backend den Kalender als bereit meldet.';
  }

  @override
  String get calendarClientSetupUsernameLabel => 'Benutzername';

  @override
  String get calendarClientSetupDiscoveryUrlLabel => 'CalDAV-Discovery-URL';

  @override
  String get calendarClientSetupPrincipalUrlLabel => 'Principal-URL';

  @override
  String get calendarClientSetupCredentialPolicyTitle =>
      'Zugangsdaten-Sicherheit';

  @override
  String get calendarClientSetupAccessModelTitle => 'Zugriffsmodell';

  @override
  String get calendarClientSetupPrivateCalendarsAvailable =>
      'Private Benutzerkalender verfügbar';

  @override
  String get calendarClientSetupPrivateCalendarsBlocked =>
      'Private Benutzerkalender blockiert';

  @override
  String calendarClientSetupExternalCredentialModel(String model) {
    return 'Externes Zugangsdatenmodell: $model';
  }

  @override
  String get calendarClientSetupCredentialReadinessTitle =>
      'Bereitschaft der Zugangsdaten';

  @override
  String calendarClientSetupCredentialReadinessStatus(String status) {
    return 'Status: $status';
  }

  @override
  String get calendarClientSetupAppleProfileBlocked =>
      'Apple-Profile bleiben deaktiviert, bis Profile signiert sind und sichere Zugangsdaten existieren.';

  @override
  String get calendarClientSetupSubscriptionsBlocked =>
      'Webcal/ICS-Abos bleiben deaktiviert, bis widerrufbare Read-only-Tokens existieren.';

  @override
  String get calendarClientSetupCredentialsSafe =>
      'Backend-Actor-Zugangsdaten werden nicht in Client-Einrichtungsdaten offengelegt.';

  @override
  String get calendarClientSetupCredentialsUnsafe =>
      'Die Einrichtung ist blockiert, weil Backend-Actor-Zugangsdaten offengelegt würden.';

  @override
  String get calendarClientSetupPlatformsTitle => 'Plattform-Einrichtung';

  @override
  String get calendarClientSetupAvailableStatus => 'verfügbar';

  @override
  String get calendarClientSetupPlannedStatus => 'geplant';

  @override
  String get calendarClientSetupPlannedFallback =>
      'Dieser Einrichtungsweg ist per Feature-Flag geschützt, bis Widerruf, Provisionierung und Plattformprofil-Tests abgeschlossen sind.';

  @override
  String calendarClientSetupOptionTitle(
    String platform,
    String method,
    String status,
  ) {
    return '$platform über $method: $status';
  }

  @override
  String calendarClientSetupCopyTooltip(String label) {
    return '$label kopieren';
  }

  @override
  String get calendarClientSetupCopied => 'Kalender-Einrichtungswert kopiert.';

  @override
  String get calendarCreateButton => 'Termin erstellen';

  @override
  String get calendarCreateDialogTitle => 'Kalendertermin erstellen';

  @override
  String get calendarEditDialogTitle => 'Kalendertermin bearbeiten';

  @override
  String get calendarTitleFieldLabel => 'Titel';

  @override
  String get calendarDescriptionFieldLabel => 'Beschreibung';

  @override
  String get calendarLocationFieldLabel => 'Ort';

  @override
  String get calendarTitleRequired => 'Gib einen Termintitel ein.';

  @override
  String get calendarCancelButton => 'Abbrechen';

  @override
  String get calendarSaveButton => 'Termin speichern';

  @override
  String calendarDeleteEventTooltip(String title) {
    return '$title löschen';
  }

  @override
  String calendarEditEventTooltip(String title) {
    return '$title bearbeiten';
  }

  @override
  String calendarViewEventTooltip(String title) {
    return '$title anzeigen';
  }

  @override
  String calendarEventSemantic(String title, String startsAt, String endsAt) {
    return '$title, beginnt $startsAt, endet $endsAt';
  }

  @override
  String get calendarDetailsDialogTitle => 'Kalendertermin-Details';

  @override
  String get calendarDetailsLoading => 'Termindetails werden geladen…';

  @override
  String get calendarDetailsError =>
      'Termindetails sind gerade nicht verfügbar.';

  @override
  String get calendarDetailsTimeLabel => 'Zeit';

  @override
  String get calendarDetailsScopeLabel => 'Kalenderbereich';

  @override
  String get calendarDetailsContextLabel => 'Kontext';

  @override
  String get calendarDetailsMeetingThreadLabel => 'Meeting-Thread';

  @override
  String get calendarDetailsMeetingThreadPending =>
      'Sichere Kontextmetadaten sind verfügbar; die Chat-Thread-Verknüpfung ist noch nicht konfiguriert.';

  @override
  String get calendarDetailsAttendeesLabel => 'Teilnehmende';

  @override
  String get calendarDetailsProviderLabel => 'Provider-Referenz';

  @override
  String get calendarDetailsProviderPathHidden =>
      'roher Provider-Pfad verborgen';

  @override
  String get calendarDetailsUpdatedLabel => 'Aktualisiert';

  @override
  String get calendarDetailsLocationLabel => 'Ort';

  @override
  String get calendarDetailsDescriptionLabel => 'Beschreibung';

  @override
  String get calendarCloseButton => 'Schließen';

  @override
  String get calendarCreateSuccess => 'Kalendertermin erstellt.';

  @override
  String get calendarUpdateSuccess => 'Kalendertermin aktualisiert.';

  @override
  String get calendarDeleteSuccess => 'Kalendertermin gelöscht.';

  @override
  String get calendarOperationFailure =>
      'Der Kalender konnte diese Änderung gerade nicht speichern.';

  @override
  String get boardsWorkspaceScreenTitle => 'Boards-Workspace';

  @override
  String get boardsWorkspaceIconSemantic => 'Boards-Workspace';

  @override
  String get boardsWorkspaceBoundaryTitle =>
      'Dogfood-Boards-/Aufgaben-Workspace';

  @override
  String get boardsWorkspaceBoundaryDescription =>
      'Diese Dogfood-Produktion zeigt das Weave-eigene Board-Modell, barrierefreie Alternativen zum Verschieben von Aufgaben und backend-eigene Provider-Schnittstellen. Nutzeränderungen an Aufgaben brauchen das authentifizierte Workspace-Backend mit Audit und Context/Space-Autorisierung.';

  @override
  String get boardsWorkspaceBoundarySemantic =>
      'Dogfood-Boards-/Aufgaben-Workspace. Anbieterneutrales Weave-Modell mit Tastatur- und Screenreader-Alternativen; Nutzeränderungen an Aufgaben brauchen das auditierte Workspace-Backend.';

  @override
  String get boardsWorkspaceActiveDogfoodChip => 'Dogfood-Produktion';

  @override
  String get boardsWorkspaceProviderNeutralChip => 'Anbieterneutrales Modell';

  @override
  String get boardsWorkspaceKeyboardChip => 'Kein Ziehen nötig';

  @override
  String boardsWorkspaceColumnCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Spalten',
      one: '1 Spalte',
      zero: 'Keine Spalten',
    );
    return '$_temp0';
  }

  @override
  String boardsWorkspaceTaskCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Aufgaben',
      one: '1 Aufgabe',
      zero: 'Keine Aufgaben',
    );
    return '$_temp0';
  }

  @override
  String get boardsWorkspaceNonDragMovement =>
      'Verschieben per Menü statt nur Drag-and-drop';

  @override
  String boardsWorkspaceBoardSemantic(
    String boardName,
    int columnCount,
    int taskCount,
  ) {
    String _temp0 = intl.Intl.pluralLogic(
      columnCount,
      locale: localeName,
      other: '$columnCount Spalten',
      one: '1 Spalte',
    );
    String _temp1 = intl.Intl.pluralLogic(
      taskCount,
      locale: localeName,
      other: '$taskCount Aufgaben',
      one: '1 Aufgabe',
    );
    return 'Board $boardName, $_temp0, $_temp1.';
  }

  @override
  String boardsWorkspaceColumnSemantic(
    String columnName,
    String status,
    int taskCount,
  ) {
    String _temp0 = intl.Intl.pluralLogic(
      taskCount,
      locale: localeName,
      other: '$taskCount Aufgaben',
      one: '1 Aufgabe',
    );
    return 'Spalte $columnName, Status $status, $_temp0.';
  }

  @override
  String boardsWorkspaceColumnTaskSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Aufgaben in dieser Spalte',
      one: '1 Aufgabe in dieser Spalte',
      zero: 'Keine Aufgaben in dieser Spalte',
    );
    return '$_temp0';
  }

  @override
  String boardsWorkspaceColumnWipSummary(int count, int limit) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Aufgaben',
      one: '1 Aufgabe',
      zero: 'Keine Aufgaben',
    );
    return '$_temp0 · WIP-Limit $limit';
  }

  @override
  String boardsWorkspaceTaskSemantic(
    String taskTitle,
    String columnName,
    String status,
    String assignee,
    String due,
    String priority,
  ) {
    return 'Aufgabe $taskTitle. Spalte $columnName. Status $status. Zuständig $assignee. Fällig $due. Priorität $priority.';
  }

  @override
  String boardsWorkspaceTaskActionsTooltip(String taskTitle) {
    return 'Aufgabenaktionen für $taskTitle';
  }

  @override
  String get boardsWorkspaceMoveTaskAction =>
      'In eine andere Spalte verschieben';

  @override
  String get boardsWorkspaceMarkDoneAction => 'Als erledigt markieren';

  @override
  String get boardsWorkspaceBlockTaskAction => 'Als blockiert markieren';

  @override
  String get boardsWorkspaceActionBackendRequired =>
      'Verbinde dich mit dem Workspace-Backend, um Aufgabenänderungen anzuwenden.';

  @override
  String get boardsWorkspaceStatusNotStarted => 'Nicht begonnen';

  @override
  String get boardsWorkspaceStatusInProgress => 'In Arbeit';

  @override
  String get boardsWorkspaceStatusBlocked => 'Blockiert';

  @override
  String get boardsWorkspaceStatusDone => 'Erledigt';

  @override
  String boardsWorkspaceStatusSemantic(String status) {
    return 'Status: $status';
  }

  @override
  String get boardsWorkspaceBackendFedChip => 'Aus Backend-Fassade';

  @override
  String get boardsWorkspaceProviderBlockedChip =>
      'Provider-Laufzeit blockiert';

  @override
  String get boardsWorkspaceStaticFixtureChip => 'Statische Workspace-Fixture';

  @override
  String boardsWorkspaceProviderCapabilitySummary(String provider) {
    return 'Provider: $provider';
  }

  @override
  String get boardsWorkspaceCapabilityNonDragReady =>
      'Backend-Aktionen ohne Ziehen bereit';

  @override
  String get boardsWorkspaceCapabilityNonDragBlocked =>
      'Backend-Aktionen ohne Ziehen blockiert';

  @override
  String get boardsWorkspaceProviderInMemory => 'In-Memory-Backend-Fassade';

  @override
  String get boardsWorkspaceProviderVikunja => 'Vikunja-Adapter';

  @override
  String get boardsWorkspaceProviderOpenProject => 'OpenProject-Adapter';

  @override
  String get boardsWorkspaceProviderNextcloudDeck => 'Nextcloud-Deck-Adapter';

  @override
  String get boardsWorkspaceProviderNone => 'kein Backend-Provider';

  @override
  String get boardsWorkspaceProviderUnavailable => 'Backend nicht verfügbar';

  @override
  String get boardsWorkspaceProviderUnknown => 'unbekannter Provider';

  @override
  String get boardsWorkspaceActionMoved =>
      'Aufgabe über die Backend-Fassade verschoben.';

  @override
  String get boardsWorkspaceActionCompleted =>
      'Aufgabe über die Backend-Fassade als erledigt markiert.';

  @override
  String get boardsWorkspaceActionBlocked =>
      'Aufgabe über die Backend-Fassade als blockiert markiert.';

  @override
  String get boardsWorkspaceActionFailed =>
      'Die Backend-Fassade konnte diese Boards-Workspace-Aktion nicht speichern.';

  @override
  String get boardsWorkspaceActionNoNextColumn =>
      'Diese Aufgabe ist bereits in der letzten Workspace-Spalte.';

  @override
  String get settingsAdminSetupTitle => 'Owner- und Admin-Einrichtung';

  @override
  String get settingsAdminSetupDescription =>
      'Workspace-Owner und Admins verwalten hier OIDC, Realm, Organisation und Dienstendpunkte. Mitglieder und Gäste sehen nur Anmeldung und Produkteinstellungen.';

  @override
  String get settingsAdminManualTitle =>
      'Eingebettetes Admin-/Operator-Handbuch';

  @override
  String get settingsAdminManualDescription =>
      'Die Admin Console bettet das MkDocs-Admin-/Operator-Handbuch neben Bereitschaft und Einrichtung ein, damit Provider-Mapping, Backup/Wiederherstellung, Migration und Support-Bundle-Hilfe in der Produktoberfläche bleiben.';

  @override
  String get settingsAdminPermissionTitle => 'Admin-Steuerung freigeschaltet';

  @override
  String settingsAdminPermissionDescription(String roles) {
    return 'Sichtbar, weil deine Weave-Rollen sind: $roles. Backend-APIs bleiben die Autorität für jeden Schreibvorgang.';
  }

  @override
  String settingsAdminPermissionSemantic(String roles) {
    return 'Admin-Steuerung freigeschaltet. Sichtbar, weil deine Weave-Rollen sind: $roles. Backend-APIs bleiben die Autorität für jeden Schreibvorgang.';
  }

  @override
  String get settingsAdminBoundaryTitle =>
      'Workspace-Einrichtung ist nur für Admins';

  @override
  String get settingsAdminBoundaryDescription =>
      'Workspace-Einrichtung übernehmen Workspace-Owner oder Admins. Normale Nutzer können Weave ohne Setup- oder Infrastrukturdetails weiterverwenden.';

  @override
  String get settingsAdminPermissionLoading =>
      'Admin-Berechtigungen werden geprüft…';

  @override
  String get chatContextCardTitle => 'Kontext für diesen Workspace';

  @override
  String get chatContextCardDescription =>
      'Weave kann fokussierten Kontext aus Kanälen, Entscheidungen und gemeinsamer Arbeit vorbereiten, wenn du Hilfe anforderst. Es zeigt kein Datenbankdiagramm und liest nicht ständig alles mit.';

  @override
  String get chatContextCardPolicy =>
      'Agenten nutzen begrenzten Kontext nur bei Bedarf, zeigen den verwendeten Kontext und bleiben innerhalb der Admin-Grenzen.';

  @override
  String get chatContextChannelHintTitle => 'Kanalkontext';

  @override
  String get chatContextChannelHintDescription =>
      'Aktuelle Raumsignale können zu einer kleinen Kontextkarte für die Aufgabe werden.';

  @override
  String get chatContextEvidenceHintTitle => 'Entscheidungen und Evidenz';

  @override
  String get chatContextEvidenceHintDescription =>
      'Entscheidungsnotizen und Links können zitiert werden, ohne Graph-Interna offenzulegen.';

  @override
  String get chatContextAgentHintTitle => 'Agent-Kontextpakete';

  @override
  String get chatContextAgentHintDescription =>
      'Assistenten erhalten nur das begrenzte Paket für Anfrage, Erwähnung oder Zeitplan.';

  @override
  String get firstRunAdminSetupTitle =>
      'Owner-/Admin-Verantwortung bei der Einrichtung';

  @override
  String get firstRunAdminSetupDescription =>
      'Deine Rolle darf die Workspace-Einrichtung verwalten. OIDC-, Realm-, Organisations-, Einladungs- und Dienstendpunkt-Änderungen gehören hier oder in die Einstellungen; normale Nutzer sollen nur eine Weave-Anmeldung brauchen.';

  @override
  String get agentCapabilityPolicyTitle =>
      'Governance für KI-Agenten-Funktionen';

  @override
  String get agentCapabilityPolicyAdminDescription =>
      'Owner und Admins entscheiden, welche Agentenpakete und Verbindungen genutzt werden dürfen. Diese Funktion bleibt aus, bis Berechtigungen, Einwilligung und Audit-Kontrollen verbunden sind.';

  @override
  String get agentCapabilityPolicyUserDescription =>
      'KI-Agentenchats sind für diesen Workspace noch nicht aktiviert. Du kannst Weave normal weiter nutzen; zuerst müssen Owner oder Admins diese Funktion freigeben.';

  @override
  String get agentCapabilityPolicyFailClosedNotice =>
      'Agenten-Funktionen sind blockiert, bis Weave deine Rolle und die Workspace-Richtlinie bestätigen kann.';

  @override
  String get agentCapabilityPolicyManageDisabledButton =>
      'Verwaltung nicht verfügbar, bis die Admin-Einrichtung abgeschlossen ist';

  @override
  String get agentCapabilityPolicyAskAdminHint =>
      'Braucht dein Team einen Agenten? Bitte Owner oder Admins, Agenten-Funktionen zu prüfen, sobald sie verfügbar sind.';

  @override
  String get agentCapabilityPolicyAdminStateHint =>
      'Aktueller Zustand: durch Policy deaktiviert. Owner-/Admin-Prüfung ist erforderlich, bevor Nutzer einen Agenten starten können.';

  @override
  String get agentCapabilityPersonalAssistantTitle => 'Persönlicher Assistent';

  @override
  String get agentCapabilityPersonalAssistantDescription =>
      'Nutzt nur Kontext, den du für eine Anfrage auswählst, nachdem dein Workspace die Funktion aktiviert hat.';

  @override
  String get agentCapabilityChannelAgentTitle => 'Kanal-Agent';

  @override
  String get agentCapabilityChannelAgentDescription =>
      'Erfordert, dass Owner oder Admins auswählen, welche Kanäle, Dateien, Kalendertermine oder Boards der Agent nutzen darf.';

  @override
  String get agentCapabilityAvailabilityPreviewOnly =>
      'Admin-Einrichtung nötig';

  @override
  String get agentCapabilityAvailabilityAdminSetupRequired =>
      'Admin-Einrichtung nötig';

  @override
  String get agentCapabilityAvailabilityBlocked => 'Blockiert';

  @override
  String get agentCapabilityPolicyErrorTitle =>
      'Agenten-Funktionsrichtlinie ist nicht verfügbar.';

  @override
  String get agentCapabilityPolicyLoading =>
      'Agenten-Funktionsrichtlinie wird geprüft…';

  @override
  String get workflowPreviewTitle => 'Aktive Workflows';

  @override
  String get workflowPreviewDescription =>
      'Eine lineare Ansicht der aktuellen Schritte, Verantwortlichen, Blockaden und Evidenz. Diagramme können später dazukommen; diese Ansicht muss zuerst mit Tastatur und Screenreadern funktionieren.';

  @override
  String get workflowPreviewLinearViewChip => 'Lineare Ansicht zuerst';

  @override
  String get workflowPreviewExplicitContextChip => 'Nur expliziter Kontext';

  @override
  String get workflowPreviewGovernedActionsChip => 'Gesteuerte Aktionen';

  @override
  String get workflowPreviewNoBackgroundReading =>
      'Workflow-Kontext wird bewusst angehängt; Weave liest Räume nicht dauerhaft im Hintergrund mit.';

  @override
  String workflowPreviewSemanticSummary(
    int workflowCount,
    int activeStepCount,
    int blockerCount,
  ) {
    String _temp0 = intl.Intl.pluralLogic(
      workflowCount,
      locale: localeName,
      other: '$workflowCount aktive Workflows',
      one: '1 aktiver Workflow',
      zero: 'Keine aktiven Workflows',
    );
    String _temp1 = intl.Intl.pluralLogic(
      activeStepCount,
      locale: localeName,
      other: '$activeStepCount aktive Schritte',
      one: '1 aktiver Schritt',
      zero: 'Keine aktiven Schritte',
    );
    String _temp2 = intl.Intl.pluralLogic(
      blockerCount,
      locale: localeName,
      other: '$blockerCount Blockaden',
      one: '1 Blockade',
      zero: 'Keine Blockaden',
    );
    return '$_temp0. $_temp1. $_temp2.';
  }

  @override
  String workflowPreviewRunSemantic(
    String title,
    String context,
    int stepCount,
    int blockerCount,
  ) {
    String _temp0 = intl.Intl.pluralLogic(
      stepCount,
      locale: localeName,
      other: '$stepCount Schritte',
      one: '1 Schritt',
    );
    String _temp1 = intl.Intl.pluralLogic(
      blockerCount,
      locale: localeName,
      other: '$blockerCount Blockaden',
      one: '1 Blockade',
      zero: 'Keine Blockaden',
    );
    return 'Workflow $title. Kontext $context. $_temp0. $_temp1.';
  }

  @override
  String workflowPreviewContextLabel(String context) {
    return 'Kontext: $context';
  }

  @override
  String workflowPreviewNextAction(String stepTitle, String action) {
    return 'Nächste Aktion: $stepTitle — $action';
  }

  @override
  String workflowPreviewStepSemantic(
    String title,
    String kind,
    String status,
    String owner,
    String due,
    String nextAction,
    String blockers,
    String evidence,
  ) {
    return 'Schritt $title. Typ $kind. Status $status. Verantwortlich $owner. Fällig $due. Nächste Aktion $nextAction. Blockiert: $blockers. Evidenz: $evidence.';
  }

  @override
  String get workflowPreviewKindStep => 'Schritt';

  @override
  String get workflowPreviewKindGate => 'Gate';

  @override
  String get workflowPreviewKindApproval => 'Freigabe';

  @override
  String get workflowPreviewStatusReady => 'Bereit';

  @override
  String get workflowPreviewStatusInProgress => 'In Arbeit';

  @override
  String get workflowPreviewStatusBlocked => 'Blockiert';

  @override
  String get workflowPreviewStatusWaiting => 'Wartet auf Freigabe';

  @override
  String get workflowPreviewStatusDone => 'Erledigt';

  @override
  String workflowPreviewOwner(String owner) {
    return 'Verantwortlich: $owner';
  }

  @override
  String workflowPreviewDue(String due) {
    return 'Fällig: $due';
  }

  @override
  String workflowPreviewStepNextAction(String action) {
    return 'Nächste Aktion: $action';
  }

  @override
  String get workflowPreviewNoBlockers => 'Keine Blockaden';

  @override
  String workflowPreviewBlockers(String blockers) {
    return 'Blockiert: $blockers';
  }

  @override
  String workflowPreviewEvidence(String evidence) {
    return 'Evidenz: $evidence';
  }

  @override
  String get workflowPreviewApprovalRequired =>
      'Owner-Freigabe ist nötig, bevor diese Aktion ausgeführt werden kann.';

  @override
  String get workflowPreviewAgentDryRunOnly =>
      'Agentenhilfe bleibt ein Trockenlauf, bis Admin-Richtlinie und Freigabe verbunden sind.';

  @override
  String get workflowPreviewOpenStepButton => 'Schritt öffnen';

  @override
  String get workflowPreviewReviewEvidenceButton => 'Evidenz prüfen';

  @override
  String get channelWorkspaceStatusAdminSetupRequired =>
      'Admin-Einrichtung nötig';

  @override
  String get channelWorkspaceStatusDisabledByPolicy =>
      'Durch Policy deaktiviert';

  @override
  String get channelWorkspaceStatusDegraded => 'Eingeschränkt';

  @override
  String get agentCapabilityAvailabilityDisabledByPolicy =>
      'Durch Policy deaktiviert';
}
