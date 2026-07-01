import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/profile_edit_controller.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class ProfileSummaryCard extends ConsumerWidget {
  const ProfileSummaryCard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final profile = ref.watch(userProfileProvider);

    return Card(
      elevation: 0,
      color: theme.colorScheme.surfaceContainerLow,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24),
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: profile.when(
          loading: () => LoadingState(message: l10n.loadingLabel),
          error: (error, _) => ErrorState(
            message: l10n.profileLoadFailure,
            retryLabel: l10n.retryButton,
            onRetry: () => ref.invalidate(userProfileProvider),
          ),
          data: (profile) => profile == null
              ? Text(
                  l10n.profileSignedOutMessage,
                  style: theme.textTheme.bodyMedium,
                )
              : _ProfileDetails(profile: profile),
        ),
      ),
    );
  }
}

class _ProfileDetails extends StatelessWidget {
  const _ProfileDetails({required this.profile});

  final UserProfile profile;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final roles = profile.roles.isEmpty ? '—' : profile.roles.join(', ');
    final groups = profile.groups.isEmpty ? '—' : profile.groups.join(', ');

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(l10n.profileSectionTitle, style: theme.textTheme.titleLarge),
        const SizedBox(height: 8),
        Text(
          l10n.profileSectionDescription,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 20),
        MergeSemantics(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _ProfileRow(
                label: l10n.profileDisplayNameLabel,
                value: profile.displayName,
              ),
              _ProfileRow(
                label: l10n.profileUsernameLabel,
                value: profile.username,
              ),
              _ProfileRow(
                label: l10n.profileEmailLabel,
                value: profile.email ?? '—',
              ),
              _ProfileRow(
                label: l10n.profileEmailVerifiedLabel,
                value: profile.emailVerified
                    ? l10n.profileEmailVerifiedYes
                    : l10n.profileEmailVerifiedNo,
              ),
              _ProfileRow(
                label: l10n.profileLocaleLabel,
                value: _languageLabel(context, profile.locale),
              ),
              _ProfileRow(
                label: l10n.profileTimezoneLabel,
                value: profile.timezone,
              ),
              _ProfileRow(label: l10n.profileRolesLabel, value: roles),
              _ProfileRow(label: l10n.profileGroupsLabel, value: groups),
            ],
          ),
        ),
        const SizedBox(height: 16),
        _ProfileEditForm(profile: profile),
      ],
    );
  }
}

class _ProfileEditForm extends ConsumerStatefulWidget {
  const _ProfileEditForm({required this.profile});

  final UserProfile profile;

  @override
  ConsumerState<_ProfileEditForm> createState() => _ProfileEditFormState();
}

class _ProfileEditFormState extends ConsumerState<_ProfileEditForm> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _displayNameController;
  late final TextEditingController _timezoneController;
  late String _selectedLocale;

  @override
  void initState() {
    super.initState();
    _displayNameController = TextEditingController(
      text: widget.profile.displayName,
    );
    _selectedLocale = _supportedLocale(widget.profile.locale);
    _timezoneController = TextEditingController(text: widget.profile.timezone);
  }

  @override
  void didUpdateWidget(covariant _ProfileEditForm oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.profile.userId != widget.profile.userId) {
      _displayNameController.text = widget.profile.displayName;
      _selectedLocale = _supportedLocale(widget.profile.locale);
      _timezoneController.text = widget.profile.timezone;
    }
  }

  @override
  void dispose() {
    _displayNameController.dispose();
    _timezoneController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final editState = ref.watch(profileEditControllerProvider);
    final message =
        editState.failure?.message ??
        (editState.savedSuccessfully ? l10n.profileEditSavedMessage : null);
    final messageColor = editState.failure == null
        ? theme.colorScheme.primary
        : theme.colorScheme.error;

    return Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l10n.profileEditSectionTitle,
            style: theme.textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          Text(
            l10n.profileEditSectionDescription,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: _displayNameController,
            enabled: !editState.isSaving,
            decoration: InputDecoration(
              labelText: l10n.profileDisplayNameLabel,
              helperText: l10n.profileDisplayNameHelper,
            ),
            textInputAction: TextInputAction.next,
            validator: (value) =>
                _required(value) ? null : l10n.profileEditRequiredFieldError,
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            initialValue: _selectedLocale,
            decoration: InputDecoration(
              labelText: l10n.profileLocaleLabel,
              helperText: _languageHelper(context),
            ),
            items: [
              for (final locale in _supportedLocales)
                DropdownMenuItem<String>(
                  value: locale,
                  child: Text(_languageLabel(context, locale)),
                ),
            ],
            onChanged: editState.isSaving
                ? null
                : (value) {
                    if (value == null) {
                      return;
                    }
                    setState(() {
                      _selectedLocale = value;
                    });
                  },
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: _timezoneController,
            enabled: !editState.isSaving,
            decoration: InputDecoration(
              labelText: l10n.profileTimezoneLabel,
              helperText: l10n.profileTimezoneHelper,
            ),
            textInputAction: TextInputAction.done,
            onFieldSubmitted: (_) => _submit(),
            validator: (value) =>
                _required(value) ? null : l10n.profileEditRequiredFieldError,
          ),
          const SizedBox(height: 16),
          AccessibleButton(
            onPressed: editState.isSaving ? null : _submit,
            semanticLabel: l10n.profileEditSaveButton,
            child: Text(
              editState.isSaving
                  ? l10n.profileEditSavingButton
                  : l10n.profileEditSaveButton,
            ),
          ),
          if (message != null) ...[
            const SizedBox(height: 12),
            Semantics(
              liveRegion: true,
              child: Text(
                message,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: messageColor,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  bool _required(String? value) => value != null && value.trim().isNotEmpty;

  void _submit() {
    if (!(_formKey.currentState?.validate() ?? false)) {
      return;
    }
    ref
        .read(profileEditControllerProvider.notifier)
        .save(
          UserProfileUpdate(
            displayName: _displayNameController.text.trim(),
            locale: _selectedLocale,
            timezone: _timezoneController.text.trim(),
          ),
        );
  }
}

const _supportedLocales = ['en', 'de'];

String _supportedLocale(String locale) {
  return _supportedLocales.contains(locale) ? locale : 'en';
}

String _languageLabel(BuildContext context, String locale) {
  final isGerman = Localizations.localeOf(context).languageCode == 'de';
  return switch (locale) {
    'de' => isGerman ? 'Deutsch' : 'German',
    'en' => isGerman ? 'Englisch' : 'English',
    _ => locale,
  };
}

String _languageHelper(BuildContext context) {
  final isGerman = Localizations.localeOf(context).languageCode == 'de';
  return isGerman
      ? 'Wähle die Sprache, die Weave für dein Profil verwenden soll.'
      : 'Choose the language Weave should use for your profile.';
}

class _ProfileRow extends StatelessWidget {
  const _ProfileRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 140,
            child: Text(label, style: theme.textTheme.labelLarge),
          ),
          const SizedBox(width: 12),
          Expanded(child: Text(value, style: theme.textTheme.bodyMedium)),
        ],
      ),
    );
  }
}
