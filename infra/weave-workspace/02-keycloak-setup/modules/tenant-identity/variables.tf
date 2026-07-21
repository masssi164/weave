variable "tenant_slug" {
  description = "Tenant identifier used for the Keycloak realm."
  type        = string
}

variable "product_public_url" {
  description = "Browser-facing Weave product URL used after organization invitation activation."
  type        = string
}

variable "keycloak_public_url" {
  description = "Browser-facing Keycloak base URL."
  type        = string
}

variable "mas_public_url" {
  description = "Browser-facing Matrix Authentication Service base URL."
  type        = string
}

variable "nextcloud_public_url" {
  description = "Browser-facing Nextcloud base URL."
  type        = string
}

variable "admin_console_public_url" {
  description = "Browser-facing Organization/Admin Console base URL."
  type        = string
}

variable "api_public_url" {
  description = "Canonical HTTPS Weave API origin used to define exact ARC and MCP OAuth resources."
  type        = string
}

variable "matrix_mas_upstream_id" {
  description = "ULID used by MAS for the upstream OIDC provider."
  type        = string
}

variable "matrix_mas_client_secret" {
  description = "Shared confidential client secret for the matrix-mas client."
  type        = string
  sensitive   = true
}

variable "admin_client_secret_rotation_epoch" {
  description = "Explicit epoch that rotates both Keycloak-generated backend administrative client secrets."
  type        = string
}

variable "weave_mcp_client_secret" {
  description = "Client secret for the confidential weave-mcp-server token-exchange workload."
  type        = string
  sensitive   = true
}

variable "smtp_host" {
  description = "SMTP host used by the dogfood/local Keycloak realm."
  type        = string
}

variable "smtp_port" {
  description = "SMTP port used by the dogfood/local Keycloak realm."
  type        = string
}

variable "smtp_from" {
  description = "Environment-configured From address used by the Keycloak realm."
  type        = string
}

variable "smtp_from_display_name" {
  description = "Environment-configured sender display name used by Keycloak mail."
  type        = string
}

variable "smtp_ssl" {
  description = "Use implicit TLS for the configured SMTP transport."
  type        = bool
}

variable "smtp_starttls" {
  description = "Require STARTTLS for the configured SMTP transport."
  type        = bool
}

variable "smtp_username" {
  description = "Optional SMTP authentication username."
  type        = string
}

variable "smtp_password" {
  description = "Optional SMTP authentication password."
  type        = string
  sensitive   = true
}

variable "organization_display_name" {
  description = "Human-readable organization name shared by Keycloak invitation and activation surfaces."
  type        = string
}

variable "test_user_email" {
  description = "Environment-derived email for the optional integration-test user."
  type        = string
}

variable "create_test_user" {
  description = "Create a test user for integration testing. Do not enable in production."
  type        = bool
  default     = false
}

variable "test_user_password" {
  type        = string
  description = "Password for the integration test user. Only used when create_test_user is true. Must be passed in as a sensitive variable."
  sensitive   = true
  default     = ""
}

variable "context_authorization_default_tenant_id" {
  description = "Tenant ID exposed as the weave_tenant_id access-token claim for this Keycloak tenant realm."
  type        = string
  default     = "tenant-default"
}
