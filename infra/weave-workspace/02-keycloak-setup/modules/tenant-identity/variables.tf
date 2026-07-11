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

variable "matrix_mas_upstream_id" {
  description = "ULID used by MAS for the upstream OIDC provider."
  type        = string
}

variable "matrix_mas_client_secret" {
  description = "Shared confidential client secret for the matrix-mas client."
  type        = string
  sensitive   = true
}

variable "identity_admin_client_secret" {
  description = "Client secret for the backend-only Keycloak organization identity administrator."
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
  description = "From address used by the dogfood/local Keycloak realm."
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
