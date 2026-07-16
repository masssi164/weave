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

resource "docker_volume" "data" {
  name = var.volume_name

  dynamic "labels" {
    for_each = var.resource_labels
    content {
      label = labels.key
      value = labels.value
    }
  }
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
    docker_volume.data,
  ]

  env = [
    "MP_DATABASE=/data/mailpit.db",
    "MP_MAX_MESSAGES=${var.max_messages}",
  ]

  volumes {
    volume_name    = docker_volume.data.name
    container_path = "/data"
  }

  ports {
    internal = 8025
    external = var.web_host_port
    ip       = "127.0.0.1"
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
