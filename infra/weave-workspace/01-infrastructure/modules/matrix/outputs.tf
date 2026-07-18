output "mas_container_name" {
  description = "Container name for Matrix Authentication Service."
  value       = docker_container.mas.name
}

output "synapse_container_name" {
  description = "Container name for Synapse."
  value       = docker_container.synapse.name
}

output "synapse_volume_name" {
  description = "Volume name backing Synapse data."
  value       = docker_volume.synapse_data.name
}

output "appservice_runtime_volume_name" {
  description = "Private runtime volume mounted read-only into Synapse and the Weave backend."
  value       = docker_volume.appservice_runtime.name
}
