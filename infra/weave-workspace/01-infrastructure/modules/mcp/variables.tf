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

variable "resource_labels" {
  description = "Ownership labels applied to every managed Docker resource."
  type        = map(string)
  default     = {}
}
