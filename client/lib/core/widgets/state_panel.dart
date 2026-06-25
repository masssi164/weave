import 'package:flutter/material.dart';
import 'package:weave/core/a11y/semantic_button.dart';

/// Visual and semantic variants for shared feature state panels.
enum StatePanelVariant { loading, empty, error, success }

/// A reusable, accessible state panel for loading, empty, error, success, and
/// recovery states.
///
/// The panel intentionally exposes one predictable live-region label composed
/// from [message], [guidance], and the optional action label. Decorative chrome
/// is excluded from semantics so screen-reader users hear the state and the
/// available recovery path, not duplicate icon names.
class StatePanel extends StatelessWidget {
  const StatePanel({
    super.key,
    required this.variant,
    required this.message,
    this.guidance,
    this.icon,
    this.actionLabel,
    this.onAction,
    this.semanticLabel,
    this.outlinedAction = false,
    this.liveRegion = true,
  });

  /// State category. This controls default icon and container color.
  final StatePanelVariant variant;

  /// Short localized state title.
  final String message;

  /// Optional localized explanation or next step.
  final String? guidance;

  /// Optional decorative icon. Defaults from [variant] when omitted.
  final IconData? icon;

  /// Optional localized recovery/action label.
  final String? actionLabel;

  /// Optional recovery/action callback.
  final VoidCallback? onAction;

  /// Optional full screen-reader label. When omitted, a label is composed from
  /// [message], [guidance], and [actionLabel].
  final String? semanticLabel;

  /// Renders the optional action as an outlined button when true.
  final bool outlinedAction;

  /// Whether assistive technologies should announce this panel as a live
  /// update. Keep enabled for transient loading/error states, but disable for
  /// stable success states that remain on screen while the user explores them.
  final bool liveRegion;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final tokens = _StatePanelTokens.from(theme, variant);
    final effectiveIcon = icon ?? tokens.icon;
    final effectiveSemanticLabel = semanticLabel ?? _composeSemanticLabel();
    final hasAction = actionLabel != null && onAction != null;

    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
      child: Center(
        child: Semantics(
          container: true,
          explicitChildNodes: true,
          liveRegion: liveRegion,
          label: effectiveSemanticLabel,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 360),
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    ExcludeSemantics(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          DecoratedBox(
                            decoration: BoxDecoration(
                              color: tokens.containerColor,
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: Padding(
                              padding: const EdgeInsets.all(12),
                              child: Icon(
                                effectiveIcon,
                                size: 28,
                                color: tokens.onContainerColor,
                              ),
                            ),
                          ),
                          if (variant == StatePanelVariant.loading) ...[
                            const SizedBox(height: 20),
                            const CircularProgressIndicator(),
                          ],
                          const SizedBox(height: 20),
                          Text(
                            message,
                            style: theme.textTheme.titleMedium,
                            textAlign: TextAlign.center,
                          ),
                          if (guidance != null) ...[
                            const SizedBox(height: 8),
                            Text(
                              guidance!,
                              style: theme.textTheme.bodyMedium?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                              textAlign: TextAlign.center,
                            ),
                          ],
                        ],
                      ),
                    ),
                    if (hasAction) ...[
                      const SizedBox(height: 24),
                      AccessibleButton(
                        onPressed: onAction,
                        semanticLabel: actionLabel!,
                        outlined: outlinedAction,
                        child: Text(actionLabel!),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  String _composeSemanticLabel() {
    final parts = <String>[
      message,
      if (guidance != null && guidance!.trim().isNotEmpty) guidance!,
      if (actionLabel != null && actionLabel!.trim().isNotEmpty)
        'Action: $actionLabel',
    ];

    return [
      for (var i = 0; i < parts.length; i++)
        if (i == parts.length - 1)
          parts[i].trim()
        else
          _withoutTerminalPunctuation(parts[i]),
    ].join('. ');
  }

  String _withoutTerminalPunctuation(String value) {
    return value.trim().replaceFirst(RegExp(r'[.!?…]+$'), '');
  }
}

class _StatePanelTokens {
  const _StatePanelTokens({
    required this.icon,
    required this.containerColor,
    required this.onContainerColor,
  });

  final IconData icon;
  final Color containerColor;
  final Color onContainerColor;

  factory _StatePanelTokens.from(ThemeData theme, StatePanelVariant variant) {
    final colors = theme.colorScheme;
    return switch (variant) {
      StatePanelVariant.loading => _StatePanelTokens(
        icon: Icons.hourglass_top_rounded,
        containerColor: colors.secondaryContainer,
        onContainerColor: colors.onSecondaryContainer,
      ),
      StatePanelVariant.empty => _StatePanelTokens(
        icon: Icons.inbox_outlined,
        containerColor: colors.surfaceContainerHighest,
        onContainerColor: colors.onSurfaceVariant,
      ),
      StatePanelVariant.error => _StatePanelTokens(
        icon: Icons.error_outline,
        containerColor: colors.errorContainer,
        onContainerColor: colors.onErrorContainer,
      ),
      StatePanelVariant.success => _StatePanelTokens(
        icon: Icons.check_circle_outline,
        containerColor: colors.tertiaryContainer,
        onContainerColor: colors.onTertiaryContainer,
      ),
    };
  }
}
