import 'package:flutter/material.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class HelpScreen extends StatelessWidget {
  const HelpScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final sections = _HelpSection.from(l10n);

    return Scaffold(
      body: FocusTraversalGroup(
        child: CustomScrollView(
          slivers: [
            SliverAppBar.large(title: Text(l10n.helpScreenTitle)),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 32),
              sliver: SliverList.separated(
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return _HelpIntroCard(
                      title: l10n.helpHandbookTitle,
                      description: l10n.helpHandbookDescription,
                    );
                  }

                  if (index == 1) {
                    return _EmbeddedManualCard(
                      title: l10n.helpEmbeddedManualTitle,
                      description: l10n.helpEmbeddedManualDescription,
                      pathLabel: l10n.helpEmbeddedManualPathLabel,
                      path: 'docs/user-handbook.md',
                      permissionLabel: l10n.helpEmbeddedManualPermissionLabel,
                      unavailableLabel: l10n.helpEmbeddedManualUnavailableLabel,
                    );
                  }

                  return _HelpSectionCard(section: sections[index - 2]);
                },
                separatorBuilder: (context, index) =>
                    const SizedBox(height: 16),
                itemCount: sections.length + 2,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _HelpIntroCard extends StatelessWidget {
  const _HelpIntroCard({required this.title, required this.description});

  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Card(
      elevation: 0,
      color: theme.colorScheme.primaryContainer,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Semantics(
              container: true,
              header: true,
              child: Text(
                title,
                style: theme.textTheme.headlineSmall?.copyWith(
                  color: theme.colorScheme.onPrimaryContainer,
                ),
              ),
            ),
            const SizedBox(height: 12),
            Text(
              description,
              style: theme.textTheme.bodyLarge?.copyWith(
                color: theme.colorScheme.onPrimaryContainer,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmbeddedManualCard extends StatelessWidget {
  const _EmbeddedManualCard({
    required this.title,
    required this.description,
    required this.pathLabel,
    required this.path,
    required this.permissionLabel,
    required this.unavailableLabel,
  });

  final String title;
  final String description;
  final String pathLabel;
  final String path;
  final String permissionLabel;
  final String unavailableLabel;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Semantics(
      container: true,
      label: '$title. $permissionLabel',
      child: Card(
        elevation: 0,
        color: theme.colorScheme.surfaceContainerLowest,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
          side: BorderSide(color: theme.colorScheme.outlineVariant),
        ),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ExcludeSemantics(
                    child: Icon(
                      Icons.article_outlined,
                      color: theme.colorScheme.primary,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Semantics(
                      container: true,
                      header: true,
                      child: Text(title, style: theme.textTheme.titleLarge),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Text(
                description,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  height: 1.45,
                ),
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  Semantics(
                    label: '$pathLabel $path',
                    child: Chip(
                      avatar: const Icon(Icons.folder_zip_outlined, size: 18),
                      label: Text('$pathLabel $path'),
                    ),
                  ),
                  Semantics(
                    label: permissionLabel,
                    child: Chip(
                      avatar: const Icon(Icons.lock_outline, size: 18),
                      label: Text(permissionLabel),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                unavailableLabel,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _HelpSectionCard extends StatelessWidget {
  const _HelpSectionCard({required this.section});

  final _HelpSection section;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Semantics(
      container: true,
      child: Card(
        elevation: 0,
        color: theme.colorScheme.surfaceContainerLow,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
          side: BorderSide(color: theme.colorScheme.outlineVariant),
        ),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ExcludeSemantics(
                    child: Icon(section.icon, color: theme.colorScheme.primary),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Semantics(
                      container: true,
                      header: true,
                      child: Text(
                        section.title,
                        style: theme.textTheme.titleLarge,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Text(
                section.body,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  height: 1.45,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _HelpSection {
  const _HelpSection({
    required this.icon,
    required this.title,
    required this.body,
  });

  final IconData icon;
  final String title;
  final String body;

  static List<_HelpSection> from(AppLocalizations l10n) => [
    _HelpSection(
      icon: Icons.hub_outlined,
      title: l10n.helpWhatIsWeaveTitle,
      body: l10n.helpWhatIsWeaveBody,
    ),
    _HelpSection(
      icon: Icons.login_outlined,
      title: l10n.helpSignInTitle,
      body: l10n.helpSignInBody,
    ),
    _HelpSection(
      icon: Icons.chat_bubble_outline,
      title: l10n.helpChatTitle,
      body: l10n.helpChatBody,
    ),
    _HelpSection(
      icon: Icons.folder_outlined,
      title: l10n.helpFilesTitle,
      body: l10n.helpFilesBody,
    ),
    _HelpSection(
      icon: Icons.settings_outlined,
      title: l10n.helpSettingsTitle,
      body: l10n.helpSettingsBody,
    ),
    _HelpSection(
      icon: Icons.event_note_outlined,
      title: l10n.helpCalendarBoardsTitle,
      body: l10n.helpCalendarBoardsBody,
    ),
    _HelpSection(
      icon: Icons.support_outlined,
      title: l10n.helpTroubleshootingTitle,
      body: l10n.helpTroubleshootingBody,
    ),
    _HelpSection(
      icon: Icons.security_outlined,
      title: l10n.helpPrivacySecurityTitle,
      body: l10n.helpPrivacySecurityBody,
    ),
  ];
}
