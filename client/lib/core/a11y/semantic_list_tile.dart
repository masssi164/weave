import 'package:flutter/material.dart';

/// A [ListTile] wrapper that merges semantics of its leading icon,
/// title, and subtitle into a single accessibility node.
///
/// The leading icon's own [semanticLabel] is excluded to prevent
/// duplicate announcements — the tile's title already conveys the meaning.
class AccessibleListTile extends StatelessWidget {
  const AccessibleListTile({
    super.key,
    this.leading,
    required this.title,
    this.subtitle,
    this.trailing,
    this.onTap,
  });

  /// Leading widget — typically an [Icon]. Its own semantic label is
  /// excluded so it does not duplicate the [title].
  final Widget? leading;

  /// Primary text of the tile.
  final Widget title;

  /// Optional secondary text.
  final Widget? subtitle;

  /// Optional trailing widget (e.g. a chevron).
  final Widget? trailing;

  /// Tap handler.
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return MergeSemantics(
      child: ListTile(
        leading: leading != null ? ExcludeSemantics(child: leading!) : null,
        title: title,
        subtitle: subtitle,
        trailing: trailing,
        onTap: onTap,
      ),
    );
  }
}
