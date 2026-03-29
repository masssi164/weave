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
  String get loadingLabel => 'Wird geladen…';

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
  String get chatScreenTitle => 'Chat';

  @override
  String get filesScreenTitle => 'Dateien';

  @override
  String get calendarScreenTitle => 'Kalender';

  @override
  String get deckScreenTitle => 'Deck';

  @override
  String get chatEmptyMessage => 'Noch keine Unterhaltungen';

  @override
  String get filesEmptyMessage => 'Noch keine Dateien';

  @override
  String get calendarEmptyMessage => 'Noch keine Termine';

  @override
  String get deckEmptyMessage => 'Noch keine Boards';

  @override
  String get deviceLanguageLabel => 'Gerätesprache';
}
