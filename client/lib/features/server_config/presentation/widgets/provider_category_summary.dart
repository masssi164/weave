import 'package:flutter/material.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class ProviderCategorySummary extends StatelessWidget {
  const ProviderCategorySummary({super.key, this.compact = false});

  final bool compact;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final categories = _categories(l10n);

    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: l10n.providerCategorySummarySemanticLabel,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: theme.colorScheme.secondaryContainer.withValues(alpha: 0.18),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: theme.colorScheme.outlineVariant),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Semantics(
                header: true,
                child: Text(
                  l10n.providerCategorySummaryTitle,
                  style: compact
                      ? theme.textTheme.titleMedium
                      : theme.textTheme.titleLarge,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                l10n.providerCategorySummaryDescription,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 12),
              for (final category in categories) ...[
                _ProviderCategoryRow(category: category),
                if (category != categories.last)
                  Divider(
                    height: compact ? 16 : 20,
                    color: theme.colorScheme.outlineVariant,
                  ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  List<_ProviderCategory> _categories(AppLocalizations l10n) {
    return [
      _ProviderCategory(
        title: l10n.providerCategoryIdentityTitle,
        state: l10n.providerCategoryStatusCurrentDefault,
        detail: l10n.providerCategoryIdentityDetail,
      ),
      _ProviderCategory(
        title: l10n.providerCategoryChatTitle,
        state: l10n.providerCategoryStatusCurrentDefault,
        detail: l10n.providerCategoryChatDetail,
      ),
      _ProviderCategory(
        title: l10n.providerCategoryFilesTitle,
        state: l10n.providerCategoryStatusCurrentDefault,
        detail: l10n.providerCategoryFilesDetail,
      ),
      _ProviderCategory(
        title: l10n.providerCategoryCalendarTitle,
        state: l10n.providerCategoryStatusCurrentDefault,
        detail: l10n.providerCategoryCalendarDetail,
      ),
      _ProviderCategory(
        title: l10n.providerCategoryBoardsTitle,
        state: l10n.providerCategoryStatusCurrentDefault,
        detail: l10n.providerCategoryBoardsDetail,
      ),
      _ProviderCategory(
        title: l10n.providerCategoryMeetingsTitle,
        state: l10n.providerCategoryStatusAdminSetupRequired,
        detail: l10n.providerCategoryMeetingsDetail,
      ),
      _ProviderCategory(
        title: l10n.providerCategoryDocumentsTitle,
        state: l10n.providerCategoryStatusAdminSetupRequired,
        detail: l10n.providerCategoryDocumentsDetail,
      ),
      _ProviderCategory(
        title: l10n.providerCategoryAgentRuntimeControlTitle,
        state: l10n.providerCategoryStatusDisabledByDefault,
        detail: l10n.providerCategoryAgentRuntimeControlDetail,
      ),
    ];
  }
}

class _ProviderCategoryRow extends StatelessWidget {
  const _ProviderCategoryRow({required this.category});

  final _ProviderCategory category;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return MergeSemantics(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Text(
                category.title,
                style: theme.textTheme.titleSmall?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              _CategoryStatePill(label: category.state),
            ],
          ),
          const SizedBox(height: 4),
          Text(category.detail, style: theme.textTheme.bodySmall),
        ],
      ),
    );
  }
}

class _CategoryStatePill extends StatelessWidget {
  const _CategoryStatePill({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return DecoratedBox(
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: theme.colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        child: Text(label, style: theme.textTheme.labelSmall),
      ),
    );
  }
}

class _ProviderCategory {
  const _ProviderCategory({
    required this.title,
    required this.state,
    required this.detail,
  });

  final String title;
  final String state;
  final String detail;
}
