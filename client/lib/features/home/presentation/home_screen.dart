import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/weave_logo.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/features/shell/domain/entities/shell_module.dart';
import 'package:weave/features/shell/presentation/providers/shell_module_preferences_provider.dart';
import 'package:weave/features/shell/presentation/shell_recent_activity.dart';
import 'package:weave/features/shell/presentation/shell_workspace_status.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final modulePreferences = ref.watch(shellModulePreferencesProvider);
    final modules =
        modulePreferences.asData?.value.orderedModules ?? ShellModule.values;
    final visibleModules = modules
        .where(
          (module) => modulePreferences.asData?.value.isVisible(module) ?? true,
        )
        .toList(growable: false);

    return Scaffold(
      appBar: AppBar(
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            WeaveLogo(
              semanticLabel: l10n.semanticWeaveLogo,
              width: 40,
              framed: false,
              excludeFromSemantics: true,
            ),
            const SizedBox(width: 12),
            Flexible(child: Text(l10n.chatOverviewTitle)),
          ],
        ),
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.only(bottom: 24),
          children: [
            _HomeHero(copy: _HomeCopy.of(context)),
            if (visibleModules.contains(ShellModule.recentActivity))
              _HomeSection(
                title: _HomeCopy.of(context).todayTitle,
                description: _HomeCopy.of(context).todayDescription,
                child: const _HomeTodaySummary(),
              ),
            if (visibleModules.contains(ShellModule.recentActivity))
              _HomeSection(
                title: _HomeCopy.of(context).continueTitle,
                description: _HomeCopy.of(context).continueDescription,
                child: const ShellRecentActivity(),
              ),
            if (visibleModules.contains(ShellModule.workspaceStatus))
              _HomeSection(
                title: _HomeCopy.of(context).workspaceTitle,
                description: _HomeCopy.of(context).workspaceDescription,
                child: const ShellWorkspaceStatus(),
              ),
            if (visibleModules.isEmpty)
              _HomeEmptyState(copy: _HomeCopy.of(context)),
          ],
        ),
      ),
    );
  }
}

class _HomeHero extends StatelessWidget {
  const _HomeHero({required this.copy});

  final _HomeCopy copy;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Semantics(
        header: true,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(copy.productCenterTitle, style: theme.textTheme.headlineSmall),
            const SizedBox(height: 6),
            Text(copy.productCenterDescription),
          ],
        ),
      ),
    );
  }
}

class _HomeSection extends StatelessWidget {
  const _HomeSection({
    required this.title,
    required this.description,
    required this.child,
  });

  final String title;
  final String description;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Semantics(
      container: true,
      explicitChildNodes: true,
      child: Padding(
        padding: const EdgeInsets.only(top: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Semantics(
                    header: true,
                    child: Text(title, style: theme.textTheme.titleLarge),
                  ),
                  const SizedBox(height: 4),
                  Text(description, style: theme.textTheme.bodyMedium),
                ],
              ),
            ),
            const SizedBox(height: 8),
            child,
          ],
        ),
      ),
    );
  }
}

class _HomeTodaySummary extends ConsumerWidget {
  const _HomeTodaySummary();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final copy = _HomeCopy.of(context);
    final calendarState = ref.watch(calendarProvider);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Card(
        margin: EdgeInsets.zero,
        elevation: 0,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: switch (calendarState) {
            AsyncLoading() => _HomeStatusRow(
              icon: Icons.calendar_today_outlined,
              title: copy.calendarLoadingTitle,
              body: copy.calendarLoadingBody,
            ),
            AsyncError() => _HomeStatusRow(
              icon: Icons.calendar_today_outlined,
              title: copy.calendarUnavailableTitle,
              body: copy.calendarUnavailableBody,
              actionLabel: copy.openCalendarAction,
              onAction: () => context.go(AppRoutes.calendar),
            ),
            AsyncData(:final value) => _HomeCalendarData(events: value.events),
          },
        ),
      ),
    );
  }
}

class _HomeCalendarData extends StatelessWidget {
  const _HomeCalendarData({required this.events});

  final List<CalendarEvent> events;

