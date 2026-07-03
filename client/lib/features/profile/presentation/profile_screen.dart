import 'package:flutter/material.dart';
import 'package:weave/features/profile/presentation/widgets/profile_summary_card.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return CustomScrollView(
      slivers: [
        SliverAppBar.large(title: Text(l10n.profileScreenTitle)),
        const SliverPadding(
          padding: EdgeInsets.all(24),
          sliver: SliverToBoxAdapter(child: ProfileSummaryCard()),
        ),
      ],
    );
  }
}
