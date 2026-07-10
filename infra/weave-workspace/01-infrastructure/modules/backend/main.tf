terraform {
  required_providers {
    docker = {
      source = "kreuzwerker/docker"
    }
  }
}

resource "docker_image" "this" {
  name         = var.image_name
  keep_locally = true
}

resource "docker_container" "this" {
  name    = var.container_name
  image   = docker_image.this.image_id
  restart = "unless-stopped"
  depends_on = [
    docker_image.this,
  ]
  env = concat([
    "WEAVE_OIDC_ISSUER_URI=${var.oidc_issuer_uri}",
    "WEAVE_OIDC_JWK_SET_URI=${var.oidc_jwk_set_uri}",
    "WEAVE_OIDC_REQUIRED_AUDIENCE=${var.oidc_required_audience}",
    "WEAVE_CLIENT_ID=${var.client_id}",
    "WEAVE_MCP_BOUNDARY_TOKEN=${var.mcp_boundary_token}",
    "WEAVE_PUBLIC_BASE_URL=${var.public_base_url}",
    "WEAVE_API_ORIGIN=${var.api_origin}",
    "WEAVE_API_BASE_URL=${var.api_base_url}",
    "WEAVE_AUTH_BASE_URL=${var.auth_base_url}",
    "WEAVE_MATRIX_BASE_URL=${var.matrix_base_url}",
    "WEAVE_FILES_PRODUCT_URL=${var.files_product_url}",
    "WEAVE_CALENDAR_PRODUCT_URL=${var.calendar_product_url}",
    "WEAVE_MATRIX_HOMESERVER_URL=${var.matrix_facade_url}",
    "WEAVE_MATRIX_FACADE_SERVER_NAME=${var.public_host}",
    "WEAVE_MATRIX_FACADE_BASE_URL=${var.matrix_facade_url}",
    "WEAVE_NEXTCLOUD_PUBLIC_BASE_URL=${var.nextcloud_public_base_url}",
    "WEAVE_NEXTCLOUD_BASE_URL=${var.nextcloud_base_url}",
    "WEAVE_NEXTCLOUD_FILES_ACTOR_MODEL=${var.nextcloud_files_actor_model}",
    "WEAVE_NEXTCLOUD_FILES_ACTOR_USERNAME=${var.nextcloud_files_actor_username}",
    "WEAVE_NEXTCLOUD_FILES_ACTOR_TOKEN=${var.nextcloud_files_actor_token}",
    "WEAVE_NEXTCLOUD_FILES_WEBDAV_ROOT_PATH=${var.nextcloud_files_webdav_root_path}",
    "WEAVE_WORKSPACE_CALENDAR_ENABLED=true",
    "WEAVE_WORKSPACE_CALENDAR_READINESS=ready",
    "WEAVE_WORKSPACE_BOARDS_ENABLED=true",
    "WEAVE_WORKSPACE_BOARDS_READINESS=ready",
    "WEAVE_CALDAV_BASE_URL=${var.caldav_base_url}",
    "WEAVE_CALDAV_CALENDAR_PATH_TEMPLATE=${var.caldav_calendar_path_template}",
    "WEAVE_CALDAV_AUTH_MODE=${var.caldav_auth_mode}",
    "WEAVE_CALDAV_BACKEND_USERNAME=${var.caldav_backend_username}",
    "WEAVE_CALDAV_BACKEND_TOKEN=${var.caldav_backend_token}",
    "WEAVE_CALDAV_REQUEST_TIMEOUT_SECONDS=${var.caldav_request_timeout_seconds}",
    "WEAVE_CALDAV_EXTERNAL_DISCOVERY_URL=${var.caldav_external_discovery_url}",
    "WEAVE_CALDAV_EXTERNAL_CREDENTIAL_MODE=${var.caldav_external_credential_mode}",
    "WEAVE_CALDAV_EXTERNAL_PROFILE_PASSWORD_MODE=${var.caldav_external_profile_password_mode}",
    "WEAVE_CALDAV_EXTERNAL_PRIVATE_USER_CALENDARS=${var.caldav_external_private_user_calendars}",
    "WEAVE_INTEROP_ENABLED=${var.interop_enabled}",
    "WEAVE_INTEROP_SLACK_ENABLED=${var.interop_slack_enabled}",
    "WEAVE_INTEROP_TEAMS_ENABLED=${var.interop_teams_enabled}",
    "WEAVE_CONNECTORS_PUBLIC_SDK_ENABLED=${var.connectors_public_sdk_enabled}",
    "WEAVE_PROVIDER_STACK_PROFILE=${var.provider_stack_profile}",
    "WEAVE_PROVIDER_STACK_READINESS=${var.provider_stack_readiness}",
    "WEAVE_PROVIDER_SELECTIONS_STORAGE_PATH=${var.provider_selections_storage_path}",
    "WEAVE_PERSISTENCE_JDBC_URL=${var.persistence_jdbc_url}",
    "WEAVE_PERSISTENCE_JDBC_USERNAME=${var.persistence_jdbc_username}",
    "WEAVE_PERSISTENCE_JDBC_PASSWORD=${var.persistence_jdbc_password}",
    "WEAVE_DEVICE_CREDENTIAL_STORAGE_MODE=${var.device_credential_storage_mode}",
    "WEAVE_MATRIX_E2EE_STORAGE_MODE=jdbc",
    "WEAVE_DEVOPS_PRIMARY_PROVIDER=${var.devops_primary_provider}",
    "WEAVE_DEVOPS_GITLAB_RUNTIME_ENABLED=${var.devops_gitlab_runtime_enabled}",
    "WEAVE_DEVOPS_GITLAB_BASE_URL=${var.devops_gitlab_base_url}",
    "WEAVE_DEVOPS_GITLAB_API_TOKEN=${var.devops_gitlab_api_token}",
    "WEAVE_DEVOPS_GITLAB_WRITES_ENABLED=${var.devops_gitlab_writes_enabled}",
    "WEAVE_OFFICE_PRIMARY_PROVIDER=${var.office_primary_provider}",
    "WEAVE_OFFICE_ONLYOFFICE_RUNTIME_ENABLED=${var.office_onlyoffice_runtime_enabled}",
    "WEAVE_OFFICE_ONLYOFFICE_DOCUMENT_SERVER_URL=${var.office_onlyoffice_document_server_url}",
    "WEAVE_OFFICE_ONLYOFFICE_JWT_SECRET=${var.office_onlyoffice_jwt_secret}",
    "WEAVE_OFFICE_NEXTCLOUD_INTEGRATION_MODE=${var.office_nextcloud_integration_mode}",
    "WEAVE_OFFICE_COLLABORA_RUNTIME_ENABLED=${var.office_collabora_runtime_enabled}",
    "WEAVE_GROUPWARE_CONTACTS_RUNTIME_ENABLED=${var.groupware_contacts_runtime_enabled}",
    "WEAVE_GROUPWARE_FORMS_RUNTIME_ENABLED=${var.groupware_forms_runtime_enabled}",
    "WEAVE_LIVEKIT_ENABLED=${var.livekit_runtime_enabled}",
    "WEAVE_LIVEKIT_URL=${var.livekit_url}",
    "WEAVE_LIVEKIT_API_KEY=${var.livekit_api_key}",
    "WEAVE_LIVEKIT_API_SECRET=${var.livekit_api_secret}",
    "WEAVE_LIVEKIT_TOKEN_ENDPOINT=${var.livekit_token_endpoint}",
    "WEAVE_BOARDS_RUNTIME_ENABLED=${var.boards_runtime_enabled}",
    "WEAVE_BOARDS_PROVIDER=${var.boards_provider}",
    "WEAVE_BOARDS_OPENPROJECT_RUNTIME_ENABLED=${var.boards_openproject_runtime_enabled}",
    "WEAVE_BOARDS_OPENPROJECT_READ_SYNC_ENABLED=${var.boards_openproject_read_sync_enabled}",
    "WEAVE_BOARDS_OPENPROJECT_CONTEXT_AUTHORIZATION_ENABLED=${var.boards_openproject_context_authorization_enabled}",
    "WEAVE_BOARDS_OPENPROJECT_AUDIT_CONSENT_ENABLED=${var.boards_openproject_audit_consent_enabled}",
    "WEAVE_BOARDS_OPENPROJECT_PROVIDER_WRITES_ENABLED=${var.boards_openproject_provider_writes_enabled}",
    "WEAVE_BOARDS_NEXTCLOUD_DECK_RUNTIME_ENABLED=${var.boards_nextcloud_deck_runtime_enabled}",
    "WEAVE_BOARDS_OPENPROJECT_AUTH_MODE=${var.boards_openproject_auth_mode}",
    "WEAVE_BOARDS_OPENPROJECT_BASE_URL=${var.boards_openproject_base_url}",
    "WEAVE_BOARDS_OPENPROJECT_API_TOKEN=${var.boards_openproject_api_token}",
    "WEAVE_CONTEXT_AUTHORIZATION_TENANT_CLAIM=${var.context_authorization_tenant_claim}",
    "WEAVE_CONTEXT_AUTHORIZATION_TENANT_FALLBACK_CLAIM=${var.context_authorization_tenant_fallback_claim}",
    "WEAVE_CONTEXT_AUTHORIZATION_DEFAULT_TENANT_ID=${var.context_authorization_default_tenant_id}",
    "WEAVE_CONTEXT_AUTHORIZATION_PRINCIPAL_CLAIM=${var.context_authorization_principal_claim}",
    "WEAVE_CONTEXT_AUTHORIZATION_PRINCIPAL_REF_PREFIX=${var.context_authorization_principal_ref_prefix}",
    ], var.context_authorization_bootstrap_enabled ? [
    "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_TENANT_ID=${var.context_authorization_default_tenant_id}",
    "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_CONTEXT_ID=${var.context_authorization_bootstrap_context_id}",
    "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_PRINCIPAL_REF=${var.context_authorization_bootstrap_principal_ref}",
    "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_ROLE=${var.context_authorization_bootstrap_role}",
    "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_SOURCE=local-dev-bootstrap",
    ] : [], var.context_authorization_bootstrap_enabled && var.context_authorization_dogfood_principal_ref != "" ? [
    "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_TENANT_ID=${var.context_authorization_default_tenant_id}",
    "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_CONTEXT_ID=${var.context_authorization_bootstrap_context_id}",
    "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_PRINCIPAL_REF=${var.context_authorization_dogfood_principal_ref}",
    "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_ROLE=${var.context_authorization_bootstrap_role}",
    "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_SOURCE=local-dogfood-bootstrap",
    ] : [], var.context_authorization_bootstrap_enabled ? [
    # Project the deterministic workspace membership to the seeded team/channel
    # Contexts used by the live-stack Calendar facade E2E. This keeps the
    # product ReBAC path fail-closed unless the local/dev bootstrap is explicitly
    # enabled, while allowing channel-scoped calendar CRUD to prove the same
    # authorization graph as production adapters will use.
    "WEAVE_CONTEXT_AUTHORIZATION_GRAPH_EDGES_0_TENANT_ID=${var.context_authorization_default_tenant_id}",
    "WEAVE_CONTEXT_AUTHORIZATION_GRAPH_EDGES_0_FROM_CONTEXT_ID=${var.context_authorization_bootstrap_context_id}",
    "WEAVE_CONTEXT_AUTHORIZATION_GRAPH_EDGES_0_TO_CONTEXT_ID=team-engineering",
    "WEAVE_CONTEXT_AUTHORIZATION_GRAPH_EDGES_0_RELATION=CONTAINS",
    "WEAVE_CONTEXT_AUTHORIZATION_GRAPH_EDGES_1_TENANT_ID=${var.context_authorization_default_tenant_id}",
    "WEAVE_CONTEXT_AUTHORIZATION_GRAPH_EDGES_1_FROM_CONTEXT_ID=team-engineering",
    "WEAVE_CONTEXT_AUTHORIZATION_GRAPH_EDGES_1_TO_CONTEXT_ID=channel-engineering-general",
    "WEAVE_CONTEXT_AUTHORIZATION_GRAPH_EDGES_1_RELATION=CONTAINS",
  ] : [])

  ports {
    internal = var.container_port
    external = var.host_port
  }

  healthcheck {
    test = [
      "CMD-SHELL",
      "curl -fsS http://127.0.0.1:${var.container_port}${var.healthcheck_path} || wget -qO- http://127.0.0.1:${var.container_port}${var.healthcheck_path} >/dev/null || exit 1",
    ]
    interval     = "10s"
    timeout      = "5s"
    retries      = 12
    start_period = "30s"
  }

  upload {
    file        = var.provider_selections_storage_path
    source      = var.provider_selections_source
    source_hash = var.provider_selections_source_hash
  }

  networks_advanced {
    name    = var.network_name
    aliases = [var.public_host, var.container_name]
  }

  lifecycle {
    ignore_changes = [
      cpu_shares,
      dns,
      dns_opts,
      dns_search,
      entrypoint,
      group_add,
      hostname,
      init,
      ipc_mode,
      log_driver,
      log_opts,
      max_retry_count,
      memory,
      memory_swap,
      privileged,
      publish_all_ports,
      runtime,
      security_opts,
      shm_size,
      stop_signal,
      stop_timeout,
      storage_opts,
      sysctls,
      tmpfs,
      user,
      working_dir,
    ]
  }
}
