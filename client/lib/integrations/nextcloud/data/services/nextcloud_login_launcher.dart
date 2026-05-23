import 'package:url_launcher/url_launcher.dart';
import 'package:weave/integrations/nextcloud/domain/entities/nextcloud_failure.dart';

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
        throw const NextcloudFailure.unsupportedPlatform(
          'Unable to open the Nextcloud login page on this device.',
        );
      }
    } on NextcloudFailure {
      rethrow;
    } catch (error) {
      throw NextcloudFailure.unknown(
        'Unable to open the Nextcloud login page.',
        cause: error,
      );
    }
  }
}
