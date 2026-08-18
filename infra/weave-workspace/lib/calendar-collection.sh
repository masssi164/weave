#!/usr/bin/env bash
# shellcheck shell=bash

# The provider-default `personal` calendar may be created again on first
# Calendar access. Weave therefore owns an explicit workspace collection in
# every profile. Isolated runs suffix that collection with their run namespace
# so provider fault injection can never target persistent dogfood data.
weave_backend_actor_workspace_calendar_id() {
  local isolated_namespace="${1:-}"

  if [[ -n "${isolated_namespace}" ]]; then
    printf 'weave-workspace-%s\n' "${isolated_namespace}"
    return
  fi
  printf '%s\n' 'weave-workspace'
}

weave_backend_actor_workspace_calendar_path() {
  local actor_username="$1"
  local isolated_namespace="${2:-}"
  local calendar_id

  calendar_id="$(weave_backend_actor_workspace_calendar_id "${isolated_namespace}")"
  printf '/remote.php/dav/calendars/%s/%s/\n' "${actor_username}" "${calendar_id}"
}
