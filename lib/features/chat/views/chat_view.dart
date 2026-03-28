import 'package:flutter/material.dart';
import 'package:weave/core/utils/build_context_extensions.dart';

/// Placeholder screen for the Matrix-powered Chat feature.
class ChatView extends StatelessWidget {
  const ChatView({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: const Text('Chat'),
          centerTitle: true,
        ),
        body: Center(
          child: Semantics(
            liveRegion: true,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  Icons.chat_bubble_outline,
                  size: 64,
                  color: context.colors.primary,
                  semanticLabel: 'Chat icon',
                ),
                const SizedBox(height: 16),
                Text(
                  'Chat coming soon',
                  style: context.textTheme.headlineSmall,
                ),
                const SizedBox(height: 8),
                Text(
                  'Matrix messaging will appear here.',
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
