import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// The Calendar feature screen.
class CalendarScreen extends ConsumerWidget {
  const CalendarScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final asyncEvents = ref.watch(calendarProvider);

    return CustomScrollView(
      slivers: [
        SliverAppBar.large(title: Text(l10n.calendarScreenTitle)),
        SliverFillRemaining(
          hasScrollBody: false,
          child: asyncEvents.when(
            loading: () => LoadingState(message: l10n.loadingLabel),
            error: (error, _) => ErrorState(
              message: l10n.errorStateLabel,
              retryLabel: l10n.retryButton,
              onRetry: () => ref.invalidate(calendarProvider),
            ),
            data: (events) => events.isEmpty
                ? EmptyState(
                    message: l10n.calendarEmptyMessage,
                    icon: Icons.calendar_today_outlined,
                  )
                : const SizedBox.shrink(),
          ),
        ),
      ],
    );
  }
}
