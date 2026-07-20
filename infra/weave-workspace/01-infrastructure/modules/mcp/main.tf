terraform {
  required_providers {
    docker = {
      source = "kreuzwerker/docker"
    }
  }
}

resource "docker_image" "this" {
  name         = var.image_name
  keep_locally = true
}

resource "docker_container" "this" {
  name    = var.container_name
  image   = docker_image.this.image_id
  restart = "unless-stopped"

  dynamic "labels" {
    for_each = var.resource_labels
    content {
      label = labels.key
      value = labels.value
    }
  }
  depends_on = [
    docker_image.this,
  ]

  env = [
    "WEAVE_MCP_PORT=${var.container_port}",
    "WEAVE_SERVER_BASE_URL=${var.backend_base_url}",
    "WEAVE_OIDC_ISSUER_URI=${var.oidc_issuer_uri}",
    "WEAVE_OIDC_AUDIENCE=${var.oidc_required_audience}",
    "WEAVE_OIDC_TOKEN_URI=${var.oidc_token_uri}",
    "SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=${var.oidc_jwk_set_uri}",
    "WEAVE_MCP_CLIENT_ID=${var.mcp_client_id}",
    "WEAVE_MCP_CLIENT_SECRET=${var.mcp_client_secret}",
    "WEAVE_MCP_INBOUND_AUTHORIZED_PARTY=${var.inbound_authorized_party}",
    "WEAVE_BACKEND_OIDC_AUDIENCE=${var.backend_oidc_audience}",
    "WEAVE_MCP_BACKEND_SCOPE=${var.backend_scope}",
  ]

  ports {
    internal = var.container_port
    external = var.host_port
    ip       = "127.0.0.1"
  }

  healthcheck {
    test = [
      "CMD-SHELL",
      "curl -fsS http://127.0.0.1:${var.container_port}/actuator/health >/dev/null || exit 1",
    ]
    interval     = "10s"
    timeout      = "5s"
    retries      = 12
    start_period = "30s"
  }

  networks_advanced {
    name    = var.network_name
    aliases = [var.container_name]
  }

  lifecycle {
    ignore_changes = [
      cpu_shares,
      dns,
      dns_opts,
      dns_search,
      entrypoint,
      group_add,
      hostname,
      init,
      ipc_mode,
      log_driver,
      log_opts,
      max_retry_count,
      memory,
      memory_swap,
      privileged,
      publish_all_ports,
      runtime,
      security_opts,
      shm_size,
      stop_signal,
      stop_timeout,
      storage_opts,
      sysctls,
      tmpfs,
      user,
      working_dir,
    ]
  }
}
