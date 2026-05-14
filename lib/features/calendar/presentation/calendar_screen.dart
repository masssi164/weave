import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
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
    final asyncCalendar = ref.watch(calendarProvider);

    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showEventDialog(context, ref),
        icon: const Icon(Icons.add),
        label: Text(l10n.calendarCreateButton),
      ),
      body: CustomScrollView(
        slivers: [
          SliverAppBar.large(title: Text(l10n.calendarScreenTitle)),
          asyncCalendar.when(
            loading: () => SliverFillRemaining(
              hasScrollBody: false,
              child: LoadingState(message: l10n.loadingLabel),
            ),
            error: (error, _) => SliverFillRemaining(
              child: ErrorState(
                message: l10n.errorStateLabel,
                retryLabel: l10n.retryButton,
                onRetry: () => ref.invalidate(calendarProvider),
              ),
            ),
            data: (calendar) => SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
              sliver: SliverList.separated(
                itemCount: calendar.events.isEmpty
                    ? 3
                    : calendar.events.length + 2,
                separatorBuilder: (context, index) =>
                    const SizedBox(height: 12),
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return _CalendarScopeBanner(scope: calendar.scope);
                  }
                  if (calendar.events.isNotEmpty &&
                      index == calendar.events.length + 1) {
                    return const _CalendarClientSetupCard();
                  }
                  if (calendar.events.isEmpty) {
                    if (index == 1) {
                      return const _CalendarClientSetupCard();
                    }
                    return SizedBox(
                      height: 320,
                      child: EmptyState(
                        message: l10n.calendarEmptyMessage,
                        icon: Icons.calendar_today_outlined,
                      ),
                    );
                  }

                  final event = calendar.events[index - 1];
                  return _CalendarEventCard(
                    event: event,
                    onEdit: () => _showEventDialog(context, ref, event: event),
                    onDelete: () => _deleteEvent(context, ref, event),
                  );
                },
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

class _CalendarClientSetupCard extends ConsumerWidget {
  const _CalendarClientSetupCard();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final setup = ref.watch(calendarClientSetupProvider);

    return Semantics(
      container: true,
      child: Card(
        elevation: 0,
        color: theme.colorScheme.surfaceContainerLow,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: BorderSide(color: theme.colorScheme.outlineVariant),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    Icons.install_mobile_outlined,
                    color: theme.colorScheme.primary,
                    semanticLabel: l10n.calendarClientSetupIconSemantic,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          l10n.calendarClientSetupTitle,
                          style: theme.textTheme.titleMedium,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          l10n.calendarClientSetupDescription,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              switch (setup) {
                AsyncData(value: final value) => _CalendarClientSetupDetails(
                  setup: value,
                ),
                AsyncError() => ErrorState(
                  message: l10n.calendarClientSetupUnavailable,
                  retryLabel: l10n.retryButton,
                  onRetry: () => ref.invalidate(calendarClientSetupProvider),
                ),
                _ => Text(
                  l10n.calendarClientSetupLoading,
                  style: theme.textTheme.bodyMedium,
                ),
              },
            ],
          ),
        ),
      ),
    );
  }
}

class _CalendarClientSetupDetails extends StatelessWidget {
  const _CalendarClientSetupDetails({required this.setup});

  final CalendarClientSetup setup;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SetupValueRow(
          label: l10n.calendarClientSetupUsernameLabel,
          value: setup.username,
          copyValue: setup.username,
        ),
        _SetupValueRow(
          label: l10n.calendarClientSetupDiscoveryUrlLabel,
          value: setup.endpoints.caldavDiscoveryUrl,
          copyValue: setup.endpoints.caldavDiscoveryUrl,
        ),
        _SetupValueRow(
          label: l10n.calendarClientSetupPrincipalUrlLabel,
          value: setup.endpoints.principalUrl,
          copyValue: setup.endpoints.principalUrl,
        ),
        const SizedBox(height: 12),
        _SetupAccessModelSummary(accessModel: setup.accessModel),
        const SizedBox(height: 12),
        _SetupCredentialReadinessSummary(
          credentialPolicy: setup.credentialPolicy,
          readiness: setup.credentialReadiness,
        ),
        const SizedBox(height: 12),
        Text(
          l10n.calendarClientSetupPlatformsTitle,
          style: theme.textTheme.labelLarge,
        ),
        const SizedBox(height: 8),
        ...setup.options.map((option) => _SetupOptionTile(option: option)),
      ],
    );
  }
}

class _SetupAccessModelSummary extends StatelessWidget {
  const _SetupAccessModelSummary({required this.accessModel});

