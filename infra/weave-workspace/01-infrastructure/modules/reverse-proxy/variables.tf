variable "network_name" {
  description = "Docker network name for the reverse proxy."
  type        = string
}

variable "container_name" {
  description = "Container name for the reverse proxy."
  type        = string
}

variable "image_name" {
  description = "Caddy image reference."
  type        = string
}

variable "http_host_port" {
  description = "HTTP host port exposed by the reverse proxy."
  type        = number
}

variable "https_host_port" {
  description = "HTTPS host port exposed by the reverse proxy."
  type        = number
}

variable "caddyfile_path" {
  description = "Path to the generated Caddyfile."
  type        = string
}

variable "caddyfile_content" {
  description = "Generated Caddyfile content uploaded into the container."
  type        = string
}

variable "tls_cert_file" {
  description = "Path to the generated local TLS certificate."
  type        = string
}

variable "tls_key_file" {
  description = "Path to the generated local TLS private key."
  type        = string
}

variable "tls_ca_file" {
  description = "Path to the generated local TLS CA certificate."
  type        = string
}

variable "tls_certs_dir" {
  description = "Host directory containing generated local TLS material mounted into /certs."
  type        = string
}

variable "data_volume_name" {
  description = "Docker volume name used for Caddy runtime data."
  type        = string
}

variable "config_volume_name" {
  description = "Docker volume name used for Caddy runtime config."
  type        = string
}

variable "public_hosts" {
  description = "Public hostnames that should resolve to the reverse proxy on the Docker network."
  type        = map(string)
}