  @override
  Widget build(BuildContext context) {
    final copy = _HomeCopy.of(context);
    final upcoming = _upcomingEvents(events);

    if (upcoming.isEmpty) {
      return _HomeStatusRow(
        icon: Icons.event_available_outlined,
        title: copy.calendarEmptyTitle,
        body: copy.calendarEmptyBody,
        actionLabel: copy.openCalendarAction,
        onAction: () => context.go(AppRoutes.calendar),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final event in upcoming)
          _HomeEventTile(
            event: event,
            onTap: () => context.go(AppRoutes.calendar),
          ),
        const SizedBox(height: 8),
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton.icon(
            onPressed: () => context.go(AppRoutes.calendar),
            icon: const Icon(Icons.calendar_today_outlined),
            label: Text(copy.openCalendarAction),
          ),
        ),
      ],
    );
  }

  List<CalendarEvent> _upcomingEvents(List<CalendarEvent> events) {
    final now = DateTime.now();
    final startOfToday = DateTime(now.year, now.month, now.day);
    final horizon = startOfToday.add(const Duration(days: 8));
    final upcoming =
        events
            .where(
              (event) =>
                  !event.endTime.isBefore(startOfToday) &&
                  event.startTime.isBefore(horizon),
            )
            .toList()
          ..sort((left, right) => left.startTime.compareTo(right.startTime));

    return upcoming.take(3).toList(growable: false);
  }
}

class _HomeEventTile extends StatelessWidget {
  const _HomeEventTile({required this.event, required this.onTap});

  final CalendarEvent event;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final timeLabel = _formatEventTime(context, event);
    final location = event.location;
    final semanticLabel = location == null || location.isEmpty
        ? '${event.title}. $timeLabel.'
        : '${event.title}. $timeLabel. $location.';

    return Semantics(
      button: true,
      label: semanticLabel,
      onTap: onTap,
      child: ExcludeSemantics(
        child: ListTile(
          contentPadding: EdgeInsets.zero,
          leading: const Icon(Icons.event_note_outlined),
          title: Text(event.title),
          subtitle: Text(
            location == null || location.isEmpty
                ? timeLabel
                : '$timeLabel\n$location',
          ),
          titleTextStyle: theme.textTheme.titleMedium,
          onTap: onTap,
        ),
      ),
    );
  }
}

