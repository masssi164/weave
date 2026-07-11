output "container_name" {
  description = "Mailpit container name."
  value       = docker_container.this.name
}

output "smtp_endpoint" {
  description = "Docker-network SMTP endpoint for dogfood services."
  value       = "${var.container_name}:1025"
}

output "web_endpoint" {
  description = "Loopback-only Mailpit operator inbox fallback URL."
  value       = "http://127.0.0.1:${var.web_host_port}"
}
