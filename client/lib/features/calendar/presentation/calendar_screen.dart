import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/app/presentation/workspace_capability_recovery_presenter.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

enum CalendarViewMode { agenda, day, week, month }

class CalendarScreen extends ConsumerStatefulWidget {
  const CalendarScreen({super.key});

  @override
  ConsumerState<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends ConsumerState<CalendarScreen> {
  CalendarViewMode _viewMode = CalendarViewMode.agenda;
  DateTime _focusedDate = DateTime.now();

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final capabilitySnapshot = ref.watch(workspaceCapabilitySnapshotProvider);
    final canCreateEvent = capabilitySnapshot.maybeWhen(
      data: (snapshot) => snapshot.calendar.isReady,
      orElse: () => false,
    );

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.calendarScreenTitle),
        actions: [
          IconButton(
            tooltip: l10n.calendarCreateButton,
            onPressed: canCreateEvent ? () => _showEventEditor(context) : null,
            icon: const Icon(Icons.add),
          ),
        ],
      ),
      body: SafeArea(
        child: capabilitySnapshot.when(
          data: (snapshot) {
            if (!snapshot.calendar.isReady) {
              return _CalendarCapabilityBody(
                capability: snapshot.calendar,
                onRetry: _refreshCapability,
              );
            }
            return _CalendarAppBody(
              viewMode: _viewMode,
              focusedDate: _focusedDate,
              onViewModeChanged: (mode) => setState(() => _viewMode = mode),
              onFocusedDateChanged: (date) =>
                  setState(() => _focusedDate = date),
              onCreate: () => _showEventEditor(context),
              onEdit: (event) => _showEventEditor(context, event: event),
              onDelete: _confirmDelete,
              onRefresh: _refreshCalendar,
            );
          },
          error: (_, _) => ErrorState(
            message: l10n.calendarCapabilityError,
            guidance: l10n.settingsWorkspaceRecoveryUnavailableAction,
            retryLabel: l10n.retryButton,
            onRetry: _refreshCapability,
          ),
          loading: () => LoadingState(
            message: l10n.calendarCapabilityLoading,
            hint: l10n.bootstrapLoadingHint,
            icon: Icons.calendar_today_outlined,
          ),
        ),
      ),
    );
  }

  void _refreshCapability() {
    ref.invalidate(weaveApiWorkspaceCapabilitySnapshotProvider);
  }

  void _refreshCalendar() {
    ref
      ..invalidate(calendarProvider)
      ..invalidate(calendarScopesProvider)
      ..invalidate(calendarClientSetupProvider);
  }

  Future<void> _showEventEditor(
    BuildContext context, {
    CalendarEvent? event,
  }) async {
    final draft = await showDialog<CalendarEventDraft>(
      context: context,
      builder: (context) {
        return _CalendarEventEditorDialog(
          initialEvent: event,
          initialScope: ref.read(selectedCalendarScopeProvider),
          initialDate: _focusedDate,
        );
      },
    );
    if (!context.mounted || draft == null) {
      return;
    }

    final l10n = AppLocalizations.of(context);
    try {
      if (event == null) {
        await ref.read(calendarProvider.notifier).createEvent(draft);
        if (context.mounted) {
          _showSnackBar(context, l10n.calendarCreateSuccess);
        }
      } else {
        await ref
            .read(calendarProvider.notifier)
            .updateEvent(event.id, draft, etag: event.etag);
        if (context.mounted) {
          _showSnackBar(context, l10n.calendarUpdateSuccess);
        }
      }
    } catch (_) {
      if (context.mounted) {
        _showSnackBar(context, l10n.calendarOperationFailure);
      }
    }
  }

  Future<void> _confirmDelete(CalendarEvent event) async {
    final context = this.context;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_copy(context).deleteTitle),
        content: Text(_copy(context).deleteMessage(event.title)),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(AppLocalizations.of(context).calendarCancelButton),
          ),
          FilledButton.tonal(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(_copy(context).deleteButton),
          ),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) {
      return;
    }

    final l10n = AppLocalizations.of(context);
    try {
      await ref.read(calendarProvider.notifier).deleteEvent(event.id);
      if (context.mounted) {
        _showSnackBar(context, l10n.calendarDeleteSuccess);
      }
    } catch (_) {
      if (context.mounted) {
        _showSnackBar(context, l10n.calendarOperationFailure);
      }
    }
  }

  void _showSnackBar(BuildContext context, String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }
}

