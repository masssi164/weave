terraform {
  required_providers {
    docker = {
      source = "kreuzwerker/docker"
    }
  }
}

resource "docker_image" "mas" {
  name         = var.mas_image_name
  keep_locally = true
}

resource "docker_image" "synapse" {
  name         = var.synapse_image_name
  keep_locally = true
}

resource "docker_volume" "synapse_data" {
  name = var.synapse_volume_name

  dynamic "labels" {
    for_each = var.resource_labels
    content {
      label = labels.key
      value = labels.value
    }
  }
}

resource "docker_volume" "appservice_runtime" {
  name = var.appservice_runtime_volume_name

  dynamic "labels" {
    for_each = var.resource_labels
    content {
      label = labels.key
      value = labels.value
    }
  }
}

resource "terraform_data" "appservice_runtime" {
  triggers_replace = [
    var.appservice_registration_hash,
    var.appservice_as_token_hash,
    var.appservice_hs_token_hash,
    docker_volume.appservice_runtime.name,
  ]

  provisioner "local-exec" {
    interpreter = ["/bin/bash", "-c"]
    environment = {
      APPSERVICE_AS_TOKEN_SOURCE     = var.appservice_as_token_source
      APPSERVICE_HS_TOKEN_SOURCE     = var.appservice_hs_token_source
      APPSERVICE_REGISTRATION_SOURCE = var.appservice_registration_source
      APPSERVICE_RUNTIME_VOLUME      = docker_volume.appservice_runtime.name
    }
    command = <<-EOT
      set -euo pipefail

      docker run --rm -u 0:0 \
        -v "$${APPSERVICE_REGISTRATION_SOURCE}:/source/registration.yaml:ro" \
        -v "$${APPSERVICE_AS_TOKEN_SOURCE}:/source/as-token:ro" \
        -v "$${APPSERVICE_HS_TOKEN_SOURCE}:/source/hs-token:ro" \
        -v "$${APPSERVICE_RUNTIME_VOLUME}:/target" \
        --entrypoint /bin/sh \
        "${var.synapse_image_name}" \
        -c 'set -eu
            install -d -m 0700 /target
            install -m 0444 /source/registration.yaml /target/registration.yaml
            install -m 0444 /source/as-token /target/as-token
            install -m 0444 /source/hs-token /target/hs-token
            chmod 0555 /target
            test -s /target/registration.yaml
            test -s /target/as-token
            test -s /target/hs-token
            ! cmp -s /target/as-token /target/hs-token'
    EOT
  }

  depends_on = [
    docker_image.synapse,
    docker_volume.appservice_runtime,
  ]
}

resource "terraform_data" "synapse_volume_permissions" {
  triggers_replace = [
    docker_volume.synapse_data.name,
    var.synapse_image_name,
    var.synapse_uid,
    var.synapse_gid,
    var.matrix_public_host,
  ]

  provisioner "local-exec" {
    interpreter = ["/bin/bash", "-c"]
    environment = {
      SYNAPSE_VOLUME      = docker_volume.synapse_data.name
      SYNAPSE_UID         = tostring(var.synapse_uid)
      SYNAPSE_GID         = tostring(var.synapse_gid)
      SYNAPSE_SIGNING_KEY = "/data/${var.matrix_public_host}.signing.key"
    }
    command = <<-EOT
      set -euo pipefail

      docker run --rm -u 0:0 \
        -e SYNAPSE_UID="$${SYNAPSE_UID}" \
        -e SYNAPSE_GID="$${SYNAPSE_GID}" \
        -e SYNAPSE_SIGNING_KEY="$${SYNAPSE_SIGNING_KEY}" \
        -v "$${SYNAPSE_VOLUME}:/data" \
        --entrypoint /bin/sh \
        "${var.synapse_image_name}" \
        -c 'set -eu
            install -d -m 0750 -o "$${SYNAPSE_UID}" -g "$${SYNAPSE_GID}" /data /data/media_store
            chown -R "$${SYNAPSE_UID}:$${SYNAPSE_GID}" /data
            chmod 0750 /data /data/media_store
            if [ -e "$${SYNAPSE_SIGNING_KEY}" ]; then
              chown "$${SYNAPSE_UID}:$${SYNAPSE_GID}" "$${SYNAPSE_SIGNING_KEY}"
              chmod 0600 "$${SYNAPSE_SIGNING_KEY}"
            fi'

      docker run --rm -u "$${SYNAPSE_UID}:$${SYNAPSE_GID}" \
        -e SYNAPSE_SIGNING_KEY_CHECK="$${SYNAPSE_SIGNING_KEY}.weave-writable-check" \
        -v "$${SYNAPSE_VOLUME}:/data" \
        --entrypoint /bin/sh \
        "${var.synapse_image_name}" \
        -c 'set -eu
            test -d /data
            test -w /data
            test -d /data/media_store
            test -w /data/media_store
            : > "$${SYNAPSE_SIGNING_KEY_CHECK}"
            rm -f "$${SYNAPSE_SIGNING_KEY_CHECK}"'
    EOT
  }

  depends_on = [
    docker_image.synapse,
    docker_volume.synapse_data,
  ]
}

