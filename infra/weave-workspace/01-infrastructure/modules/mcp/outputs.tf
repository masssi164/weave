output "container_name" {
  description = "Container name for the Weave MCP server."
  value       = docker_container.this.name
}
