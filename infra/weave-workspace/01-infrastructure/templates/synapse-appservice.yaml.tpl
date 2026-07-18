id: "${appservice_id}"
url: "${appservice_callback_url}"
as_token: "${appservice_as_token}"
hs_token: "${appservice_hs_token}"
sender_localpart: "${appservice_sender_localpart}"
rate_limited: true
receive_ephemeral: false

namespaces:
  users:
    - exclusive: true
      regex: '^@${virtual_user_prefix}[a-z0-9]{26,64}:${matrix_homeserver_regex}$'
  aliases:
    - exclusive: true
      regex: '^#${virtual_user_prefix}[a-z0-9]{26,64}:${matrix_homeserver_regex}$'
  rooms: []
