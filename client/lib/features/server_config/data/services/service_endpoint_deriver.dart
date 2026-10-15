import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/core/failures/app_failure.dart';

part 'service_endpoint_deriver.g.dart';

class ServiceEndpointDeriver {
  Uri parseIssuerUrl(String rawValue) {
    final trimmed = rawValue.trim();
    final uri = Uri.tryParse(trimmed);

    if (uri == null || !uri.isAbsolute || uri.host.isEmpty) {
      throw const AppFailure.validation('Enter a valid absolute issuer URL.');
    }

    if (uri.scheme != 'http' && uri.scheme != 'https') {
      throw const AppFailure.validation(
        'The issuer URL must use HTTP or HTTPS.',
      );
    }

    if (uri.hasQuery || uri.hasFragment) {
      throw const AppFailure.validation(
        'The issuer URL must not include a query or fragment.',
      );
    }

    return uri;
  }

  Uri parseServiceUrl(String rawValue, {required String fieldName}) {
    final trimmed = rawValue.trim();
    final uri = Uri.tryParse(trimmed);

    if (uri == null || !uri.isAbsolute || uri.host.isEmpty) {
      throw AppFailure.validation('Enter a valid absolute URL for $fieldName.');
    }

    if (uri.scheme != 'http' && uri.scheme != 'https') {
      throw AppFailure.validation('$fieldName must use HTTP or HTTPS.');
    }

    if (uri.hasQuery || uri.hasFragment) {
      throw AppFailure.validation(
        '$fieldName must not include a query or fragment.',
      );
    }
    final pathSegments = uri.pathSegments
        .where((segment) => segment.trim().isNotEmpty)
        .toList(growable: false);
    if (pathSegments.isEmpty || pathSegments.last != 'api') {
      throw AppFailure.validation('$fieldName must end in /api.');
    }

    return uri;
  }

  Uri parseMatrixHomeserverUrl(String rawValue) {
    final uri = Uri.tryParse(rawValue.trim());
    if (uri == null ||
        !uri.isAbsolute ||
        uri.host.isEmpty ||
        !const {'http', 'https'}.contains(uri.scheme) ||
        (uri.path.isNotEmpty && uri.path != '/') ||
        uri.hasQuery ||
        uri.hasFragment ||
        uri.userInfo.isNotEmpty) {
      throw const AppFailure.validation(
        'Enter the Weave Matrix Client-Server origin using HTTP or HTTPS.',
      );
    }
    return uri.replace(path: '');
  }
}

@Riverpod(keepAlive: true)
ServiceEndpointDeriver serviceEndpointDeriver(Ref ref) {
  return ServiceEndpointDeriver();
}
