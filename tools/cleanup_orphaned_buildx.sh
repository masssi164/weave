#!/usr/bin/env bash
set -euo pipefail

container_ids=()
while IFS= read -r container_id; do
  [[ -n "$container_id" ]] && container_ids+=("$container_id")
done < <(docker ps -aq --filter 'name=^/buildx_buildkit_builder-')

if (( ${#container_ids[@]} > 0 )); then
  docker rm -f "${container_ids[@]}" >/dev/null
fi

volume_names=()
while IFS= read -r volume_name; do
  [[ -n "$volume_name" ]] && volume_names+=("$volume_name")
done < <(docker volume ls -q --filter 'name=^buildx_buildkit_builder-')

if (( ${#volume_names[@]} > 0 )); then
  docker volume rm "${volume_names[@]}" >/dev/null
fi

printf 'Removed orphaned Buildx resources: containers=%d volumes=%d\n' \
  "${#container_ids[@]}" "${#volume_names[@]}"
