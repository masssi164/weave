import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';
import 'package:weave/features/server_config/presentation/providers/saved_server_configuration_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

typedef WorkspaceSetupLauncher =
    Future<bool> Function(Uri url, {required LaunchMode mode});

final workspaceSetupLauncherProvider = Provider<WorkspaceSetupLauncher>(
  (ref) =>
      (url, {required mode}) => launchUrl(url, mode: mode),
);

/// Opens first-party administration; current Server authorization still applies.
class WorkspaceSetupAction extends ConsumerWidget {
  const WorkspaceSetupAction({super.key, required this.capability});

  final WorkspaceCapabilityState capability;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (!capability.memberSpaceAccessUnavailable) {
      return const SizedBox.shrink();
    }
    final admin = ref
        .watch(weaveApiWorkspaceCapabilitySnapshotProvider)
        .asData
        ?.value
        ?.adminControlPlane;
    if (admin?.isReady != true ||
        admin?.policyState != WorkspaceCapabilityPolicyState.allowed) {
      return const SizedBox.shrink();
    }
    final configuration = ref
        .watch(savedServerConfigurationProvider)
        .asData
        ?.value;
    final origin = configuration?.organizationOrigin;
    if (origin == null || configuration?.validatedManifest == null) {
      return const SizedBox.shrink();
    }
    Uri url;
    try {
      final access = const OrganizationAccessParser().parse(origin);
      if (access.handoff != null ||
          origin.scheme != 'https' ||
          (origin.path.isNotEmpty && origin.path != '/')) {
        return const SizedBox.shrink();
      }
      url = access.organizationOrigin.resolve('/admin-console/');
    } on AppFailure {
      return const SizedBox.shrink();
    }
    final l10n = AppLocalizations.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: AccessibleButton(
        outlined: true,
        semanticLabel: l10n.workspaceSetupOpenAction,
        onPressed: () async {
          var opened = false;
          try {
            opened = await ref.read(workspaceSetupLauncherProvider)(
              url,
              mode: LaunchMode.externalApplication,
            );
          } catch (_) {
            // Platform launch failures must not expose private platform details.
          }
          if (!opened && context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(l10n.workspaceSetupOpenFailed)),
            );
          }
        },
        child: Text(l10n.workspaceSetupOpenAction),
      ),
    );
  }
}
