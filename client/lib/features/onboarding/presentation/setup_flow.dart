import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/a11y/focus_utils.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/weave_logo.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// Member-facing organization access.
///
/// Email/app links, QR payloads and manually entered server URIs all become the
/// same support-safe `/join` handoff. Identity-provider and operator settings
/// deliberately do not belong in the member client.
class SetupFlow extends StatefulWidget {
  const SetupFlow({super.key});

  @override
  State<SetupFlow> createState() => _SetupFlowState();
}

class _SetupFlowState extends State<SetupFlow> {
  final _focusNode = FocusNode();
  final _controller = TextEditingController();
  String? _error;

  @override
  void initState() {
    super.initState();
    FocusUtils.requestFocusAfterFrame(_focusNode);
  }

  @override
  void dispose() {
    _focusNode.dispose();
    _controller.dispose();
    super.dispose();
  }

  void _continue() {
    final l10n = AppLocalizations.of(context);
    final uri = _organizationHandoffUri(_controller.text);
    if (uri == null) {
      setState(() => _error = l10n.setupOrganizationUriError);
      return;
    }
    setState(() => _error = null);
    context.go(uri.toString());
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const WeaveLogo(
              semanticLabel: 'Weave logo',
              width: 40,
              framed: false,
              excludeFromSemantics: true,
            ),
            const SizedBox(width: 12),
            Flexible(child: Text(l10n.setupTitle)),
          ],
        ),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.go(AppRoutes.welcome),
          tooltip: l10n.semanticBackButton,
        ),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 640),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Focus(
                    focusNode: _focusNode,
                    child: Semantics(
                      header: true,
                      child: Text(
                        l10n.setupMemberHandoffTitle,
                        style: theme.textTheme.headlineSmall,
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    l10n.setupMemberHandoffDescription,
                    style: theme.textTheme.bodyLarge?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 24),
                  TextField(
                    controller: _controller,
                    keyboardType: TextInputType.url,
                    textInputAction: TextInputAction.done,
                    autofillHints: const [AutofillHints.url],
                    decoration: InputDecoration(
                      prefixIcon: const Icon(Icons.qr_code_2),
                      labelText: l10n.setupOrganizationUriLabel,
                      hintText: 'https://weave.example',
                      helperText: l10n.setupOrganizationUriHelper,
                      errorText: _error,
                    ),
                    onChanged: (_) {
                      if (_error != null) setState(() => _error = null);
                    },
                    onSubmitted: (_) => _continue(),
                  ),
                  const SizedBox(height: 24),
                  AccessibleButton(
                    onPressed: _continue,
                    semanticLabel: l10n.setupOrganizationContinueButton,
                    child: Text(l10n.setupOrganizationContinueButton),
                  ),
                  const SizedBox(height: 16),
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: MergeSemantics(
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Icon(Icons.mail_outline),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Text(l10n.setupOrganizationAccessHelp),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

Uri? _organizationHandoffUri(String raw) {
  final parsed = Uri.tryParse(raw.trim());
  if (parsed == null || !parsed.isAbsolute || parsed.host.isEmpty) return null;
  if (parsed.scheme != 'https' && parsed.scheme != 'weave') return null;
  if (parsed.userInfo.isNotEmpty) return null;

  // Invitation, app-link and pasted QR payloads keep their signed/support-safe
  // handoff context. MemberHandoffParser performs the strict final validation.
  if ((parsed.scheme == 'https' && parsed.path == AppRoutes.join) ||
      (parsed.scheme == 'weave' &&
          (parsed.host == 'join' || parsed.path == AppRoutes.join))) {
    return parsed;
  }
  if (parsed.scheme != 'https' || parsed.hasQuery || parsed.hasFragment) {
    return null;
  }

  final origin = Uri(
    scheme: parsed.scheme,
    host: parsed.host,
    port: parsed.hasPort ? parsed.port : null,
  );
  final hostSlug = parsed.host
      .split('.')
      .first
      .replaceAll(RegExp('[^A-Za-z0-9_-]'), '-');
  return origin.replace(
    path: AppRoutes.join,
    queryParameters: {
      'handoff_ref': 'manual-${parsed.host.hashCode.abs()}',
      'org': hostSlug.length >= 2 ? hostSlug : 'weave',
      'workspace': 'default',
      'profile': 'organization-access',
      'run_id': 'manual-access',
    },
  );
}
