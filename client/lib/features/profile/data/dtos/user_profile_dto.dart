import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;

extension AuthenticatedUserResponseMapper on openapi.AuthenticatedUserResponse {
  UserProfile toDomain() {
    final requiredUsername = _requiredString(username, 'username');
    return UserProfile(
      userId: _requiredString(userId, 'userId'),
      username: requiredUsername,
      email: email,
      emailVerified: emailVerified ?? false,
      displayName: _optionalNonBlank(displayName) ?? requiredUsername,
      locale: _optionalNonBlank(locale) ?? 'en',
      timezone: _optionalNonBlank(timezone) ?? 'UTC',
      roles: roles ?? const <String>[],
      groups: groups ?? const <String>[],
    );
  }
}

extension ProductProfileResponseMapper on openapi.ProductProfileResponse {
  UserProfile toDomain() {
    final requiredUsername = _requiredString(username, 'username');
    return UserProfile(
      userId: _requiredString(userId, 'userId'),
      username: requiredUsername,
      email: email,
      emailVerified: emailVerified ?? false,
      displayName: _optionalNonBlank(displayName) ?? requiredUsername,
      locale: _optionalNonBlank(locale) ?? 'en',
      timezone: _optionalNonBlank(timezone) ?? 'UTC',
      roles: const <String>[],
      groups: const <String>[],
    );
  }
}

openapi.UpdateProductProfileRequest userProfileUpdateToOpenApi(
  UserProfileUpdate update,
) {
  return openapi.UpdateProductProfileRequest(
    displayName: update.displayName,
    locale: update.locale,
    timezone: update.timezone,
  );
}

String _requiredString(String? value, String field) {
  if (value != null) return value;
  throw AppFailure.unknown(
    'The Weave backend returned an invalid profile payload.',
    cause: '$field is required.',
  );
}

String? _optionalNonBlank(String? value) {
  return value != null && value.trim().isNotEmpty ? value : null;
}