class _CalendarAppBody extends ConsumerWidget {
  const _CalendarAppBody({
    required this.viewMode,
    required this.focusedDate,
    required this.onViewModeChanged,
    required this.onFocusedDateChanged,
    required this.onCreate,
    required this.onEdit,
    required this.onDelete,
    required this.onRefresh,
  });

  final CalendarViewMode viewMode;
  final DateTime focusedDate;
  final ValueChanged<CalendarViewMode> onViewModeChanged;
  final ValueChanged<DateTime> onFocusedDateChanged;
  final VoidCallback onCreate;
  final ValueChanged<CalendarEvent> onEdit;
  final ValueChanged<CalendarEvent> onDelete;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final scopes = ref.watch(calendarScopesProvider);
    final events = ref.watch(calendarProvider);
    final selectedScope = ref.watch(selectedCalendarScopeProvider);
    final copy = _copy(context);

    return RefreshIndicator(
      onRefresh: () async => onRefresh(),
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
        children: [
          _CalendarScopeSummary(scope: selectedScope),
          const SizedBox(height: 12),
          scopes.when(
            data: (scopeList) => _CalendarScopeSelector(
              scopes: scopeList.scopes,
              selectedScope: selectedScope,
            ),
            error: (_, _) => _InlineNotice(
              icon: Icons.info_outline,
              title: l10n.calendarClientSetupUnavailable,
            ),
            loading: () => _InlineNotice(
              icon: Icons.sync,
              title: l10n.calendarClientSetupLoading,
            ),
          ),
          const SizedBox(height: 12),
          _CalendarViewToolbar(
            viewMode: viewMode,
            focusedDate: focusedDate,
            onViewModeChanged: onViewModeChanged,
            onFocusedDateChanged: onFocusedDateChanged,
          ),
          const SizedBox(height: 12),
          events.when(
            data: (eventList) {
              final visibleEvents = _visibleEvents(
                eventList.events,
                viewMode,
                focusedDate,
              );
              if (visibleEvents.isEmpty) {
                return EmptyState(
                  message: l10n.calendarEmptyMessage,
                  guidance: copy.emptyGuidance(viewMode),
                  icon: Icons.event_available_outlined,
                  actionLabel: l10n.calendarCreateButton,
                  onAction: onCreate,
                );
              }
              return _CalendarEventCollection(
                events: visibleEvents,
                viewMode: viewMode,
                focusedDate: focusedDate,
                onEdit: onEdit,
                onDelete: onDelete,
              );
            },
            error: (_, _) => ErrorState(
              message: l10n.calendarDetailsError,
              guidance: l10n.calendarOperationFailure,
              retryLabel: l10n.retryButton,
              onRetry: onRefresh,
            ),
            loading: () => LoadingState(
              message: l10n.calendarDetailsLoading,
              hint: l10n.bootstrapLoadingHint,
              icon: Icons.calendar_today_outlined,
            ),
          ),
          const SizedBox(height: 12),
          _CalendarClientSetupSummary(),
        ],
      ),
    );
  }
}

class _CalendarScopeSummary extends StatelessWidget {
  const _CalendarScopeSummary({required this.scope});

  final CalendarScope scope;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final isWorkspace = scope.isWorkspace;
    return Semantics(
      container: true,
      header: true,
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                isWorkspace ? l10n.calendarWorkspaceScopeTitle : scope.label,
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 8),
              Text(
                isWorkspace
                    ? l10n.calendarWorkspaceScopeDescription
                    : l10n.calendarGenericScopeDescription(scope.label),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _CalendarScopeSelector extends ConsumerWidget {
  const _CalendarScopeSelector({
    required this.scopes,
    required this.selectedScope,
  });

  final List<CalendarScope> scopes;
  final CalendarScope selectedScope;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (scopes.length <= 1) {
      return const SizedBox.shrink();
    }
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        for (final scope in scopes)
          ChoiceChip(
            label: Text(scope.label),
            selected: scope.id == selectedScope.id,
            onSelected: (_) {
              ref.read(selectedCalendarScopeProvider.notifier).select(scope);
              ref.invalidate(calendarProvider);
            },
          ),
      ],
    );
  }
}

