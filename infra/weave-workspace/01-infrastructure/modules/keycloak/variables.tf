variable "network_name" {
  description = "Docker network name for Keycloak."
  type        = string
}

variable "container_name" {
  description = "Container name for Keycloak."
  type        = string
}

variable "image_name" {
  description = "Locally built Weave Keycloak image reference."
  type        = string
}

variable "image_build_context" {
  description = "Build context containing the version-pinned Keycloak image and Weave event provider."
  type        = string
}

variable "keycloak_version" {
  description = "Exact Keycloak version used by both the server image and event-provider compile dependency."
  type        = string
}

variable "volume_name" {
  description = "Docker volume name used for Keycloak data."
  type        = string
}

variable "host_port" {
  description = "Direct host port exposed by Keycloak application HTTP."
  type        = number
}

variable "management_host_port" {
  description = "Direct host port exposed by Keycloak management HTTP for health and metrics."
  type        = number
}

variable "public_url" {
  description = "Browser-facing URL for Keycloak."
  type        = string
}

variable "db_host" {
  description = "PostgreSQL host reachable from the container network."
  type        = string
}

variable "db_port" {
  description = "PostgreSQL port reachable from the container network."
  type        = number
}

variable "db_name" {
  description = "Database name used by Keycloak."
  type        = string
}

variable "db_schema" {
  description = "Database schema used by Keycloak."
  type        = string
}

variable "db_username" {
  description = "Database username used by Keycloak."
  type        = string
}

variable "db_password" {
  description = "Database password used by Keycloak."
  type        = string
  sensitive   = true
}

variable "admin_username" {
  description = "Bootstrap Keycloak admin username."
  type        = string
}

variable "admin_password" {
  description = "Bootstrap Keycloak admin password."
  type        = string
  sensitive   = true
}

variable "identity_events_endpoint" {
  description = "Docker-internal Weave endpoint receiving signed organization-membership events."
  type        = string
}

variable "identity_events_hmac_secret" {
  description = "HMAC secret shared only by Keycloak and Weave Server for identity event delivery."
  type        = string
  sensitive   = true
}

variable "resource_labels" {
  description = "Ownership labels applied to every managed Docker resource."
  type        = map(string)
  default     = {}
}
