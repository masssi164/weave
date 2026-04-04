import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';

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

    return uri;
  }

  ServiceEndpoints derive(Uri issuerUrl) {
    final labels = issuerUrl.host.split('.');
    final baseHost = labels.length >= 3
        ? labels.skip(1).join('.')
        : issuerUrl.host;
    final scheme = issuerUrl.scheme;

    return ServiceEndpoints(
      matrixHomeserverUrl: Uri.parse('$scheme://matrix.$baseHost'),
      nextcloudBaseUrl: Uri.parse('$scheme://files.$baseHost'),
    );
  }
}

@Riverpod(keepAlive: true)
ServiceEndpointDeriver serviceEndpointDeriver(Ref ref) {
  return ServiceEndpointDeriver();
}