class _CalendarViewToolbar extends StatelessWidget {
  const _CalendarViewToolbar({
    required this.viewMode,
    required this.focusedDate,
    required this.onViewModeChanged,
    required this.onFocusedDateChanged,
  });

  final CalendarViewMode viewMode;
  final DateTime focusedDate;
  final ValueChanged<CalendarViewMode> onViewModeChanged;
  final ValueChanged<DateTime> onFocusedDateChanged;

  @override
  Widget build(BuildContext context) {
    final copy = _copy(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: SegmentedButton<CalendarViewMode>(
            segments: [
              for (final mode in CalendarViewMode.values)
                ButtonSegment(
                  value: mode,
                  icon: Icon(_modeIcon(mode)),
                  label: Text(copy.viewModeLabel(mode)),
                ),
            ],
            selected: {viewMode},
            onSelectionChanged: (selection) =>
                onViewModeChanged(selection.single),
          ),
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            IconButton(
              tooltip: copy.previousRange,
              onPressed: () => onFocusedDateChanged(
                _shiftFocusedDate(focusedDate, viewMode, -1),
              ),
              icon: const Icon(Icons.chevron_left),
            ),
            Expanded(
              child: Semantics(
                header: true,
                child: Text(
                  _rangeLabel(context, focusedDate, viewMode),
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
            ),
            IconButton(
              tooltip: copy.nextRange,
              onPressed: () => onFocusedDateChanged(
                _shiftFocusedDate(focusedDate, viewMode, 1),
              ),
              icon: const Icon(Icons.chevron_right),
            ),
            TextButton(
              onPressed: () => onFocusedDateChanged(DateTime.now()),
              child: Text(copy.today),
            ),
          ],
        ),
      ],
    );
  }
}

class _CalendarEventCollection extends StatelessWidget {
  const _CalendarEventCollection({
    required this.events,
    required this.viewMode,
    required this.focusedDate,
    required this.onEdit,
    required this.onDelete,
  });

  final List<CalendarEvent> events;
  final CalendarViewMode viewMode;
  final DateTime focusedDate;
  final ValueChanged<CalendarEvent> onEdit;
  final ValueChanged<CalendarEvent> onDelete;

  @override
  Widget build(BuildContext context) {
    return switch (viewMode) {
      CalendarViewMode.agenda => _AgendaEventList(
        events: events,
        onEdit: onEdit,
        onDelete: onDelete,
      ),
      CalendarViewMode.day => _TimelineEventList(
        events: events,
        emptyDate: focusedDate,
        onEdit: onEdit,
        onDelete: onDelete,
      ),
      CalendarViewMode.week => _WeekEventList(
        events: events,
        focusedDate: focusedDate,
        onEdit: onEdit,
        onDelete: onDelete,
      ),
      CalendarViewMode.month => _MonthEventList(
        events: events,
        focusedDate: focusedDate,
        onEdit: onEdit,
        onDelete: onDelete,
      ),
    };
  }
}

class _AgendaEventList extends StatelessWidget {
  const _AgendaEventList({
    required this.events,
    required this.onEdit,
    required this.onDelete,
  });

  final List<CalendarEvent> events;
  final ValueChanged<CalendarEvent> onEdit;
  final ValueChanged<CalendarEvent> onDelete;

  @override
  Widget build(BuildContext context) {
    final groups = _groupEventsByDay(events);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final entry in groups.entries) ...[
          _DateHeader(date: entry.key),
          for (final event in entry.value)
            _CalendarEventCard(
              event: event,
              onEdit: onEdit,
              onDelete: onDelete,
            ),
        ],
      ],
    );
  }
}

class _TimelineEventList extends StatelessWidget {
  const _TimelineEventList({
    required this.events,
    required this.emptyDate,
    required this.onEdit,
    required this.onDelete,
  });

