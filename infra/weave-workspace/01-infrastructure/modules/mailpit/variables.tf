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

variable "web_host_port" {
  description = "Loopback-only host port for the Mailpit web/API inbox."
  type        = number
}
