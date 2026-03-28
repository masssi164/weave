import 'package:flutter/material.dart';
import 'package:weave/core/utils/build_context_extensions.dart';

/// Placeholder screen for the Nextcloud WebDAV Files feature.
class FilesView extends StatelessWidget {
  const FilesView({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: const Text('Files'),
          centerTitle: true,
        ),
        body: Center(
          child: Semantics(
            liveRegion: true,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  Icons.folder_open,
                  size: 64,
                  color: context.colors.primary,
                  semanticLabel: 'Files icon',
                ),
                const SizedBox(height: 16),
                Text(
                  'Files coming soon',
                  style: context.textTheme.headlineSmall,
                ),
                const SizedBox(height: 8),
                Text(
                  'Nextcloud file browsing will appear here.',
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
