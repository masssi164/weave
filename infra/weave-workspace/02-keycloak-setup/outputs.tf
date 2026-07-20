output "keycloak_realm_name" {
  description = "Configured tenant realm name."
  value       = module.tenant_identity.keycloak_realm_name
}

output "keycloak_issuer_url" {
  description = "Issuer URL advertised to downstream clients."
  value       = module.tenant_identity.keycloak_issuer_url
}

output "weave_app_client_id" {
  description = "Client ID configured for the Weave mobile app."
  value       = module.tenant_identity.weave_app_client_id
}

output "weave_app_post_logout_redirect_uris" {
  description = "Allowed post-logout redirect URIs for the Weave mobile app."
  value       = module.tenant_identity.weave_app_post_logout_redirect_uris
}

output "weave_app_redirect_uris" {
  description = "Allowed sign-in redirect URIs for the Weave mobile app."
  value       = module.tenant_identity.weave_app_redirect_uris
}

output "weave_app_optional_scopes" {
  description = "Optional scopes assigned to the Weave mobile app."
  value       = module.tenant_identity.weave_app_optional_scopes
}

output "weave_app_default_scopes" {
  description = "Default scopes assigned to the Weave mobile app."
  value       = module.tenant_identity.weave_app_default_scopes
}

output "weave_backend_client_id" {
  description = "Client ID configured for the Weave backend."
  value       = module.tenant_identity.weave_backend_client_id
}

output "weave_backend_audience" {
  description = "Audience value emitted for access tokens that the Weave backend accepts."
  value       = module.tenant_identity.weave_backend_audience
}

output "weave_mcp_client_id" {
  description = "Confidential MCP resource server client ID used only for server-side exchange."
  value       = module.tenant_identity.weave_mcp_client_id
}

output "weave_mcp_audience" {
  description = "Audience required by the MCP resource server."
  value       = module.tenant_identity.weave_mcp_audience
}

output "agent_runtime_resource" {
  description = "Exact HTTPS audience required by the workload-only RuntimeProfile resource."
  value       = module.tenant_identity.agent_runtime_resource
}

output "agent_runtime_profile_read_scope_name" {
  description = "Machine-only RuntimeProfile read scope."
  value       = module.tenant_identity.agent_runtime_profile_read_scope_name
}

output "weaver_runtime_workload_scope_name" {
  description = "Fixed default client scope carrying only the per-cell Weaver workload role."
  value       = module.tenant_identity.weaver_runtime_workload_scope_name
}

output "agent_runtime_admin_scope_name" {
  description = "Interactive owner/admin scope used by the separate Organization/Admin Console."
  value       = module.tenant_identity.agent_runtime_admin_scope_name
}

output "mcp_tools_scope_name" {
  description = "Machine-only MCP tool scope."
  value       = module.tenant_identity.mcp_tools_scope_name
}

output "weave_identity_admin_client_id" {
  description = "Backend-only client used for Keycloak organization invitation administration."
  value       = module.tenant_identity.weave_identity_admin_client_id
}

output "weave_agent_runtime_admin_client_id" {
  description = "Dedicated client used only for ARC-managed per-cell Keycloak workload identities."
  value       = module.tenant_identity.weave_agent_runtime_admin_client_id
}

output "weave_organization_id" {
  description = "Keycloak organization identifier managed by this stage."
  value       = module.tenant_identity.weave_organization_id
}

output "weave_workspace_scope_name" {
  description = "Client scope that adds the Weave backend-required audience."
  value       = module.tenant_identity.weave_workspace_scope_name
}

output "nextcloud_client_id" {
  description = "Client ID configured for Nextcloud."
  value       = module.tenant_identity.nextcloud_client_id
}

output "nextcloud_client_secret" {
  description = "Client secret configured for Nextcloud."
  value       = module.tenant_identity.nextcloud_client_secret
  sensitive   = true
}

output "test_user_username" {
  description = "Integration test username when create_test_user is enabled."
  value       = module.tenant_identity.test_user_username
}

output "test_user_password" {
  description = "Integration test password when create_test_user is enabled."
  value       = module.tenant_identity.test_user_password
  sensitive   = true
}

output "weave_product_roles" {
  description = "MVP weave-app client roles exposed through the roles scope."
  value       = module.tenant_identity.weave_product_roles
}

output "weave_product_role_groups" {
  description = "Default Keycloak groups mapped one-to-one to MVP product roles."
  value       = module.tenant_identity.weave_product_role_groups
}

output "weave_admin_console_client_id" {
  description = "Client ID configured for the separate Organization/Admin Console."
  value       = module.tenant_identity.weave_admin_console_client_id
}

output "weave_admin_console_redirect_uris" {
  description = "Allowed sign-in redirect URIs for the Organization/Admin Console."
  value       = module.tenant_identity.weave_admin_console_redirect_uris
}
