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
  description = "Confidential MCP resource server client used only for server-side token exchange."
  value       = try(keycloak_openid_client.client["weave_mcp_server"].client_id, null)
}

output "weave_mcp_audience" {
  description = "Exact HTTPS audience accepted by the machine-only MCP resource server."
  value       = "${var.api_public_url}/mcp"
}

output "agent_runtime_resource" {
  description = "Exact HTTPS audience accepted by the workload-only RuntimeProfile resource."
  value       = "${var.api_public_url}/api/v1/agent-runtime"
}

output "agent_runtime_profile_read_scope_name" {
  description = "Machine-only scope used by a cell to fetch its current RuntimeProfile."
  value       = keycloak_openid_client_scope.agent_runtime_profile_read.name
}

output "weaver_runtime_workload_scope_name" {
  description = "Fixed default client scope carrying only the per-cell Weaver workload role."
  value       = keycloak_openid_client_scope.weaver_runtime_workload.name
}

output "agent_runtime_admin_scope_name" {
  description = "Interactive owner/admin scope used by the separate Organization/Admin Console."
  value       = keycloak_openid_client_scope.agent_runtime_admin.name
}

output "mcp_tools_scope_name" {
  description = "Machine-only scope used by an active cell to call the MCP resource."
  value       = keycloak_openid_client_scope.mcp_tools.name
}

output "weave_identity_admin_client_id" {
  description = "Backend-only Keycloak client used for organization invitation administration."
  value       = try(keycloak_openid_client.client["weave_identity_admin"].client_id, null)
}

output "weave_agent_runtime_admin_client_id" {
  description = "Dedicated client used only for ARC-managed per-cell Keycloak workload identities."
  value       = try(keycloak_openid_client.client["weave_agent_runtime_admin"].client_id, null)
}

output "weave_identity_admin_client_secret" {
  description = "Keycloak-generated credential for the backend-only identity administrator."
  value       = keycloak_openid_client.client["weave_identity_admin"].client_secret
  sensitive   = true
}

output "weave_agent_runtime_admin_client_secret" {
  description = "Keycloak-generated credential for the ARC workload identity administrator."
  value       = keycloak_openid_client.client["weave_agent_runtime_admin"].client_secret
  sensitive   = true
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
