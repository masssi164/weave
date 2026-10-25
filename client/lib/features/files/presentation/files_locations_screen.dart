import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/app/domain/entities/member_space_access_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// An authorized navigation root, not another file store or permission source.
/// Opening a location creates an independent, route-bound directory controller.
class FilesLocationsScreen extends ConsumerWidget {
  const FilesLocationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final capabilities = ref.watch(workspaceCapabilitySnapshotProvider);
    Widget body;
    if (capabilities.isLoading) {
      body = _loading(l10n);
    } else if (capabilities.hasError ||
        capabilities.asData?.value.files.isReady != true) {
      body = _error(l10n, ref);
    } else {
      final spaces = ref.watch(weaveApiMemberSpacesProvider);
      // Never retain location names while the current member is revalidated.
      if (spaces.isLoading) {
        body = _loading(l10n);
      } else if (spaces.hasError || spaces.asData?.value == null) {
        body = _error(l10n, ref);
      } else {
        body = _locations(context, ref, l10n, spaces.asData!.value!);
      }
    }
    return CustomScrollView(
      key: const PageStorageKey('files-locations'),
      slivers: [
        SliverAppBar.large(title: Text(l10n.filesScreenTitle)),
        body,
      ],
    );
  }

  Widget _loading(AppLocalizations l10n) => SliverFillRemaining(
    hasScrollBody: false,
    child: LoadingState(
      message: l10n.filesLoadingLabel,
      hint: l10n.filesLoadingHint,
      icon: Icons.folder_outlined,
    ),
  );

  Widget _error(AppLocalizations l10n, WidgetRef ref) => SliverFillRemaining(
    hasScrollBody: false,
    child: ErrorState(
      message: l10n.filesLoadErrorTitle,
      guidance: l10n.filesErrorGuidance,
      retryLabel: l10n.retryButton,
      onRetry: () {
        ref
          ..invalidate(weaveApiWorkspaceCapabilitySnapshotProvider)
          ..invalidate(weaveApiMemberSpacesProvider);
      },
    ),
  );

  Widget _locations(
    BuildContext context,
    WidgetRef ref,
    AppLocalizations l10n,
    MemberSpaceAccessSnapshot access,
  ) {
    final spaces = access.visibleSpaceRefs.toList()
      ..sort((left, right) {
        final order = (access.displayNameFor(left) ?? '').toLowerCase().compareTo(
          (access.displayNameFor(right) ?? '').toLowerCase(),
        );
        return order == 0 ? left.compareTo(right) : order;
      });
    return SliverList.builder(
      itemCount: spaces.length + 1,
      itemBuilder: (context, index) {
        final spaceRef = index == 0 ? null : spaces[index - 1];
        final name = spaceRef == null ? null : access.displayNameFor(spaceRef);
        final ordinal = spaceRef == null
            ? null
            : access.displayOrdinalFor(spaceRef);
        final label = spaceRef == null
            ? l10n.filesPrivateSpace
            : name == null
            ? l10n.filesUnavailableSpace
            : ordinal == null
            ? name
            : l10n.filesSpaceOrdinal(name, ordinal);
        final enabled = spaceRef == null || name != null;
        void open() {
          final current = ref.read(weaveApiMemberSpacesProvider);
          if (current.isLoading ||
              current.hasError ||
              current.asData?.value == null ||
              ref.read(workspaceCapabilitySnapshotProvider).isLoading ||
              ref.read(workspaceCapabilitySnapshotProvider).asData?.value.files
                      .isReady !=
                  true ||
              (spaceRef != null &&
                  !current.asData!.value!.visibleSpaceRefs.contains(spaceRef))) {
            return;
          }
          context.push(AppRoutes.filesLocation('/', spaceRef: spaceRef));
        }

        return Semantics(
          key: ValueKey(('files-location', spaceRef)),
          identifier: spaceRef == null
              ? 'files-location-private'
              : 'files-location-space-$spaceRef',
          label: l10n.filesFolderSemantic(label),
          button: true,
          enabled: enabled,
          excludeSemantics: true,
          onTap: enabled ? open : null,
          child: ListTile(
            minTileHeight: 56,
            leading: const Icon(Icons.folder_outlined),
            title: Text(label),
            trailing: const Icon(Icons.chevron_right),
            enabled: enabled,
            onTap: enabled ? open : null,
          ),
        );
      },
    );
  }
}
