import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';

abstract interface class NextcloudLoginLauncher {
  Future<void> launch(Uri loginUri);
}

class UrlLauncherNextcloudLoginLauncher implements NextcloudLoginLauncher {
  const UrlLauncherNextcloudLoginLauncher();

  @override
  Future<void> launch(Uri loginUri) async {
    try {
      final didLaunch = await launchUrl(
        loginUri,
        mode: LaunchMode.externalApplication,
      );
      if (!didLaunch) {
        throw const FilesFailure.unsupportedPlatform(
          'Unable to open the Nextcloud login page on this device.',
        );
      }
    } on FilesFailure {
      rethrow;
    } catch (error) {
      throw FilesFailure.unknown(
        'Unable to open the Nextcloud login page.',
        cause: error,
      );
    }
  }
}

final nextcloudLoginLauncherProvider = Provider<NextcloudLoginLauncher>((ref) {
  return const UrlLauncherNextcloudLoginLauncher();
});
