import 'package:flutter/material.dart';
import 'package:weave/core/utils/build_context_extensions.dart';

/// Placeholder screen for the Nextcloud CalDAV Calendar feature.
class CalendarView extends StatelessWidget {
  const CalendarView({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: const Text('Calendar'),
          centerTitle: true,
        ),
        body: Center(
          child: Semantics(
            liveRegion: true,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  Icons.calendar_today,
                  size: 64,
                  color: context.colors.primary,
                  semanticLabel: 'Calendar icon',
                ),
                const SizedBox(height: 16),
                Text(
                  'Calendar coming soon',
                  style: context.textTheme.headlineSmall,
                ),
                const SizedBox(height: 8),
                Text(
                  'CalDAV events will appear here.',
                  style: context.textTheme.bodyMedium?.copyWith(
                    color: context.colors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ),
      );
}