  final List<CalendarEvent> events;
  final DateTime emptyDate;
  final ValueChanged<CalendarEvent> onEdit;
  final ValueChanged<CalendarEvent> onDelete;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _DateHeader(date: emptyDate),
        for (final event in events)
          _CalendarEventCard(event: event, onEdit: onEdit, onDelete: onDelete),
      ],
    );
  }
}

class _WeekEventList extends StatelessWidget {
  const _WeekEventList({
    required this.events,
    required this.focusedDate,
    required this.onEdit,
    required this.onDelete,
  });

  final List<CalendarEvent> events;
  final DateTime focusedDate;
  final ValueChanged<CalendarEvent> onEdit;
  final ValueChanged<CalendarEvent> onDelete;

  @override
  Widget build(BuildContext context) {
    final start = _startOfWeek(focusedDate);
    final days = [
      for (var i = 0; i < 7; i++) _dateOnly(start.add(Duration(days: i))),
    ];
    final groups = _groupEventsByDay(events);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final day in days) ...[
          _DateHeader(date: day),
          if ((groups[day] ?? const []).isEmpty)
            _QuietText(text: _copy(context).noEventsForDay)
          else
            for (final event in groups[day]!)
              _CalendarEventCard(
                event: event,
                onEdit: onEdit,
                onDelete: onDelete,
              ),
        ],
      ],
    );
  }
}

class _MonthEventList extends StatelessWidget {
  const _MonthEventList({
    required this.events,
    required this.focusedDate,
    required this.onEdit,
    required this.onDelete,
  });

  final List<CalendarEvent> events;
  final DateTime focusedDate;
  final ValueChanged<CalendarEvent> onEdit;
  final ValueChanged<CalendarEvent> onDelete;

  @override
  Widget build(BuildContext context) {
    final monthStart = DateTime(focusedDate.year, focusedDate.month);
    final daysInMonth = DateUtils.getDaysInMonth(
      focusedDate.year,
      focusedDate.month,
    );
    final days = [
      for (var i = 0; i < daysInMonth; i++)
        _dateOnly(monthStart.add(Duration(days: i))),
    ];
    final groups = _groupEventsByDay(events);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final day in days)
          if ((groups[day] ?? const []).isNotEmpty) ...[
            _DateHeader(date: day),
            for (final event in groups[day]!)
              _CalendarEventCard(
                event: event,
                onEdit: onEdit,
                onDelete: onDelete,
              ),
          ],
      ],
    );
  }
}

class _DateHeader extends StatelessWidget {
  const _DateHeader({required this.date});

  final DateTime date;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 16, bottom: 8),
      child: Semantics(
        header: true,
        child: Text(
          DateFormat.yMMMMEEEEd(
            Localizations.localeOf(context).toString(),
          ).format(date),
          style: Theme.of(context).textTheme.titleSmall,
        ),
      ),
    );
  }
}

class _CalendarEventCard extends StatelessWidget {
  const _CalendarEventCard({
    required this.event,
    required this.onEdit,
    required this.onDelete,
  });

  final CalendarEvent event;
  final ValueChanged<CalendarEvent> onEdit;
  final ValueChanged<CalendarEvent> onDelete;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final copy = _copy(context);
    final timeLabel = _eventTimeLabel(context, event);
    final semanticLabel = l10n.calendarEventSemantic(
      event.title,
      _formatDateTime(context, event.startTime),
      _formatDateTime(context, event.endTime),
    );

