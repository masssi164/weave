import 'package:weave/features/profile/domain/entities/user_profile.dart';

abstract interface class UserProfileRepository {
  Future<UserProfile?> loadProfile();

  Future<UserProfile> updateProfile(UserProfileUpdate update);
}
