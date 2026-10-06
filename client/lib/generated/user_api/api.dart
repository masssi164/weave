//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

library weave_user_api;

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:collection/collection.dart';
import 'package:http/http.dart';
import 'package:intl/intl.dart';
import 'package:meta/meta.dart';

part 'api_client.dart';
part 'api_helper.dart';
part 'api_exception.dart';
part 'auth/authentication.dart';
part 'auth/api_key_auth.dart';
part 'auth/oauth.dart';
part 'auth/http_basic_auth.dart';
part 'auth/http_bearer_auth.dart';

part 'api/boards_workspace_api.dart';
part 'api/calendar_api.dart';
part 'api/calendar_user_api.dart';
part 'api/chat_domain_api.dart';
part 'api/dev_ops_facade_api.dart';
part 'api/files_api.dart';
part 'api/files_user_api.dart';
part 'api/guest_access_api.dart';
part 'api/health_controller_api.dart';
part 'api/identity_api.dart';
part 'api/identity_session_api.dart';
part 'api/interop_api.dart';
part 'api/office_facade_api.dart';
part 'api/platform_api.dart';
part 'api/profile_api.dart';
part 'api/workspace_api.dart';

part 'model/api_error_response.dart';
part 'model/authenticated_user_response.dart';
part 'model/board.dart';
part 'model/board_column.dart';
part 'model/board_provider_capabilities.dart';
part 'model/boards_create_task_request.dart';
part 'model/boards_link_decision_request.dart';
part 'model/boards_move_task_request.dart';
part 'model/boards_sync_metadata_response.dart';
part 'model/boards_update_task_status_request.dart';
part 'model/boards_workspace_response.dart';
part 'model/calendar_access_model_response.dart';
part 'model/calendar_access_policy_response.dart';
part 'model/calendar_event_attendee.dart';
part 'model/calendar_event_override.dart';
part 'model/calendar_event_recurrence.dart';
part 'model/calendar_event_write_request.dart';
part 'model/calendar_scope_response.dart';
part 'model/calendar_scopes_response.dart';
part 'model/calendar_time_value.dart';
part 'model/calendar_user_agenda.dart';
part 'model/calendar_user_calendar.dart';
part 'model/calendar_user_calendars.dart';
part 'model/calendar_user_event.dart';
part 'model/calendar_user_occurrence.dart';
part 'model/calendar_user_scope.dart';
part 'model/canonical_bridge_event_response.dart';
part 'model/capability_response.dart';
part 'model/chat_history_policy.dart';
part 'model/chat_provider_mapping_record.dart';
part 'model/chat_readiness.dart';
part 'model/client_access_credential_lifecycle_response.dart';
part 'model/client_access_discovery_response.dart';
part 'model/client_access_protocol_surface_response.dart';
part 'model/connector_boundary_summary_response.dart';
part 'model/consent_capability_registry_response.dart';
part 'model/decision_ledger_create_request.dart';
part 'model/decision_ledger_evidence_posture_response.dart';
part 'model/decision_ledger_record_response.dart';
part 'model/decision_ledger_records_response.dart';
part 'model/decision_ledger_reference_request.dart';
part 'model/decision_ledger_reference_response.dart';
part 'model/devops_issue_summary_response.dart';
part 'model/devops_job_summary_response.dart';
part 'model/devops_merge_request_summary_response.dart';
part 'model/devops_pipeline_summary_response.dart';
part 'model/devops_release_summary_response.dart';
part 'model/devops_summary_response.dart';
part 'model/diagnostic_check.dart';
part 'model/diagnostic_status.dart';
part 'model/domain_capability.dart';
part 'model/e2ee_status.dart';
part 'model/external_connection_response.dart';
part 'model/files_user_create_folder_request.dart';
part 'model/files_user_item_response.dart';
part 'model/files_user_list_response.dart';
part 'model/guest_access_contract_response.dart';
part 'model/guest_invitation_request.dart';
part 'model/guest_invitation_response.dart';
part 'model/health_response.dart';
part 'model/identity_session_reconcile_response.dart';
part 'model/interop_status_response.dart';
part 'model/linked_source_project_response.dart';
part 'model/matrix_backend_boundary.dart';
part 'model/matrix_status.dart';
part 'model/meeting_capsule_create_request.dart';
part 'model/meeting_capsule_response.dart';
part 'model/meeting_capsules_response.dart';
part 'model/module_sync_status_response.dart';
part 'model/office_capabilities_response.dart';
part 'model/office_capability_flags_response.dart';
part 'model/office_launch_request.dart';
part 'model/office_launch_response.dart';
part 'model/office_lock_session_readiness_response.dart';
part 'model/office_permission_model_response.dart';
part 'model/office_provider_candidate_response.dart';
part 'model/oidc.dart';
part 'model/organization_manifest_response.dart';
part 'model/platform_config_response.dart';
part 'model/platform_status_response.dart';
part 'model/product_profile_response.dart';
part 'model/profile_readiness_response.dart';
part 'model/protocols.dart';
part 'model/provider_ref.dart';
part 'model/provider_status_response.dart';
part 'model/recovery_action.dart';
part 'model/slack_o_auth_callback_request.dart';
part 'model/slack_o_auth_callback_response.dart';
part 'model/slack_outbound_message_request.dart';
part 'model/slack_outbound_message_response.dart';
part 'model/slack_status_response.dart';
part 'model/source_repository_response.dart';
part 'model/support_bundle_policy_response.dart';
part 'model/task_item.dart';
part 'model/teams_contract_response.dart';
part 'model/update_product_profile_request.dart';
part 'model/weave_project.dart';
part 'model/workspace_capabilities_response.dart';
part 'model/workspace_capability_status_response.dart';
part 'model/workspace_home_action_response.dart';
part 'model/workspace_home_recent_activity_response.dart';
part 'model/workspace_home_response.dart';
part 'model/workspace_home_section_response.dart';

/// An [ApiClient] instance that uses the default values obtained from
/// the OpenAPI specification file.
var defaultApiClient = ApiClient();

const _delimiters = {'csv': ',', 'ssv': ' ', 'tsv': '\t', 'pipes': '|'};
const _dateEpochMarker = 'epoch';
const _deepEquality = DeepCollectionEquality();
final _dateFormatter = DateFormat('yyyy-MM-dd');
final _regList = RegExp(r'^List<(.*)>$');
final _regSet = RegExp(r'^Set<(.*)>$');
final _regMap = RegExp(r'^Map<String,(.*)>$');

bool _isEpochMarker(String? pattern) =>
    pattern == _dateEpochMarker || pattern == '/$_dateEpochMarker/';
