output "public_hosts" {
  description = "Browser-facing hostnames reserved by the local stack contract."
  value       = local.public_hosts
}

output "public_urls" {
  description = "Browser-facing URLs reserved by the local stack contract."
  value       = local.public_urls
}

output "weave_api_base_url" {
  description = "Canonical public Weave backend API base URL."
  value       = "${local.public_urls.api}/api"
}

output "weave_files_product_url" {
  description = "Weave product files route; not a direct Nextcloud route."
  value       = "${local.public_urls.weave}/files"
}

output "weave_calendar_product_url" {
  description = "Weave product calendar route; not a direct Nextcloud route."
  value       = "${local.public_urls.weave}/calendar"
}

output "nextcloud_base_url" {
  description = "Canonical Nextcloud base URL for WebDAV, CalDAV, OCS, OIDC redirects, and fallback/admin UI."
  value       = local.public_urls.files
}

output "nextcloud_backend_actor_username" {
  description = "Backend-owned Nextcloud service account username configured for local/dev files and calendar facades."
  value       = var.nextcloud_backend_actor_username
}


output "app_config" {
  description = "No-secret public endpoint contract for Weave native clients and local tests."
  value = {
    WEAVE_PUBLIC_BASE_URL                        = local.public_urls.weave
    WEAVE_API_ORIGIN                             = local.public_urls.api
    WEAVE_API_BASE_URL                           = "${local.public_urls.api}/api"
    WEAVE_AUTH_BASE_URL                          = local.public_urls.auth
    WEAVE_OIDC_ISSUER_URL                        = local.keycloak_issuer_url
    WEAVE_MATRIX_HOMESERVER_URL                  = local.public_urls.matrix
    WEAVE_FILES_PRODUCT_URL                      = "${local.public_urls.weave}/files"
    WEAVE_CALENDAR_PRODUCT_URL                   = "${local.public_urls.weave}/calendar"
    WEAVE_NEXTCLOUD_BASE_URL                     = local.public_urls.files
    WEAVE_CALDAV_EXTERNAL_DISCOVERY_URL          = "${local.public_urls.files}/remote.php/dav"
    WEAVE_CALDAV_EXTERNAL_CREDENTIAL_MODE        = "nextcloud-login-flow-app-password"
    WEAVE_CALDAV_EXTERNAL_PROFILE_PASSWORD_MODE  = "omit"
    WEAVE_CALDAV_EXTERNAL_PRIVATE_USER_CALENDARS = "disabled"
    WEAVE_PROVIDER_STACK_PROFILE                 = var.provider_stack_profile
    WEAVE_PROVIDER_STACK_READINESS               = var.provider_stack_readiness
    WEAVE_DEVOPS_PRIMARY_PROVIDER                = var.devops_primary_provider
    WEAVE_DEVOPS_ALTERNATIVE_PROVIDER            = var.devops_alternative_provider
    WEAVE_DEVOPS_GITLAB_RUNTIME_ENABLED          = tostring(var.devops_gitlab_runtime_enabled)
    WEAVE_DEVOPS_GITLAB_BASE_URL                 = var.devops_gitlab_base_url
    WEAVE_DEVOPS_GITLAB_WRITES_ENABLED           = tostring(var.devops_gitlab_writes_enabled)
    WEAVE_DEVOPS_FORGEJO_RUNTIME_ENABLED         = tostring(var.devops_forgejo_runtime_enabled)
    WEAVE_DEVOPS_FORGEJO_BASE_URL                = var.devops_forgejo_base_url
    WEAVE_DEVOPS_FORGEJO_WRITES_ENABLED          = tostring(var.devops_forgejo_writes_enabled)
    WEAVE_OFFICE_PRIMARY_PROVIDER                = var.office_primary_provider
    WEAVE_OFFICE_ONLYOFFICE_RUNTIME_ENABLED      = tostring(var.office_onlyoffice_runtime_enabled)
    WEAVE_OFFICE_ONLYOFFICE_DOCUMENT_SERVER_URL  = var.office_onlyoffice_document_server_url
    WEAVE_OFFICE_NEXTCLOUD_INTEGRATION_MODE      = var.office_nextcloud_integration_mode
    WEAVE_OFFICE_COLLABORA_RUNTIME_ENABLED       = tostring(var.office_collabora_runtime_enabled)
    WEAVE_GROUPWARE_CONTACTS_RUNTIME_ENABLED     = tostring(var.groupware_contacts_runtime_enabled)
    WEAVE_GROUPWARE_FORMS_RUNTIME_ENABLED        = tostring(var.groupware_forms_runtime_enabled)
    WEAVE_BOARDS_NEXTCLOUD_DECK_RUNTIME_ENABLED  = tostring(var.boards_nextcloud_deck_runtime_enabled)
    WEAVE_TARGET_MOBILE                          = "true"
    WEAVE_TARGET_DESKTOP                         = "true"
    WEAVE_TARGET_WEB                             = "false"
    WEAVE_MATRIX_FEDERATION                      = "disabled"
    WEAVE_CHAT_E2EE                              = "active-architecture-gated"
  }
}

output "database_names" {
  description = "Runtime PostgreSQL database name used by each service inside the shared PostgreSQL instance."
  value = {
    for service, config in local.service_databases :
    service => config.database_name
  }
}

output "nextcloud_database_name" {
  description = "PostgreSQL database name used by Nextcloud inside the shared PostgreSQL instance."
  value       = local.service_databases.nextcloud.database_name
}

output "weave_backend_oidc_issuer_uri" {
  description = "OIDC issuer URI configured for the Weave backend."
  value       = local.keycloak_issuer_url
}

output "weave_backend_oidc_jwk_set_uri" {
  description = "OIDC JWKS URI configured for the Weave backend."
  value       = local.keycloak_jwk_set_uri
}

output "weave_backend_required_audience" {
  description = "OIDC audience value configured for the Weave backend."
  value       = local.weave_backend_audience
}

output "weave_backend_client_id" {
  description = "OIDC client ID configured for Weave backend authorized-party validation."
  value       = local.weave_app_client_id
}

output "service_names" {
  description = "Stable internal Docker service names used by the stack."
  value       = local.service_names
}
