import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';

class ProfileEditState {
  const ProfileEditState({
    this.isSaving = false,
    this.failure,
    this.savedProfile,
  });

  final bool isSaving;
  final AppFailure? failure;
  final UserProfile? savedProfile;

  bool get savedSuccessfully => savedProfile != null && failure == null;
}

class ProfileEditController extends Notifier<ProfileEditState> {
  @override
  ProfileEditState build() => const ProfileEditState();

  Future<void> save(UserProfileUpdate update) async {
    state = const ProfileEditState(isSaving: true);
    try {
      final profile = await ref
          .read(userProfileRepositoryProvider)
          .updateProfile(update);
      ref.invalidate(userProfileProvider);
      state = ProfileEditState(savedProfile: profile);
    } on AppFailure catch (failure) {
      state = ProfileEditState(failure: failure);
    } catch (error) {
      state = ProfileEditState(
        failure: AppFailure.unknown(
          'The Weave profile could not be saved right now.',
          cause: error,
        ),
      );
    }
  }

  void clearMessage() {
    if (!state.isSaving) {
      state = const ProfileEditState();
    }
  }
}

final profileEditControllerProvider =
    NotifierProvider<ProfileEditController, ProfileEditState>(
      ProfileEditController.new,
    );