    return Card(
      child: Semantics(
        container: true,
        label: semanticLabel,
        child: ListTile(
          leading: Icon(
            event.allDay ? Icons.event_available : Icons.schedule,
            semanticLabel: event.allDay ? copy.allDay : null,
          ),
          title: Text(event.title),
          subtitle: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(timeLabel),
              if (event.location case final location?)
                Text('${l10n.calendarDetailsLocationLabel}: $location'),
              Text('${l10n.calendarDetailsScopeLabel}: ${event.scope.label}'),
            ],
          ),
          onTap: () => _showEventDetails(context, event),
          trailing: Wrap(
            spacing: 4,
            children: [
              IconButton(
                tooltip: l10n.calendarEditEventTooltip(event.title),
                onPressed: () => onEdit(event),
                icon: const Icon(Icons.edit_outlined),
              ),
              IconButton(
                tooltip: l10n.calendarDeleteEventTooltip(event.title),
                onPressed: () => onDelete(event),
                icon: const Icon(Icons.delete_outline),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _CalendarClientSetupSummary extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final setup = ref.watch(calendarClientSetupProvider);
    return setup.when(
      data: (setup) => Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                l10n.calendarClientSetupTitle,
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 8),
              Text(l10n.calendarClientSetupDescription),
              const SizedBox(height: 8),
              Text(
                '${l10n.calendarClientSetupUsernameLabel}: ${setup.username}',
              ),
              Text(
                l10n.calendarClientSetupCredentialReadinessStatus(
                  setup.credentialReadiness.status,
                ),
              ),
              Text(
                setup.credentialReadiness.backendActorCredentialsExposed
                    ? l10n.calendarClientSetupCredentialsUnsafe
                    : l10n.calendarClientSetupCredentialsSafe,
              ),
            ],
          ),
        ),
      ),
      error: (_, _) => _InlineNotice(
        icon: Icons.info_outline,
        title: l10n.calendarClientSetupUnavailable,
      ),
      loading: () => _InlineNotice(
        icon: Icons.sync,
        title: l10n.calendarClientSetupLoading,
      ),
    );
  }
}

class _InlineNotice extends StatelessWidget {
  const _InlineNotice({required this.icon, required this.title});

  final IconData icon;
  final String title;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(leading: Icon(icon), title: Text(title)),
    );
  }
}

class _QuietText extends StatelessWidget {
  const _QuietText({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 8, bottom: 8),
      child: Text(
        text,
        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
          color: Theme.of(context).colorScheme.onSurfaceVariant,
        ),
      ),
    );
  }
}

class _CalendarCapabilityBody extends StatelessWidget {
  const _CalendarCapabilityBody({
    required this.capability,
    required this.onRetry,
  });

  final WorkspaceCapabilityState capability;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final recovery = workspaceCapabilityRecoveryPresentation(l10n, capability);

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: EmptyState(
          message: l10n.calendarUnavailableTitle,
          guidance: recovery.recovery,
          icon: Icons.calendar_today_outlined,
          actionLabel: l10n.retryButton,
          onAction: onRetry,
          semanticLabel: recovery.semanticLabel(l10n, l10n.navCalendar),
        ),
      ),
    );
  }
}

class _CalendarEventEditorDialog extends StatefulWidget {
  const _CalendarEventEditorDialog({
    required this.initialScope,
    required this.initialDate,
    this.initialEvent,
  });

  final CalendarEvent? initialEvent;
  final CalendarScope initialScope;
  final DateTime initialDate;

  @override
  State<_CalendarEventEditorDialog> createState() =>
      _CalendarEventEditorDialogState();
}

