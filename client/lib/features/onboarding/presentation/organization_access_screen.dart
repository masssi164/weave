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
    context.push(uri.toString());
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
                      width: 80,
                      padding: const EdgeInsets.all(12),
                    ),
                  ),
                  const SizedBox(height: 20),
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
                  ExcludeSemantics(
                    child: Text(
                      l10n.setupOrganizationUriLabel,
                      style: theme.textTheme.labelLarge,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Semantics(
                    label: l10n.setupOrganizationUriLabel,
                    child: TextField(
                      controller: _controller,
                      keyboardType: TextInputType.url,
                      textInputAction: TextInputAction.done,
                      autofillHints: const [AutofillHints.url],
                      decoration: InputDecoration(
                        hintText: 'weave.example',
                        helper: Text(l10n.setupOrganizationUriHelper),
                        errorMaxLines: 4,
                        errorText: _error,
                      ),
                      onChanged: (_) {
                        if (_error != null) setState(() => _error = null);
                      },
                      onSubmitted: (_) => _continue(),
                    ),
                  ),
                  const SizedBox(height: 24),
                  AccessibleButton(
                    onPressed: _continue,
                    semanticLabel: l10n.setupOrganizationContinueButton,
                    child: Text(l10n.setupOrganizationContinueButton),
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
  final input = raw.trim();
  // Only a bare hostname (optionally with a port) gains an HTTPS scheme.
  // Explicit schemes, credentials, paths and invitation context remain intact.
  final bareHost = RegExp(
    r'^[a-zA-Z0-9.-]+(?::[0-9]{1,5})?/?$',
  ).hasMatch(input);
  final parsed = Uri.tryParse(bareHost ? 'https://$input' : input);
  if (parsed == null || !parsed.isAbsolute || parsed.host.isEmpty) return null;
  if (parsed.scheme != 'https' && parsed.scheme != 'weave') return null;
  if (parsed.userInfo.isNotEmpty || parsed.hasFragment) return null;

  // Invitation, app-link and pasted QR payloads keep their signed/support-safe
  // handoff context. MemberHandoffParser performs the strict final validation.
  if ((parsed.scheme == 'https' && parsed.path == AppRoutes.join) ||
      (parsed.scheme == 'weave' &&
          (parsed.host == 'join' || parsed.path == AppRoutes.join))) {
    return Uri.parse(normalizedJoinRouteLocation(parsed) ?? parsed.toString());
  }
  if (parsed.scheme != 'https' ||
      parsed.hasQuery ||
      parsed.hasFragment ||
      (parsed.path.isNotEmpty && parsed.path != '/') ||
      (parsed.hasPort && (parsed.port < 1 || parsed.port > 65535))) {
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