resource "docker_container" "mas" {
  name    = var.mas_container_name
  image   = docker_image.mas.image_id
  restart = "unless-stopped"

  dynamic "labels" {
    for_each = var.resource_labels
    content {
      label = labels.key
      value = labels.value
    }
  }
  command = ["server", "-c", "/config/config.yaml"]
  depends_on = [
    docker_image.mas,
  ]
  env = [
    "SSL_CERT_FILE=/certs/${var.tls_ca_filename}",
    "CURL_CA_BUNDLE=/certs/${var.tls_ca_filename}",
    "REQUESTS_CA_BUNDLE=/certs/${var.tls_ca_filename}",
  ]

  ports {
    internal = 8080
    external = var.mas_host_port
  }

  upload {
    file        = "/config/config.yaml"
    source      = var.mas_config_source
    source_hash = var.mas_config_hash
  }

  upload {
    file        = "/config/signing.key"
    source      = var.mas_signing_key_source
    source_hash = var.mas_signing_key_hash
  }

  upload {
    file        = "/certs/${var.tls_ca_filename}"
    source      = var.tls_ca_file
    source_hash = filesha256(var.tls_ca_file)
  }

  networks_advanced {
    name    = var.network_name
    aliases = [var.mas_container_name]
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

resource "docker_container" "synapse" {
  name    = var.synapse_container_name
  image   = docker_image.synapse.image_id
  restart = "unless-stopped"
  user    = "${var.synapse_uid}:${var.synapse_gid}"

  dynamic "labels" {
    for_each = var.resource_labels
    content {
      label = labels.key
      value = labels.value
    }
  }
  env = [
    "SYNAPSE_CONFIG_PATH=/config/homeserver.yaml",
    "SYNAPSE_SERVER_NAME=${var.matrix_public_host}",
    "SYNAPSE_REPORT_STATS=no",
    "SSL_CERT_FILE=/certs/${var.tls_ca_filename}",
    "CURL_CA_BUNDLE=/certs/${var.tls_ca_filename}",
    "REQUESTS_CA_BUNDLE=/certs/${var.tls_ca_filename}",
  ]

  ports {
    internal = 8008
    external = var.synapse_host_port
  }

  upload {
    file        = "/config/homeserver.yaml"
    source      = var.synapse_config_source
    source_hash = var.synapse_config_hash
  }

  volumes {
    volume_name    = docker_volume.synapse_data.name
    container_path = "/data"
  }

  volumes {
    volume_name    = docker_volume.appservice_runtime.name
    container_path = var.appservice_runtime_container_path
    read_only      = true
  }

  upload {
    file        = "/certs/${var.tls_ca_filename}"
    source      = var.tls_ca_file
    source_hash = filesha256(var.tls_ca_file)
  }

  networks_advanced {
    name    = var.network_name
    aliases = [var.synapse_container_name]
  }

  depends_on = [
    terraform_data.appservice_runtime,
    terraform_data.synapse_volume_permissions,
  ]

  lifecycle {
    ignore_changes = [
      cpu_shares,
      dns,
      dns_opts,
      dns_search,
      entrypoint,
      group_add,
      healthcheck,
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
      working_dir,
    ]
  }
}