class _CalendarEventEditorDialogState
    extends State<_CalendarEventEditorDialog> {
  late final TextEditingController _titleController;
  late final TextEditingController _descriptionController;
  late final TextEditingController _locationController;
  late DateTime _startTime;
  late DateTime _endTime;
  late bool _allDay;

  @override
  void initState() {
    super.initState();
    final event = widget.initialEvent;
    final start = event?.startTime ?? _defaultStart(widget.initialDate);
    _titleController = TextEditingController(text: event?.title ?? '');
    _descriptionController = TextEditingController(
      text: event?.description ?? '',
    );
    _locationController = TextEditingController(text: event?.location ?? '');
    _startTime = start;
    _endTime = event?.endTime ?? start.add(const Duration(hours: 1));
    _allDay = event?.allDay ?? false;
  }

  @override
  void dispose() {
    _titleController.dispose();
    _descriptionController.dispose();
    _locationController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final copy = _copy(context);
    return AlertDialog(
      title: Text(
        widget.initialEvent == null
            ? l10n.calendarCreateDialogTitle
            : l10n.calendarEditDialogTitle,
      ),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _titleController,
              autofocus: true,
              textInputAction: TextInputAction.next,
              decoration: InputDecoration(
                labelText: l10n.calendarTitleFieldLabel,
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _descriptionController,
              minLines: 2,
              maxLines: 4,
              decoration: InputDecoration(
                labelText: l10n.calendarDescriptionFieldLabel,
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _locationController,
              textInputAction: TextInputAction.done,
              decoration: InputDecoration(
                labelText: l10n.calendarLocationFieldLabel,
              ),
            ),
            const SizedBox(height: 12),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(copy.allDay),
              value: _allDay,
              onChanged: (value) => setState(() => _allDay = value),
            ),
            _DateTimePickerTile(
              label: copy.starts,
              value: _startTime,
              includeTime: !_allDay,
              onChanged: (value) {
                setState(() {
                  final delta = _endTime.difference(_startTime);
                  _startTime = value;
                  _endTime = value.add(
                    delta.isNegative ? const Duration(hours: 1) : delta,
                  );
                });
              },
            ),
            _DateTimePickerTile(
              label: copy.ends,
              value: _endTime,
              includeTime: !_allDay,
              onChanged: (value) => setState(() => _endTime = value),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(l10n.calendarCancelButton),
        ),
        FilledButton(onPressed: _submit, child: Text(l10n.calendarSaveButton)),
      ],
    );
  }

  void _submit() {
    final l10n = AppLocalizations.of(context);
    final title = _titleController.text.trim();
    if (title.isEmpty) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(l10n.calendarTitleRequired)));
      return;
    }
    if (!_endTime.isAfter(_startTime)) {
      _endTime = _startTime.add(
        _allDay ? const Duration(days: 1) : const Duration(hours: 1),
      );
    }
    Navigator.of(context).pop(
      CalendarEventDraft(
        title: title,
        description: _blankToNull(_descriptionController.text),
        startTime: _allDay ? _dateOnly(_startTime) : _startTime,
        endTime: _allDay ? _dateOnly(_endTime) : _endTime,
        timezone: DateTime.now().timeZoneName,
        location: _blankToNull(_locationController.text),
        allDay: _allDay,
        scope: widget.initialEvent?.scope ?? widget.initialScope,
      ),
    );
  }
}

class _DateTimePickerTile extends StatelessWidget {
  const _DateTimePickerTile({
    required this.label,
    required this.value,
    required this.includeTime,
    required this.onChanged,
  });

  final String label;
  final DateTime value;
  final bool includeTime;
  final ValueChanged<DateTime> onChanged;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(label),
      subtitle: Text(_formatDateTime(context, value, includeTime: includeTime)),
      trailing: const Icon(Icons.edit_calendar_outlined),
      onTap: () async {
        final date = await showDatePicker(
          context: context,
          initialDate: value,
          firstDate: DateTime(2020),
          lastDate: DateTime(2035),
        );
        if (date == null || !context.mounted) {
          return;
        }
        var selected = DateTime(
          date.year,
          date.month,
          date.day,
          value.hour,
          value.minute,
        );
        if (includeTime) {
          final time = await showTimePicker(
            context: context,
            initialTime: TimeOfDay.fromDateTime(value),
          );
          if (time == null) {
            return;
          }
          selected = DateTime(
            date.year,
            date.month,
            date.day,
            time.hour,
            time.minute,
          );
        }
        onChanged(selected);
      },
    );
  }
}

void _showEventDetails(BuildContext context, CalendarEvent event) {
  final l10n = AppLocalizations.of(context);
  showDialog<void>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(l10n.calendarDetailsDialogTitle),
      content: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(event.title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            _DetailLine(
              label: l10n.calendarDetailsTimeLabel,
              value: _eventTimeLabel(context, event),
            ),
            _DetailLine(
              label: l10n.calendarDetailsScopeLabel,
              value: event.scope.label,
            ),
            if (event.location case final location?)
              _DetailLine(
                label: l10n.calendarDetailsLocationLabel,
                value: location,
              ),
            if (event.description case final description?)
              _DetailLine(
                label: l10n.calendarDetailsDescriptionLabel,
                value: description,
              ),
            if (event.attendees.isNotEmpty)
              _DetailLine(
                label: l10n.calendarDetailsAttendeesLabel,
                value: event.attendees
                    .map((attendee) => attendee.displayLabel)
                    .join('\n'),
              ),
            _DetailLine(
              label: l10n.calendarDetailsMeetingThreadLabel,
              value: l10n.calendarDetailsMeetingThreadPending,
            ),
            if (event.updatedAt case final updatedAt?)
              _DetailLine(
                label: l10n.calendarDetailsUpdatedLabel,
                value: _formatDateTime(context, updatedAt),
              ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(l10n.calendarCloseButton),
        ),
      ],
    ),
  );
}

