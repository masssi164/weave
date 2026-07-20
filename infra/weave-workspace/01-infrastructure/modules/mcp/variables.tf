variable "network_name" {
  description = "Docker network name for the Weave MCP server."
  type        = string
}

variable "container_name" {
  description = "Container name for the Weave MCP server."
  type        = string
}

variable "image_name" {
  description = "Spring AI Weave MCP server image reference."
  type        = string
}

variable "host_port" {
  description = "Loopback host port used by operator smoke checks."
  type        = number
}

variable "container_port" {
  description = "Internal HTTP port exposed by the Weave MCP server."
  type        = number
}

variable "backend_base_url" {
  description = "Internal Weave backend base URL used for canonical MCP dispatch."
  type        = string
}

variable "oidc_issuer_uri" {
  description = "OIDC issuer URI validated by the MCP resource server."
  type        = string
}

variable "oidc_jwk_set_uri" {
  description = "Internal JWKS URI used to validate MCP bearer tokens."
  type        = string
}

variable "oidc_required_audience" {
  description = "Required audience for MCP bearer tokens."
  type        = string
}

variable "oidc_token_uri" {
  description = "Internal Keycloak token endpoint used for standard token exchange."
  type        = string
}

variable "mcp_client_id" {
  description = "Confidential Keycloak workload client ID used by the MCP server."
  type        = string
}

variable "mcp_client_secret" {
  description = "Confidential Keycloak workload client secret used only for token exchange."
  type        = string
  sensitive   = true
}

variable "inbound_authorized_party" {
  description = "Authorized party required on member tokens accepted by the MCP server."
  type        = string
}

variable "backend_oidc_audience" {
  description = "Audience requested for delegated backend access tokens."
  type        = string
}

variable "backend_scope" {
  description = "Least-privilege backend scope requested during standard token exchange."
  type        = string
}

variable "resource_labels" {
  description = "Ownership labels applied to every managed Docker resource."
  type        = map(string)
  default     = {}
}
