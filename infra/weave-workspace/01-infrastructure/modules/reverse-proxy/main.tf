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
  name = var.data_volume_name
}

resource "docker_volume" "config" {
  name = var.config_volume_name
}

resource "docker_container" "this" {
  name    = var.container_name
  image   = docker_image.this.image_id
  restart = "unless-stopped"
  depends_on = [
    docker_image.this,
    docker_volume.data,
    docker_volume.config,
  ]

  ports {
    internal = 80
    external = var.http_host_port
  }

  ports {
    internal = 443
    external = var.https_host_port
  }

  upload {
    file        = "/etc/caddy/Caddyfile"
    content     = var.caddyfile_content
    source_hash = sha256(var.caddyfile_content)
  }

  upload {
    file        = "/certs/${basename(var.tls_cert_file)}"
    source      = var.tls_cert_file
    source_hash = filesha256(var.tls_cert_file)
  }

  upload {
    file        = "/certs/${basename(var.tls_key_file)}"
    source      = var.tls_key_file
    source_hash = filesha256(var.tls_key_file)
  }

  upload {
    file        = "/certs/${basename(var.tls_ca_file)}"
    source      = var.tls_ca_file
    source_hash = filesha256(var.tls_ca_file)
  }

  volumes {
    volume_name    = docker_volume.data.name
    container_path = "/data"
  }

  volumes {
    volume_name    = docker_volume.config.name
    container_path = "/config"
  }

  networks_advanced {
    name    = var.network_name
    aliases = distinct(concat([var.container_name], values(var.public_hosts)))
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
