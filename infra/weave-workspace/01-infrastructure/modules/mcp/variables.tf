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

variable "oidc_required_scopes" {
  description = "Exact scope set accepted on inbound per-cell MCP access tokens."
  type        = list(string)
}

variable "authorization_server" {
  description = "Public Keycloak issuer advertised by RFC 9728 protected-resource metadata."
  type        = string
}

variable "resource_metadata_uri" {
  description = "Public RFC 9728 metadata URL advertised in bearer challenges."
  type        = string
}

variable "token_uri" {
  description = "Internal Keycloak token endpoint used for Standard Token Exchange V2."
  type        = string
}

variable "exchange_client_id" {
  description = "Confidential MCP-edge client ID used only as the token-exchange requester."
  type        = string
}

variable "exchange_secret_source" {
  description = "Permission-restricted host file containing the MCP-edge client credential."
  type        = string
  sensitive   = true
}

variable "exchange_secret_file" {
  description = "Read-only in-container path for the MCP-edge client credential."
  type        = string
}

variable "backend_resource" {
  description = "Exact HTTPS Weave API resource requested during token exchange."
  type        = string
}

variable "backend_context_uri" {
  description = "Docker-private backend route that resolves the current server-owned workload context."
  type        = string
}

variable "exchange_scopes" {
  description = "Exact downscoped backend scopes used by the active read-only MCP proof slice."
  type        = list(string)
}

variable "resource_labels" {
  description = "Ownership labels applied to every managed Docker resource."
  type        = map(string)
  default     = {}
}
