#!/usr/bin/env bash
# shellcheck shell=bash

# The provider-default `personal` calendar may be created again on first
# Calendar access. Isolated failure-containment therefore uses a dedicated
# collection in its disposable provider namespace, while persistent/local
# installs retain their established backend-actor collection.
weave_backend_actor_workspace_calendar_id() {
  local isolated_namespace="${1:-}"

  if [[ -n "${isolated_namespace}" ]]; then
    printf '%s\n' 'weave-e2e-workspace'
    return
  fi
  printf '%s\n' 'personal'
}

weave_backend_actor_workspace_calendar_path() {
  local actor_username="$1"
  local isolated_namespace="${2:-}"
  local calendar_id

  calendar_id="$(weave_backend_actor_workspace_calendar_id "${isolated_namespace}")"
  printf '/remote.php/dav/calendars/%s/%s/\n' "${actor_username}" "${calendar_id}"
}