  final CalendarAccessModel accessModel;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final privateCalendarStatus = accessModel.privateUserCalendarsAvailable
        ? l10n.calendarClientSetupPrivateCalendarsAvailable
        : l10n.calendarClientSetupPrivateCalendarsBlocked;

    return MergeSemantics(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l10n.calendarClientSetupAccessModelTitle,
            style: theme.textTheme.labelLarge,
          ),
          const SizedBox(height: 4),
          Text(
            privateCalendarStatus,
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            accessModel.privateUserCalendarsReason,
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            l10n.calendarClientSetupExternalCredentialModel(
              accessModel.externalClientCredentialModel,
            ),
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          ...accessModel.notes.map(
            (note) => Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Text(
                note,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SetupCredentialReadinessSummary extends StatelessWidget {
  const _SetupCredentialReadinessSummary({
    required this.credentialPolicy,
    required this.readiness,
  });

  final String credentialPolicy;
  final CalendarCredentialReadiness readiness;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final safeCredentialBoundary = readiness.backendActorCredentialsExposed
        ? l10n.calendarClientSetupCredentialsUnsafe
        : l10n.calendarClientSetupCredentialsSafe;
    final blockers = <String>[
      if (!readiness.appleProfileSigned)
        l10n.calendarClientSetupAppleProfileBlocked,
      if (!readiness.readOnlySubscriptionTokensAvailable)
        l10n.calendarClientSetupSubscriptionsBlocked,
      ...readiness.blockers,
    ];

    return MergeSemantics(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l10n.calendarClientSetupCredentialReadinessTitle,
            style: theme.textTheme.labelLarge,
          ),
          const SizedBox(height: 4),
          Text(
            l10n.calendarClientSetupCredentialReadinessStatus(readiness.status),
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            safeCredentialBoundary,
            style: theme.textTheme.bodySmall?.copyWith(
              color: readiness.backendActorCredentialsExposed
                  ? theme.colorScheme.error
                  : theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            credentialPolicy,
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          ...blockers.map(
            (blocker) => Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Text(
                blocker,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SetupValueRow extends StatelessWidget {
  const _SetupValueRow({
    required this.label,
    required this.value,
    required this.copyValue,
  });

  final String label;
  final String value;
  final String copyValue;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: theme.textTheme.labelLarge),
                const SizedBox(height: 2),
                SelectableText(
                  value,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          IconButton(
            tooltip: l10n.calendarClientSetupCopyTooltip(label),
            onPressed: () async {
              await Clipboard.setData(ClipboardData(text: copyValue));
              if (!context.mounted) {
                return;
              }
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text(l10n.calendarClientSetupCopied)),
              );
            },
            icon: const Icon(Icons.copy_outlined),
          ),
        ],
      ),
    );
  }
}

class _SetupOptionTile extends StatelessWidget {
  const _SetupOptionTile({required this.option});

  final CalendarClientSetupOption option;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final status = option.available
        ? l10n.calendarClientSetupAvailableStatus
        : l10n.calendarClientSetupPlannedStatus;
    final reason = option.available
        ? option.actionUrl
        : option.unavailableReason ?? l10n.calendarClientSetupPlannedFallback;

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: MergeSemantics(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              option.available
                  ? Icons.check_circle_outline
                  : Icons.lock_clock_outlined,
              color: option.available
                  ? theme.colorScheme.primary
                  : theme.colorScheme.onSurfaceVariant,
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    l10n.calendarClientSetupOptionTitle(
                      option.platform,
                      option.method,
                      status,
                    ),
                    style: theme.textTheme.bodyMedium,
                  ),
                  if (reason != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      reason,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CalendarScopeBanner extends StatelessWidget {
  const _CalendarScopeBanner({required this.scope});

  final CalendarScope scope;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final isWorkspace = scope.isWorkspace;
    final title = isWorkspace ? l10n.calendarWorkspaceScopeTitle : scope.label;
    final description = isWorkspace
        ? l10n.calendarWorkspaceScopeDescription
        : l10n.calendarGenericScopeDescription(scope.label);

    return Semantics(
      container: true,
      child: Card(
        elevation: 0,
        color: theme.colorScheme.secondaryContainer,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                Icons.groups_2_outlined,
                color: theme.colorScheme.onSecondaryContainer,
                semanticLabel: l10n.semanticCalendarIcon,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: theme.textTheme.titleMedium?.copyWith(
                        color: theme.colorScheme.onSecondaryContainer,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      description,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSecondaryContainer,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
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
