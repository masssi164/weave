variable "network_name" {
  description = "Docker network name for Mailpit."
  type        = string
}

variable "container_name" {
  description = "Container name for Mailpit."
  type        = string
}

variable "image_name" {
  description = "Mailpit image reference."
  type        = string
}

variable "volume_name" {
  description = "Docker volume name backing the persistent Mailpit SQLite database."
  type        = string
}

variable "max_messages" {
  description = "Maximum number of messages retained in the dogfood Mailpit inbox."
  type        = number

  validation {
    condition     = var.max_messages > 0
    error_message = "max_messages must be greater than zero."
  }
}

variable "web_host_port" {
  description = "Loopback-only host port for the Mailpit web/API inbox."
  type        = number
}

variable "resource_labels" {
  description = "Ownership labels applied to every managed Docker resource."
  type        = map(string)
  default     = {}
}
