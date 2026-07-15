variable "network_name" {
  description = "Docker network name for the Matrix stack."
  type        = string
}

variable "mas_container_name" {
  description = "Container name for Matrix Authentication Service."
  type        = string
}

variable "synapse_container_name" {
  description = "Container name for Synapse."
  type        = string
}

variable "mas_image_name" {
  description = "Matrix Authentication Service image reference."
  type        = string
}

variable "synapse_image_name" {
  description = "Synapse image reference."
  type        = string
}

variable "synapse_volume_name" {
  description = "Docker volume name backing Synapse data."
  type        = string
}

variable "appservice_runtime_volume_name" {
  description = "Private Docker volume containing the generated Matrix Chat Application Service registration and token files."
  type        = string
}

variable "appservice_runtime_container_path" {
  description = "Read-only runtime path shared by Synapse and the Weave backend for Application Service files."
  type        = string
}

variable "appservice_registration_source" {
  description = "Host path to the private generated Synapse Application Service registration."
  type        = string
  sensitive   = true
}

variable "appservice_registration_hash" {
  description = "Sensitive content hash used only to refresh the private Application Service runtime volume."
  type        = string
  sensitive   = true
}

variable "appservice_as_token_source" {
  description = "Host path to the private generated Application Service as_token file."
  type        = string
  sensitive   = true
}

variable "appservice_as_token_hash" {
  description = "Sensitive content hash used only to refresh the private as_token runtime file."
  type        = string
  sensitive   = true
}

variable "appservice_hs_token_source" {
  description = "Host path to the private generated Application Service hs_token file."
  type        = string
  sensitive   = true
}

variable "appservice_hs_token_hash" {
  description = "Sensitive content hash used only to refresh the private hs_token runtime file."
  type        = string
  sensitive   = true
}

variable "mas_host_port" {
  description = "Direct host port exposed by Matrix Authentication Service."
  type        = number
}

variable "synapse_host_port" {
  description = "Direct host port exposed by Synapse."
  type        = number
}

variable "synapse_uid" {
  description = "UID used by Synapse for files inside the mounted data volume."
  type        = number
}

variable "synapse_gid" {
  description = "GID used by Synapse for files inside the mounted data volume."
  type        = number
}

variable "matrix_public_host" {
  description = "Browser-facing hostname for the Matrix entrypoint."
  type        = string
}

variable "mas_config_source" {
  description = "Path to the generated MAS config file."
  type        = string
}

variable "mas_config_hash" {
  description = "Content hash for the generated MAS config file."
  type        = string
}

variable "mas_signing_key_source" {
  description = "Path to the generated MAS signing key."
  type        = string
}

variable "mas_signing_key_hash" {
  description = "Content hash for the generated MAS signing key."
  type        = string
}

variable "synapse_config_source" {
  description = "Path to the generated Synapse homeserver config."
  type        = string
}

variable "synapse_config_hash" {
  description = "Content hash for the generated Synapse homeserver config."
  type        = string
}

variable "tls_ca_file" {
  description = "Path to the generated local TLS CA certificate trusted by MAS and Synapse outbound HTTPS calls."
  type        = string
}

variable "tls_ca_filename" {
  description = "Filename of the local CA certificate inside /certs."
  type        = string
}
