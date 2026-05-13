import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// The Calendar feature screen.
class CalendarScreen extends ConsumerWidget {
  const CalendarScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final asyncEvents = ref.watch(calendarProvider);

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showEventDialog(context, ref),
        icon: const Icon(Icons.add),
        label: Text(l10n.calendarCreateButton),
      ),
      body: CustomScrollView(
        slivers: [
          SliverAppBar.large(title: Text(l10n.calendarScreenTitle)),
          asyncEvents.when(
            loading: () => SliverFillRemaining(
              hasScrollBody: false,
              child: LoadingState(message: l10n.loadingLabel),
            ),
            error: (error, _) => SliverFillRemaining(
              hasScrollBody: false,
              child: ErrorState(
                message: l10n.errorStateLabel,
                retryLabel: l10n.retryButton,
                onRetry: () => ref.invalidate(calendarProvider),
              ),
            ),
            data: (events) => events.isEmpty
                ? SliverFillRemaining(
                    hasScrollBody: false,
                    child: EmptyState(
                      message: l10n.calendarEmptyMessage,
                      icon: Icons.calendar_today_outlined,
                    ),
                  )
                : SliverPadding(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
                    sliver: SliverList.separated(
                      itemCount: events.length,
                      separatorBuilder: (context, index) =>
                          const SizedBox(height: 12),
                      itemBuilder: (context, index) => _CalendarEventCard(
                        event: events[index],
                        onEdit: () => _showEventDialog(
                          context,
                          ref,
                          event: events[index],
                        ),
                        onDelete: () =>
                            _deleteEvent(context, ref, events[index]),
                      ),
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  Future<void> _showEventDialog(
    BuildContext context,
    WidgetRef ref, {
    CalendarEvent? event,
  }) async {
    final draft = await showDialog<CalendarEventDraft>(
      context: context,
      builder: (context) => _CalendarEventDialog(initialEvent: event),
    );
    if (draft == null || !context.mounted) {
      return;
    }

    final l10n = AppLocalizations.of(context);
    if (event == null) {
      await ref.read(calendarProvider.notifier).createEvent(draft);
    } else {
      await ref.read(calendarProvider.notifier).updateEvent(event.id, draft);
    }
    if (!context.mounted) {
      return;
    }
    final state = ref.read(calendarProvider);
    final message = state.hasError
        ? l10n.calendarOperationFailure
        : event == null
        ? l10n.calendarCreateSuccess
        : l10n.calendarUpdateSuccess;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _deleteEvent(
    BuildContext context,
    WidgetRef ref,
    CalendarEvent event,
  ) async {
    final l10n = AppLocalizations.of(context);
    await ref.read(calendarProvider.notifier).deleteEvent(event.id);
    if (!context.mounted) {
      return;
    }
    final state = ref.read(calendarProvider);
    final message = state.hasError
        ? l10n.calendarOperationFailure
        : l10n.calendarDeleteSuccess;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }
}

class _CalendarEventCard extends StatelessWidget {
  const _CalendarEventCard({
    required this.event,
    required this.onEdit,
    required this.onDelete,
  });

  final CalendarEvent event;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final startsAt = _formatDateTime(context, event.startTime);
    final endsAt = _formatDateTime(context, event.endTime);

    return Semantics(
      label: l10n.calendarEventSemantic(event.title, startsAt, endsAt),
      button: false,
      child: Card(
        elevation: 0,
        color: theme.colorScheme.surfaceContainerLow,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: BorderSide(color: theme.colorScheme.outlineVariant),
        ),
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 20,
            vertical: 8,
          ),
          title: Text(event.title, style: theme.textTheme.titleMedium),
          subtitle: Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('$startsAt – $endsAt'),
                if ((event.location ?? '').isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(event.location!),
                ],
                if ((event.description ?? '').isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(event.description!),
                ],
              ],
            ),
          ),
          trailing: Wrap(
            spacing: 4,
            children: [
              IconButton(
                tooltip: l10n.calendarEditEventTooltip(event.title),
                onPressed: onEdit,
                icon: const Icon(Icons.edit_outlined),
              ),
              IconButton(
                tooltip: l10n.calendarDeleteEventTooltip(event.title),
                onPressed: onDelete,
                icon: const Icon(Icons.delete_outline),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _CalendarEventDialog extends StatefulWidget {
  const _CalendarEventDialog({this.initialEvent});

  final CalendarEvent? initialEvent;

  @override
  State<_CalendarEventDialog> createState() => _CalendarEventDialogState();
}

class _CalendarEventDialogState extends State<_CalendarEventDialog> {
  final _formKey = GlobalKey<FormState>();
  final _titleController = TextEditingController();
  final _descriptionController = TextEditingController();
  final _locationController = TextEditingController();

  @override
  void initState() {
    super.initState();
    final initialEvent = widget.initialEvent;
    if (initialEvent != null) {
      _titleController.text = initialEvent.title;
      _descriptionController.text = initialEvent.description ?? '';
      _locationController.text = initialEvent.location ?? '';
    }
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
    final initialEvent = widget.initialEvent;

    return AlertDialog(
      title: Text(
        initialEvent == null
            ? l10n.calendarCreateDialogTitle
            : l10n.calendarEditDialogTitle,
      ),
      content: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextFormField(
                controller: _titleController,
                autofocus: true,
                textInputAction: TextInputAction.next,
                decoration: InputDecoration(
                  labelText: l10n.calendarTitleFieldLabel,
                ),
                validator: (value) => value == null || value.trim().isEmpty
                    ? l10n.calendarTitleRequired
                    : null,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _descriptionController,
                minLines: 2,
                maxLines: 4,
                decoration: InputDecoration(
                  labelText: l10n.calendarDescriptionFieldLabel,
                ),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _locationController,
                decoration: InputDecoration(
                  labelText: l10n.calendarLocationFieldLabel,
                ),
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(l10n.calendarCancelButton),
        ),
        FilledButton(
          onPressed: () {
            if (!_formKey.currentState!.validate()) {
              return;
            }
            final startsAt = initialEvent?.startTime ?? _defaultStartTime();
            Navigator.of(context).pop(
              CalendarEventDraft(
                title: _titleController.text.trim(),
                description: _blankToNull(_descriptionController.text),
                location: _blankToNull(_locationController.text),
                startTime: startsAt,
                endTime:
                    initialEvent?.endTime ??
                    startsAt.add(const Duration(hours: 1)),
                timezone: initialEvent?.timezone ?? 'UTC',
                allDay: initialEvent?.allDay ?? false,
              ),
            );
          },
          child: Text(l10n.calendarSaveButton),
        ),
      ],
    );
  }

  String? _blankToNull(String value) {
    final trimmed = value.trim();
    return trimmed.isEmpty ? null : trimmed;
  }

  DateTime _defaultStartTime() {
    final now = DateTime.now().toUtc();
    return DateTime.utc(now.year, now.month, now.day, now.hour + 1);
  }
}

String _formatDateTime(BuildContext context, DateTime value) {
  return DateFormat.yMMMd(
    Localizations.localeOf(context).toLanguageTag(),
  ).add_Hm().format(value.toLocal());
}
