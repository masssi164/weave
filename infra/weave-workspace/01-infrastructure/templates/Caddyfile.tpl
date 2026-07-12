{
	admin off
	default_sni ${ca_bootstrap_host}
}

# Certificate bootstrap must work before DNS and trust are configured.
# Serve the local CA on the HTTP bootstrap port regardless of Host header so
# phones can fetch it via the Mac LAN IP as a break-glass path.
http:// {
	@local_ca path /weave-local-ca.pem
	handle @local_ca {
		root * /certs
		rewrite * /${tls_ca_filename}
		file_server
	}

	respond "Weave local dogfood HTTP endpoint. Download /weave-local-ca.pem, trust the Weave Local Development CA, then use https://${ca_bootstrap_host}." 200
}

http://${ca_bootstrap_host} {
	@local_ca path /weave-local-ca.pem
	handle @local_ca {
		root * /certs
		rewrite * /${tls_ca_filename}
		file_server
	}

	respond "Weave local dogfood HTTP endpoint. Download /weave-local-ca.pem, trust the Weave Local Development CA, then use https://${ca_bootstrap_host}." 200
}

${weave_site_addresses} {
	tls /certs/${tls_cert_filename} /certs/${tls_key_filename}
	encode zstd gzip

	@local_ca path /weave-local-ca.pem
	handle @local_ca {
		root * /certs
		rewrite * /${tls_ca_filename}
		file_server
	}

	@files path /files /files/*
	handle @files {
		respond "Weave files product route. Raw Nextcloud technical/admin/protocol fallback: ${nextcloud_public_url}" 200
	}

	@calendar path /calendar /calendar/*
	handle @calendar {
		respond "Weave calendar product route. Calendar data is served through the Weave backend facade." 200
	}

	@internal_product_api path /api/internal/*
	handle @internal_product_api {
		respond "Not Found" 404
	}

	@backend_actuator path /actuator /actuator/*
	handle @backend_actuator {
		respond "Not Found" 404
	}

	@product_api path /api/*
	handle @product_api {
		reverse_proxy ${api_upstream}
	}

	@app_start path / /join /join/ /start /start/
	handle @app_start {
		header Content-Type "text/html; charset=utf-8"
		respond `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Weave Local Dogfood start</title>
  <style>
    body { font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; line-height: 1.5; margin: 2rem auto; max-width: 52rem; padding: 0 1rem; color: #0f172a; background: #f8fafc; }
    main { background: white; border: 1px solid #cbd5e1; border-radius: 1rem; padding: 1.5rem; }
    a { color: #0f5ab8; }
    code { background: #e2e8f0; border-radius: .25rem; padding: .1rem .25rem; }
    .warning { border-left: .35rem solid #b45309; background: #fffbeb; padding: .75rem 1rem; }
  </style>
</head>
<body>
<main>
  <h1>Weave Local Dogfood start</h1>
  <p>Use this page on the Mac or iPhone when testing the local Weave stack for <strong>massimo-dogfood/home</strong>.</p>

  <section aria-labelledby="trust-ca">
    <h2 id="trust-ca">1. Trust the local development CA on iPhone</h2>
    <p>Download the Weave Local Development CA certificate:</p>
    <ul>
      <li><a href="${ca_bootstrap_url}/weave-local-ca.pem">${ca_bootstrap_url}/weave-local-ca.pem</a></li>
      <li><a href="${client_public_url}/weave-local-ca.pem">${client_public_url}/weave-local-ca.pem</a></li>
    </ul>
    <ol>
      <li>Open the certificate on the iPhone and install the profile when prompted.</li>
      <li>Open Settings, then General, then About, then Certificate Trust Settings.</li>
      <li>Enable full trust for <strong>Weave Local Development CA</strong>.</li>
      <li>Return to Safari or the Weave app and continue with the invite link.</li>
    </ol>
  </section>

  <section aria-labelledby="start-app">
    <h2 id="start-app">2. Start or join the app</h2>
    <p>Open the app-start discovery contract at <a href="${client_public_url}/api/platform/config">${client_public_url}/api/platform/config</a> if you need to verify product-gateway app-start, or at <a href="${api_public_url}/api/platform/config">${api_public_url}/api/platform/config</a> for the canonical API host.</p>
    <p>Default Massimo invite/join link: <a href="/join?handoff_ref=handoff-s32-massimo-dogfood-home&amp;org=massimo-dogfood&amp;workspace=home&amp;profile=local-lan-dogfood&amp;run_id=s32-massimo-dogfood">${client_public_url}/join?handoff_ref=handoff-s32-massimo-dogfood-home&amp;org=massimo-dogfood&amp;workspace=home&amp;profile=local-lan-dogfood&amp;run_id=s32-massimo-dogfood</a></p>
    <p>App custom-scheme link for installed-client handoff testing: <a href="weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&amp;org=massimo-dogfood&amp;workspace=home&amp;profile=local-lan-dogfood&amp;run_id=s32-massimo-dogfood&amp;product_base_url=${client_public_url}&amp;platform_config_url=${api_public_url}/api/platform/config">weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&amp;org=massimo-dogfood&amp;workspace=home&amp;profile=local-lan-dogfood&amp;run_id=s32-massimo-dogfood&amp;product_base_url=${client_public_url}&amp;platform_config_url=${api_public_url}/api/platform/config</a></p>
    <p>The QR payload should be the same DNS-first join URL. The app should fetch platform config, start sign-in, and land in workspace home.</p>
  </section>

  <section aria-labelledby="service-links">
    <h2 id="service-links">DNS-first service links</h2>
    <ul>
      <li>Product gateway: <a href="${client_public_url}/">${client_public_url}/</a></li>
      <li>API: <a href="${api_public_url}/api/health/ready">${api_public_url}/api/health/ready</a></li>
      <li>Auth: <a href="${auth_public_url}/realms/weave">${auth_public_url}/realms/weave</a></li>
      <li>Chat protocol facade: <a href="${matrix_facade_url}/_matrix/client/versions">${matrix_facade_url}/_matrix/client/versions</a></li>
      <li>Files fallback: <a href="${nextcloud_public_url}">${nextcloud_public_url}</a></li>
      <li>Product files route: <a href="/files">/files</a>; product calendar route: <a href="/calendar">/calendar</a>.</li>
    </ul>
  </section>

  <section aria-labelledby="secrets" class="warning">
    <h2 id="secrets">Secrets are not embedded here</h2>
	    <p>This page, invite link, and QR payload contain no passwords, tokens, client secrets, credential URLs, or activation action links. Account activation uses the identity-provider required-action flow; one-time action links belong only in the local Mailpit capture and must not be copied into docs, QR codes, logs, or app storage.</p>
  </section>
</main>
</body>
</html>` 200
	}

	handle {
		respond "Weave local product gateway" 200
	}
}

${api_site_addresses} {
	tls /certs/${tls_cert_filename} /certs/${tls_key_filename}
	encode zstd gzip

${connector_provider_callbacks_guard}
	@backend_actuator path /actuator /actuator/*
	handle @backend_actuator {
		respond "Not Found" 404
	}

	@internal_api path /api/internal/*
	handle @internal_api {
		respond "Not Found" 404
	}

	reverse_proxy ${api_upstream}
}

${admin_site_addresses} {
	tls /certs/${tls_cert_filename} /certs/${tls_key_filename}
	encode zstd gzip

	header Content-Type text/plain
	respond "Weave Organization/Admin Console deploy target. Build the React app from admin-console/ and configure it to call only the Weave backend admin APIs." 200
}

${auth_site_addresses} {
	tls /certs/${tls_cert_filename} /certs/${tls_key_filename}
	encode zstd gzip

	reverse_proxy ${keycloak_upstream}
}

${mail_site_addresses} {
	tls /certs/${tls_cert_filename} /certs/${tls_key_filename}
	encode zstd gzip

	%{ if mailpit_enabled ~}
	@private_network remote_ip ${mailpit_allowed_cidrs}
	handle @private_network {
		reverse_proxy ${mailpit_upstream} {
			header_up Host {host}
		}
	}

	respond "Forbidden" 403
	%{ else ~}
	respond "Not Found" 404
	%{ endif ~}
}

${matrix_site_addresses} {
	tls /certs/${tls_cert_filename} /certs/${tls_key_filename}
	encode zstd gzip

	@matrix_auth_metadata path /_matrix/client/v1/auth_metadata
	@matrix_auth path_regexp matrix_auth ^/_matrix/client/.*/(login|logout|refresh)$
	@matrix_client_well_known path /.well-known/matrix/client
	@synapse path /_matrix/* /_synapse/client/* /_synapse/mas/* /.well-known/matrix/*

	handle @matrix_auth_metadata {
		header Content-Type application/json
		respond `{"authorization_endpoint":"${matrix_public_url}/authorize","code_challenge_methods_supported":["S256"],"grant_types_supported":["authorization_code","refresh_token"],"issuer":"${matrix_public_url}/","registration_endpoint":"${matrix_public_url}/oauth2/registration","response_modes_supported":["query"],"response_types_supported":["code"],"revocation_endpoint":"${matrix_public_url}/oauth2/revoke","token_endpoint":"${matrix_public_url}/oauth2/token"}` 200
	}

	handle @matrix_auth {
		reverse_proxy ${mas_upstream}
	}

	handle @matrix_client_well_known {
		header Content-Type application/json
		respond `{"m.homeserver":{"base_url":"${matrix_public_url}"}}` 200
	}

	handle @synapse {
		reverse_proxy ${synapse_upstream}
	}

	handle {
		reverse_proxy ${mas_upstream}
	}
}

${files_site_addresses} {
	tls /certs/${tls_cert_filename} /certs/${tls_key_filename}
	encode zstd gzip

	reverse_proxy ${nextcloud_upstream} {
		# Caddy is the only trusted public gateway. Replace, rather than append,
		# client-controlled forwarding headers before Nextcloud evaluates them.
		header_up X-Forwarded-For {http.request.remote.host}
		header_up X-Forwarded-Host {host}
		header_up X-Forwarded-Proto {scheme}
	}
}
