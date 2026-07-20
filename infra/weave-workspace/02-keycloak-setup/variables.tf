variable "tenant_slug" {
  description = "Tenant identifier used for the Keycloak realm."
  type        = string
  default     = "weave"

  validation {
    condition     = can(regex("^[a-z0-9-]+$", var.tenant_slug))
    error_message = "tenant_slug must contain only lowercase letters, numbers, and hyphens."
  }
}

variable "tenant_domain" {
  description = "Base domain used to derive public service hostnames."
  type        = string
  default     = "weave.test"
}

variable "auth_subdomain" {
  description = "Subdomain used for Keycloak."
  type        = string
  default     = "auth"
}

variable "matrix_subdomain" {
  description = "Subdomain used for Matrix."
  type        = string
  default     = "matrix"
}

variable "nextcloud_subdomain" {
  description = "Subdomain used for the canonical Nextcloud URL."
  type        = string
  default     = "files"
}

variable "api_subdomain" {
  description = "Subdomain used for the canonical Weave backend API origin."
  type        = string
  default     = "api"
}

variable "admin_subdomain" {
  description = "Subdomain used for the separate Organization/Admin Console deploy target."
  type        = string
  default     = "admin"
}

variable "public_scheme" {
  description = "Public URL scheme used by browser-facing services."
  type        = string
  default     = "https"

  validation {
    condition     = contains(["http", "https"], var.public_scheme)
    error_message = "public_scheme must be either http or https."
  }
}

variable "proxy_host_port" {
  description = "HTTPS host port exposed by the reverse proxy."
  type        = number
  default     = 443
}

variable "local_lan_host" {
  description = "Deprecated/non-canonical LAN host/IP compatibility input. Keycloak public issuer and redirects remain DNS-first on weave.test / *.weave.test."
  type        = string
  default     = ""
}

variable "keycloak_host_port" {
  description = "Direct host port for Keycloak admin access."
  type        = number
  default     = 8080
}

variable "keycloak_admin_host" {
  description = "Host used by Terraform to reach direct Keycloak admin access."
  type        = string
  default     = "127.0.0.1"
}

variable "keycloak_admin_username" {
  description = "Keycloak admin username."
  type        = string
  default     = "admin"
}

variable "keycloak_admin_password" {
  description = "Keycloak admin password."
  type        = string
  sensitive   = true
}

variable "keycloak_smtp_host" {
  description = "SMTP host used by the dogfood/local Keycloak realm."
  type        = string
  default     = "weave-mailpit"
}

variable "keycloak_smtp_port" {
  description = "SMTP port used by the dogfood/local Keycloak realm."
  type        = string
  default     = "1025"
}

variable "keycloak_smtp_from" {
  description = "Optional From address used by Keycloak. Defaults to no-reply at tenant_domain."
  type        = string
  default     = null
  nullable    = true
}

variable "keycloak_smtp_from_display_name" {
  description = "Sender display name used by Keycloak invitation and activation mail."
  type        = string
  default     = "Weave"
}

variable "keycloak_smtp_ssl" {
  description = "Use implicit TLS for Keycloak SMTP."
  type        = bool
  default     = false
}

variable "keycloak_smtp_starttls" {
  description = "Require STARTTLS for Keycloak SMTP. Enable for the typical production submission profile."
  type        = bool
  default     = false
}

variable "keycloak_smtp_username" {
  description = "Optional Keycloak SMTP authentication username."
  type        = string
  default     = ""
}

variable "keycloak_smtp_password" {
  description = "Optional Keycloak SMTP authentication password."
  type        = string
  sensitive   = true
  default     = ""
}

variable "organization_display_name" {
  description = "Human-readable organization name used in Keycloak-managed identity surfaces."
  type        = string
  default     = "Weave"
}

variable "test_user_email" {
  description = "Optional integration-test email. Defaults to test at tenant_domain."
  type        = string
  default     = null
  nullable    = true
}

variable "matrix_mas_client_secret" {
  description = "Fixed client secret for the matrix-mas confidential client."
  type        = string
  sensitive   = true
}

variable "identity_admin_client_secret" {
  description = "Client secret for the backend-only Keycloak organization identity administrator."
  type        = string
  sensitive   = true
}

variable "weave_mcp_client_secret" {
  description = "Client secret for the confidential weave-mcp-server token-exchange workload."
  type        = string
  sensitive   = true
}

variable "create_test_user" {
  description = "Create a test user for integration testing. Do not enable in production."
  type        = bool
  default     = false
}

variable "test_user_password" {
  type        = string
  description = "Password for the integration test user. Only used when create_test_user is true."
  sensitive   = true
  default     = ""
}

variable "context_authorization_default_tenant_id" {
  description = "Tenant ID exposed as the weave_tenant_id access-token claim for this Keycloak tenant realm."
  type        = string
  default     = "tenant-default"
}
