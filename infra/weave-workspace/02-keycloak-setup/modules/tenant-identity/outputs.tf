output "keycloak_realm_name" {
  description = "Configured tenant realm name."
  value       = keycloak_realm.tenant.realm
}

output "keycloak_issuer_url" {
  description = "Issuer URL advertised by Keycloak for the tenant realm."
  value       = "${var.keycloak_public_url}/realms/${keycloak_realm.tenant.realm}"
}

output "weave_app_client_id" {
  description = "Client ID configured for the Weave mobile app."
  value       = try(keycloak_openid_client.client["weave_app"].client_id, null)
}

output "weave_app_post_logout_redirect_uris" {
  description = "Allowed post-logout redirect URIs for the Weave mobile app."
  value       = try(keycloak_openid_client.client["weave_app"].valid_post_logout_redirect_uris, [])
}

output "weave_app_redirect_uris" {
  description = "Allowed sign-in redirect URIs for the Weave mobile app."
  value       = try(keycloak_openid_client.client["weave_app"].valid_redirect_uris, [])
}

output "weave_app_optional_scopes" {
  description = "Optional scopes assigned to the Weave mobile app."
  value       = try(keycloak_openid_client_optional_scopes.weave_app.optional_scopes, [])
}

output "weave_app_default_scopes" {
  description = "Default scopes assigned to the Weave mobile app."
  value       = try(keycloak_openid_client_default_scopes.weave_app.default_scopes, [])
}

output "weave_backend_client_id" {
  description = "Client ID configured for the Weave backend."
  value       = try(keycloak_openid_client.client["weave_backend"].client_id, null)
}

output "weave_backend_audience" {
  description = "Audience value emitted for access tokens that the Weave backend accepts."
  value       = try(keycloak_openid_client.client["weave_backend"].client_id, null)
}

output "weave_mcp_client_id" {
  description = "Confidential MCP workload client allowed to perform standard token exchange."
  value       = try(keycloak_openid_client.client["weave_mcp_server"].client_id, null)
}

output "weave_mcp_audience" {
  description = "Audience required by the MCP resource server for member runtime tokens."
  value       = var.weave_mcp_resource
}

output "weave_mcp_backend_scope_name" {
  description = "Backend-only scope requested during MCP standard token exchange."
  value       = try(keycloak_openid_client_scope.weave_mcp_backend.name, null)
}

output "weave_identity_admin_client_id" {
  description = "Backend-only Keycloak client used for organization invitation administration."
  value       = try(keycloak_openid_client.client["weave_identity_admin"].client_id, null)
}

output "weave_organization_id" {
  description = "Keycloak organization identifier managed by this tenant module."
  value       = keycloak_organization.tenant.id
}

output "weave_workspace_scope_name" {
  description = "Client scope that adds the Weave backend-required audience."
  value       = try(keycloak_openid_client_scope.weave_workspace.name, null)
}

output "nextcloud_client_id" {
  description = "Client ID configured for Nextcloud."
  value       = try(keycloak_openid_client.client["nextcloud"].client_id, null)
}

output "nextcloud_client_secret" {
  description = "Client secret configured for Nextcloud."
  value       = try(keycloak_openid_client.client["nextcloud"].client_secret, null)
  sensitive   = true
}

output "test_user_username" {
  description = "Integration test username when create_test_user is enabled."
  value       = var.create_test_user ? local.test_user.username : null
}

output "test_user_password" {
  description = "Integration test password when create_test_user is enabled."
  value       = var.create_test_user ? local.test_user.password : null
  sensitive   = true
}

output "weave_product_roles" {
  description = "MVP weave-app client roles exposed through the default roles scope."
  value       = sort(keys(keycloak_role.weave_product))
}

output "weave_product_role_groups" {
  description = "Default Keycloak groups mapped one-to-one to MVP product roles."
  value       = local.weave_product_role_groups
}

output "weave_admin_console_client_id" {
  description = "Client ID configured for the separate Organization/Admin Console."
  value       = try(keycloak_openid_client.client["weave_admin_console"].client_id, null)
}

output "weave_admin_console_redirect_uris" {
  description = "Allowed sign-in redirect URIs for the Organization/Admin Console."
  value       = try(keycloak_openid_client.client["weave_admin_console"].valid_redirect_uris, [])
}