class _HomeStatusRow extends StatelessWidget {
  const _HomeStatusRow({
    required this.icon,
    required this.title,
    required this.body,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String title;
  final String body;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Semantics(
      liveRegion: true,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: theme.colorScheme.primary),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: theme.textTheme.titleMedium),
                const SizedBox(height: 4),
                Text(body),
                if (actionLabel != null && onAction != null) ...[
                  const SizedBox(height: 8),
                  TextButton(onPressed: onAction, child: Text(actionLabel!)),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _HomeEmptyState extends StatelessWidget {
  const _HomeEmptyState({required this.copy});

  final _HomeCopy copy;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 0),
      child: Card(
        elevation: 0,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                Icons.dashboard_customize_outlined,
                color: theme.colorScheme.primary,
              ),
              const SizedBox(height: 12),
              Text(copy.emptyTitle, style: theme.textTheme.titleMedium),
              const SizedBox(height: 4),
              Text(copy.emptyBody),
              const SizedBox(height: 8),
              TextButton.icon(
                onPressed: () => context.go(AppRoutes.settings),
                icon: const Icon(Icons.settings_outlined),
                label: Text(copy.openSettingsAction),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

String _formatEventTime(BuildContext context, CalendarEvent event) {
  final material = MaterialLocalizations.of(context);
  final date = material.formatMediumDate(event.startTime.toLocal());
  if (event.allDay) {
    return '${_HomeCopy.of(context).allDayLabel}, $date';
  }
  final start = material.formatTimeOfDay(
    TimeOfDay.fromDateTime(event.startTime.toLocal()),
  );
  final end = material.formatTimeOfDay(
    TimeOfDay.fromDateTime(event.endTime.toLocal()),
  );
  return '$date, $start-$end';
}

class _HomeCopy {
  const _HomeCopy({
    required this.productCenterTitle,
    required this.productCenterDescription,
    required this.todayTitle,
    required this.todayDescription,
    required this.continueTitle,
    required this.continueDescription,
    required this.workspaceTitle,
    required this.workspaceDescription,
    required this.calendarLoadingTitle,
    required this.calendarLoadingBody,
    required this.calendarUnavailableTitle,
    required this.calendarUnavailableBody,
    required this.calendarEmptyTitle,
    required this.calendarEmptyBody,
    required this.openCalendarAction,
    required this.openSettingsAction,
    required this.emptyTitle,
    required this.emptyBody,
    required this.allDayLabel,
  });

  factory _HomeCopy.of(BuildContext context) {
    final isGerman = Localizations.localeOf(context).languageCode == 'de';
    return isGerman ? _de : _en;
  }

  final String productCenterTitle;
  final String productCenterDescription;
  final String todayTitle;
  final String todayDescription;
  final String continueTitle;
  final String continueDescription;
  final String workspaceTitle;
  final String workspaceDescription;
  final String calendarLoadingTitle;
  final String calendarLoadingBody;
  final String calendarUnavailableTitle;
  final String calendarUnavailableBody;
  final String calendarEmptyTitle;
  final String calendarEmptyBody;
  final String openCalendarAction;
  final String openSettingsAction;
  final String emptyTitle;
  final String emptyBody;
  final String allDayLabel;

  static const _en = _HomeCopy(
    productCenterTitle: 'Product center',
    productCenterDescription:
        'Start from the work that matters now: today, continue, and workspace readiness.',
    todayTitle: 'Today',
    todayDescription: 'Calendar signals and near-term work for this workspace.',
    continueTitle: 'Continue',
    continueDescription: 'Recent conversations and files you can resume.',
    workspaceTitle: 'Workspace status',
    workspaceDescription:
        'What is ready, degraded, or waiting for setup in member language.',
    calendarLoadingTitle: 'Calendar is loading',
    calendarLoadingBody: 'Weave is checking upcoming workspace events.',
    calendarUnavailableTitle: 'Calendar needs attention',
    calendarUnavailableBody:
        'Upcoming events are unavailable right now. Open Calendar for the current status and next action.',
    calendarEmptyTitle: 'No upcoming events',
    calendarEmptyBody:
        'Your next workspace events will appear here when Calendar has data.',
    openCalendarAction: 'Open Calendar',
    openSettingsAction: 'Open Settings',
    emptyTitle: 'Home modules are hidden',
    emptyBody:
        'Turn workspace status or recent activity back on to make Home useful again.',
    allDayLabel: 'All day',
  );

  static const _de = _HomeCopy(
    productCenterTitle: 'Produktzentrum',
    productCenterDescription:
        'Starte mit der Arbeit, die jetzt zählt: Heute, Weiterarbeiten und Workspace-Status.',
    todayTitle: 'Heute',
    todayDescription:
        'Kalendersignale und kurzfristige Arbeit für diesen Workspace.',
    continueTitle: 'Weiterarbeiten',
    continueDescription:
        'Aktuelle Unterhaltungen und Dateien, die du fortsetzen kannst.',
    workspaceTitle: 'Workspace-Status',
    workspaceDescription:
        'Was bereit, eingeschränkt oder noch einzurichten ist - in verständlicher Sprache.',
    calendarLoadingTitle: 'Kalender wird geladen',
    calendarLoadingBody: 'Weave prüft anstehende Workspace-Termine.',
    calendarUnavailableTitle: 'Kalender braucht Aufmerksamkeit',
    calendarUnavailableBody:
        'Anstehende Termine sind gerade nicht verfügbar. Öffne Kalender für den aktuellen Status und die nächste Aktion.',
    calendarEmptyTitle: 'Keine anstehenden Termine',
    calendarEmptyBody:
        'Deine nächsten Workspace-Termine erscheinen hier, sobald Kalender Daten hat.',
    openCalendarAction: 'Kalender öffnen',
    openSettingsAction: 'Einstellungen öffnen',
    emptyTitle: 'Home-Module sind ausgeblendet',
    emptyBody:
        'Aktiviere Workspace-Status oder letzte Aktivität wieder, damit Home nützlich wird.',
    allDayLabel: 'Ganztägig',
  );
}