class _DetailLine extends StatelessWidget {
  const _DetailLine({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: Theme.of(context).textTheme.labelLarge),
          Text(value),
        ],
      ),
    );
  }
}

List<CalendarEvent> _visibleEvents(
  List<CalendarEvent> events,
  CalendarViewMode viewMode,
  DateTime focusedDate,
) {
  final sorted = [...events]
    ..sort((a, b) => a.startTime.compareTo(b.startTime));
  return switch (viewMode) {
    CalendarViewMode.agenda => sorted,
    CalendarViewMode.day =>
      sorted
          .where((event) => DateUtils.isSameDay(event.startTime, focusedDate))
          .toList(growable: false),
    CalendarViewMode.week =>
      sorted
          .where((event) => _isInSameWeek(event.startTime, focusedDate))
          .toList(growable: false),
    CalendarViewMode.month =>
      sorted
          .where(
            (event) =>
                event.startTime.year == focusedDate.year &&
                event.startTime.month == focusedDate.month,
          )
          .toList(growable: false),
  };
}

Map<DateTime, List<CalendarEvent>> _groupEventsByDay(
  List<CalendarEvent> events,
) {
  final groups = <DateTime, List<CalendarEvent>>{};
  for (final event in events) {
    groups.putIfAbsent(_dateOnly(event.startTime), () => []).add(event);
  }
  return groups;
}

DateTime _shiftFocusedDate(
  DateTime focusedDate,
  CalendarViewMode mode,
  int delta,
) {
  return switch (mode) {
    CalendarViewMode.agenda ||
    CalendarViewMode.day => focusedDate.add(Duration(days: delta)),
    CalendarViewMode.week => focusedDate.add(Duration(days: delta * 7)),
    CalendarViewMode.month => DateTime(
      focusedDate.year,
      focusedDate.month + delta,
      focusedDate.day,
    ),
  };
}

String _rangeLabel(
  BuildContext context,
  DateTime focusedDate,
  CalendarViewMode mode,
) {
  final locale = Localizations.localeOf(context).toString();
  return switch (mode) {
    CalendarViewMode.agenda ||
    CalendarViewMode.day => DateFormat.yMMMMEEEEd(locale).format(focusedDate),
    CalendarViewMode.week =>
      '${DateFormat.MMMd(locale).format(_startOfWeek(focusedDate))} - ${DateFormat.MMMd(locale).format(_startOfWeek(focusedDate).add(const Duration(days: 6)))}',
    CalendarViewMode.month => DateFormat.yMMMM(locale).format(focusedDate),
  };
}

String _eventTimeLabel(BuildContext context, CalendarEvent event) {
  final copy = _copy(context);
  if (event.allDay) {
    return copy.allDay;
  }
  return '${_formatDateTime(context, event.startTime)} - ${_formatDateTime(context, event.endTime)}';
}

String _formatDateTime(
  BuildContext context,
  DateTime value, {
  bool includeTime = true,
}) {
  final locale = Localizations.localeOf(context).toString();
  final date = DateFormat.yMMMd(locale).format(value);
  if (!includeTime) {
    return date;
  }
  final time = MaterialLocalizations.of(
    context,
  ).formatTimeOfDay(TimeOfDay.fromDateTime(value));
  return '$date, $time';
}

DateTime _defaultStart(DateTime focusedDate) {
  final now = DateTime.now();
  if (DateUtils.isSameDay(focusedDate, now)) {
    return DateTime(now.year, now.month, now.day, now.hour + 1);
  }
  return DateTime(focusedDate.year, focusedDate.month, focusedDate.day, 9);
}

