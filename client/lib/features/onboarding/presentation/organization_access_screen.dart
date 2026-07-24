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
class OrganizationAccessScreen extends StatefulWidget {
  const OrganizationAccessScreen({super.key});

  @override
  State<OrganizationAccessScreen> createState() =>
      _OrganizationAccessScreenState();
}

class _OrganizationAccessScreenState extends State<OrganizationAccessScreen> {
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
    final uri = _organizationAccessUri(_controller.text);
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
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 640),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Center(
                    child: WeaveLogo(
                      semanticLabel: l10n.semanticWeaveLogo,
                      width: 144,
                    ),
                  ),
                  const SizedBox(height: 28),
                  Focus(
                    focusNode: _focusNode,
                    child: Semantics(
                      header: true,
                      child: Text(
                        l10n.setupTitle,
                        style: theme.textTheme.headlineMedium,
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    l10n.setupMemberHandoffDescription,
                    style: theme.textTheme.bodyLarge?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                    textAlign: TextAlign.center,
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

Uri? _organizationAccessUri(String raw) {
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

  final organizationOrigin = Uri(
    scheme: parsed.scheme,
    host: parsed.host,
    port: parsed.hasPort ? parsed.port : null,
    path: '/',
  );
  return Uri(
    path: AppRoutes.join,
    queryParameters: {'organization_origin': organizationOrigin.toString()},
  );
}
