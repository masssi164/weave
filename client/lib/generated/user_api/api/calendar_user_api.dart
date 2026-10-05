//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarUserApi {
  CalendarUserApi([ApiClient? apiClient])
      : apiClient = apiClient ?? defaultApiClient;

  final ApiClient apiClient;

  /// Create an event with a stable retry identity
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] calendarId (required):
  ///
  /// * [String] idempotencyKey (required):
  ///
  /// * [CalendarEventWriteRequest] calendarEventWriteRequest (required):
  Future<Response> createCalendarEventWithHttpInfo(
    String calendarId,
    String idempotencyKey,
    CalendarEventWriteRequest calendarEventWriteRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/calendar/calendars/{calendarId}/events'
        .replaceAll('{calendarId}', calendarId);

    // ignore: prefer_final_locals
    Object? postBody = calendarEventWriteRequest;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    headerParams[r'Idempotency-Key'] = parameterToString(idempotencyKey);

    const contentTypes = <String>['application/json'];

    return apiClient.invokeAPI(
      path,
      'POST',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
    );
  }

  /// Create an event with a stable retry identity
  ///
  /// Parameters:
  ///
  /// * [String] calendarId (required):
  ///
  /// * [String] idempotencyKey (required):
  ///
  /// * [CalendarEventWriteRequest] calendarEventWriteRequest (required):
  Future<CalendarUserEvent?> createCalendarEvent(
    String calendarId,
    String idempotencyKey,
    CalendarEventWriteRequest calendarEventWriteRequest,
  ) async {
    final response = await createCalendarEventWithHttpInfo(
      calendarId,
      idempotencyKey,
      calendarEventWriteRequest,
    );
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
    // When a remote server returns no body with a status of 204, we shall not decode it.
    // At the time of writing this, `dart:convert` will throw an "Unexpected end of input"
    // FormatException when trying to decode an empty string.
    if (response.body.isNotEmpty &&
        response.statusCode != HttpStatus.noContent) {
      return await apiClient.deserializeAsync(
        await _decodeBodyBytes(response),
        'CalendarUserEvent',
      ) as CalendarUserEvent;
    }
    return null;
  }

  /// Delete an event with a strong version precondition
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] calendarId (required):
  ///
  /// * [String] eventId (required):
  ///
  /// * [String] ifMatch (required):
  ///   Exactly one strong version returned by this API.
  Future<Response> deleteCalendarEventWithHttpInfo(
    String calendarId,
    String eventId,
    String ifMatch,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/calendar/calendars/{calendarId}/events/{eventId}'
        .replaceAll('{calendarId}', calendarId)
        .replaceAll('{eventId}', eventId);

    // ignore: prefer_final_locals
    Object? postBody;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    headerParams[r'If-Match'] = parameterToString(ifMatch);

    const contentTypes = <String>[];

    return apiClient.invokeAPI(
      path,
      'DELETE',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
    );
  }

  /// Delete an event with a strong version precondition
  ///
  /// Parameters:
  ///
  /// * [String] calendarId (required):
  ///
  /// * [String] eventId (required):
  ///
  /// * [String] ifMatch (required):
  ///   Exactly one strong version returned by this API.
  Future<void> deleteCalendarEvent(
    String calendarId,
    String eventId,
    String ifMatch,
  ) async {
    final response = await deleteCalendarEventWithHttpInfo(
      calendarId,
      eventId,
      ifMatch,
    );
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
  }

  /// Read a stable Weave Calendar event
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] calendarId (required):
  ///
  /// * [String] eventId (required):
  Future<Response> getCalendarEventWithHttpInfo(
    String calendarId,
    String eventId,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/calendar/calendars/{calendarId}/events/{eventId}'
        .replaceAll('{calendarId}', calendarId)
        .replaceAll('{eventId}', eventId);

    // ignore: prefer_final_locals
    Object? postBody;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    const contentTypes = <String>[];

    return apiClient.invokeAPI(
      path,
      'GET',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
    );
  }

  /// Read a stable Weave Calendar event
  ///
  /// Parameters:
  ///
  /// * [String] calendarId (required):
  ///
  /// * [String] eventId (required):
  Future<CalendarUserEvent?> getCalendarEvent(
    String calendarId,
    String eventId,
  ) async {
    final response = await getCalendarEventWithHttpInfo(
      calendarId,
      eventId,
    );
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
    // When a remote server returns no body with a status of 204, we shall not decode it.
    // At the time of writing this, `dart:convert` will throw an "Unexpected end of input"
    // FormatException when trying to decode an empty string.
    if (response.body.isNotEmpty &&
        response.statusCode != HttpStatus.noContent) {
      return await apiClient.deserializeAsync(
        await _decodeBodyBytes(response),
        'CalendarUserEvent',
      ) as CalendarUserEvent;
    }
    return null;
  }

  /// Discover authorized workspace, team and channel calendars
  ///
  /// Note: This method returns the HTTP [Response].
  Future<Response> listUserCalendarsWithHttpInfo() async {
    // ignore: prefer_const_declarations
    final path = r'/api/calendar/calendars';

    // ignore: prefer_final_locals
    Object? postBody;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    const contentTypes = <String>[];

    return apiClient.invokeAPI(
      path,
      'GET',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
    );
  }

  /// Discover authorized workspace, team and channel calendars
  Future<CalendarUserCalendars?> listUserCalendars() async {
    final response = await listUserCalendarsWithHttpInfo();
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
    // When a remote server returns no body with a status of 204, we shall not decode it.
    // At the time of writing this, `dart:convert` will throw an "Unexpected end of input"
    // FormatException when trying to decode an empty string.
    if (response.body.isNotEmpty &&
        response.statusCode != HttpStatus.noContent) {
      return await apiClient.deserializeAsync(
        await _decodeBodyBytes(response),
        'CalendarUserCalendars',
      ) as CalendarUserCalendars;
    }
    return null;
  }

  /// Query a bounded Calendar agenda with an explicit evaluation timezone
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] calendarId (required):
  ///
  /// * [DateTime] from (required):
  ///
  /// * [DateTime] to (required):
  ///
  /// * [String] evaluationTimeZone (required):
  ///   IANA timezone for DATE/FLOATING occurrence evaluation; stored intent is unchanged.
  Future<Response> queryCalendarAgendaWithHttpInfo(
    String calendarId,
    DateTime from,
    DateTime to,
    String evaluationTimeZone,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/calendar/calendars/{calendarId}/events'
        .replaceAll('{calendarId}', calendarId);

    // ignore: prefer_final_locals
    Object? postBody;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    queryParams.addAll(_queryParams('', 'from', from));
    queryParams.addAll(_queryParams('', 'to', to));
    queryParams
        .addAll(_queryParams('', 'evaluationTimeZone', evaluationTimeZone));

    const contentTypes = <String>[];

    return apiClient.invokeAPI(
      path,
      'GET',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
    );
  }

  /// Query a bounded Calendar agenda with an explicit evaluation timezone
  ///
  /// Parameters:
  ///
  /// * [String] calendarId (required):
  ///
  /// * [DateTime] from (required):
  ///
  /// * [DateTime] to (required):
  ///
  /// * [String] evaluationTimeZone (required):
  ///   IANA timezone for DATE/FLOATING occurrence evaluation; stored intent is unchanged.
  Future<CalendarUserAgenda?> queryCalendarAgenda(
    String calendarId,
    DateTime from,
    DateTime to,
    String evaluationTimeZone,
  ) async {
    final response = await queryCalendarAgendaWithHttpInfo(
      calendarId,
      from,
      to,
      evaluationTimeZone,
    );
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
    // When a remote server returns no body with a status of 204, we shall not decode it.
    // At the time of writing this, `dart:convert` will throw an "Unexpected end of input"
    // FormatException when trying to decode an empty string.
    if (response.body.isNotEmpty &&
        response.statusCode != HttpStatus.noContent) {
      return await apiClient.deserializeAsync(
        await _decodeBodyBytes(response),
        'CalendarUserAgenda',
      ) as CalendarUserAgenda;
    }
    return null;
  }

  /// Replace supported event content with a strong version precondition
  ///
  /// Note: This method returns the HTTP [Response].
  ///
  /// Parameters:
  ///
  /// * [String] calendarId (required):
  ///
  /// * [String] eventId (required):
  ///
  /// * [String] ifMatch (required):
  ///   Exactly one strong version returned by this API; wildcard and weak versions are rejected.
  ///
  /// * [CalendarEventWriteRequest] calendarEventWriteRequest (required):
  Future<Response> updateCalendarEventWithHttpInfo(
    String calendarId,
    String eventId,
    String ifMatch,
    CalendarEventWriteRequest calendarEventWriteRequest,
  ) async {
    // ignore: prefer_const_declarations
    final path = r'/api/calendar/calendars/{calendarId}/events/{eventId}'
        .replaceAll('{calendarId}', calendarId)
        .replaceAll('{eventId}', eventId);

    // ignore: prefer_final_locals
    Object? postBody = calendarEventWriteRequest;

    final queryParams = <QueryParam>[];
    final headerParams = <String, String>{};
    final formParams = <String, String>{};

    headerParams[r'If-Match'] = parameterToString(ifMatch);

    const contentTypes = <String>['application/json'];

    return apiClient.invokeAPI(
      path,
      'PUT',
      queryParams,
      postBody,
      headerParams,
      formParams,
      contentTypes.isEmpty ? null : contentTypes.first,
    );
  }

  /// Replace supported event content with a strong version precondition
  ///
  /// Parameters:
  ///
  /// * [String] calendarId (required):
  ///
  /// * [String] eventId (required):
  ///
  /// * [String] ifMatch (required):
  ///   Exactly one strong version returned by this API; wildcard and weak versions are rejected.
  ///
  /// * [CalendarEventWriteRequest] calendarEventWriteRequest (required):
  Future<CalendarUserEvent?> updateCalendarEvent(
    String calendarId,
    String eventId,
    String ifMatch,
    CalendarEventWriteRequest calendarEventWriteRequest,
  ) async {
    final response = await updateCalendarEventWithHttpInfo(
      calendarId,
      eventId,
      ifMatch,
      calendarEventWriteRequest,
    );
    if (response.statusCode >= HttpStatus.badRequest) {
      throw ApiException(response.statusCode, await _decodeBodyBytes(response));
    }
    // When a remote server returns no body with a status of 204, we shall not decode it.
    // At the time of writing this, `dart:convert` will throw an "Unexpected end of input"
    // FormatException when trying to decode an empty string.
    if (response.body.isNotEmpty &&
        response.statusCode != HttpStatus.noContent) {
      return await apiClient.deserializeAsync(
        await _decodeBodyBytes(response),
        'CalendarUserEvent',
      ) as CalendarUserEvent;
    }
    return null;
  }
}