DateTime _dateOnly(DateTime value) =>
    DateTime(value.year, value.month, value.day);

DateTime _startOfWeek(DateTime value) {
  final date = _dateOnly(value);
  return date.subtract(Duration(days: date.weekday - DateTime.monday));
}

bool _isInSameWeek(DateTime a, DateTime b) =>
    _startOfWeek(a) == _startOfWeek(b);

String? _blankToNull(String value) {
  final trimmed = value.trim();
  return trimmed.isEmpty ? null : trimmed;
}

IconData _modeIcon(CalendarViewMode mode) {
  return switch (mode) {
    CalendarViewMode.agenda => Icons.view_agenda_outlined,
    CalendarViewMode.day => Icons.today_outlined,
    CalendarViewMode.week => Icons.view_week_outlined,
    CalendarViewMode.month => Icons.calendar_month_outlined,
  };
}

_CalendarCopy _copy(BuildContext context) {
  final languageCode = Localizations.localeOf(context).languageCode;
  return languageCode == 'de'
      ? const _CalendarCopy.de()
      : const _CalendarCopy.en();
}

class _CalendarCopy {
  const _CalendarCopy.en()
    : agenda = 'Agenda',
      day = 'Day',
      week = 'Week',
      month = 'Month',
      today = 'Today',
      previousRange = 'Previous calendar range',
      nextRange = 'Next calendar range',
      allDay = 'All day',
      starts = 'Starts',
      ends = 'Ends',
      noEventsForDay = 'No events this day',
      deleteTitle = 'Delete event',
      deleteButton = 'Delete',
      _deleteMessageTemplate = 'Delete "{title}"?',
      _emptyAgenda =
          'Create an event, or refresh if events should already be here.',
      _emptyDay = 'No events on this day. Create one from the Calendar action.',
      _emptyWeek =
          'No events this week. Move to another week or create an event.',
      _emptyMonth =
          'No events this month. Move to another month or create an event.';

  const _CalendarCopy.de()
    : agenda = 'Agenda',
      day = 'Tag',
      week = 'Woche',
      month = 'Monat',
      today = 'Heute',
      previousRange = 'Vorheriger Kalenderzeitraum',
      nextRange = 'Nächster Kalenderzeitraum',
      allDay = 'Ganztägig',
      starts = 'Beginn',
      ends = 'Ende',
      noEventsForDay = 'Keine Termine an diesem Tag',
      deleteTitle = 'Termin löschen',
      deleteButton = 'Löschen',
      _deleteMessageTemplate = '"{title}" löschen?',
      _emptyAgenda =
          'Erstelle einen Termin oder aktualisiere, falls Termine vorhanden sein sollten.',
      _emptyDay =
          'Keine Termine an diesem Tag. Erstelle einen über die Kalender-Aktion.',
      _emptyWeek =
          'Keine Termine in dieser Woche. Wechsle die Woche oder erstelle einen Termin.',
      _emptyMonth =
          'Keine Termine in diesem Monat. Wechsle den Monat oder erstelle einen Termin.';

  final String agenda;
  final String day;
  final String week;
  final String month;
  final String today;
  final String previousRange;
  final String nextRange;
  final String allDay;
  final String starts;
  final String ends;
  final String noEventsForDay;
  final String deleteTitle;
  final String deleteButton;
  final String _deleteMessageTemplate;
  final String _emptyAgenda;
  final String _emptyDay;
  final String _emptyWeek;
  final String _emptyMonth;

  String viewModeLabel(CalendarViewMode mode) {
    return switch (mode) {
      CalendarViewMode.agenda => agenda,
      CalendarViewMode.day => day,
      CalendarViewMode.week => week,
      CalendarViewMode.month => month,
    };
  }

  String emptyGuidance(CalendarViewMode mode) {
    return switch (mode) {
      CalendarViewMode.agenda => _emptyAgenda,
      CalendarViewMode.day => _emptyDay,
      CalendarViewMode.week => _emptyWeek,
      CalendarViewMode.month => _emptyMonth,
    };
  }

  String deleteMessage(String title) =>
      _deleteMessageTemplate.replaceFirst('{title}', title);
}
