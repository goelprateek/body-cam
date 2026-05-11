#!/usr/bin/env bash

set -euo pipefail

repo_root="${APP_DIR:-$(pwd)}"
production_env_file="${PRODUCTION_ENV_FILE:-infra/.env.prod}"
backend_env_file="${BACKEND_ENV_FILE:-backend/.env.prod}"
compose_file="${repo_root}/infra/docker-compose.prod.yml"
env_file_path="${repo_root}/${production_env_file}"
backend_env_path="${repo_root}/${backend_env_file}"
livekit_render_script="${repo_root}/scripts/deploy/render-livekit-config.sh"
livekit_validate_script="${repo_root}/scripts/deploy/validate-livekit-config.sh"
deploy_action="${DEPLOY_ACTION:-deploy}"
release_label="${RELEASE_LABEL:-}"
state_file="${repo_root}/.deploy-state.env"
history_file="${repo_root}/.deploy-history"
current_backend_image_ref=""
current_frontend_image_ref=""
rollback_backend_image_ref=""
rollback_frontend_image_ref=""
rollback_candidates=()

read_key_value_from_file() {
  local file_path="${1:?file path is required}"
  local key="${2:?key is required}"

  if [[ ! -f "${file_path}" ]]; then
    return 0
  fi

  grep -E "^${key}=" "${file_path}" | tail -n 1 | cut -d '=' -f 2- || true
}

read_state_value() {
  read_key_value_from_file "${state_file}" "${1}"
}

capture_current_image_refs() {
  local backend_container_id=""
  local frontend_container_id=""

  backend_container_id="$(docker compose --env-file "${production_env_file}" -f "${compose_file}" ps -q backend 2>/dev/null || true)"
  frontend_container_id="$(docker compose --env-file "${production_env_file}" -f "${compose_file}" ps -q frontend 2>/dev/null || true)"

  if [[ -n "${backend_container_id}" ]]; then
    current_backend_image_ref="$(docker inspect --format '{{.Config.Image}}' "${backend_container_id}" 2>/dev/null || true)"
  fi

  if [[ -n "${frontend_container_id}" ]]; then
    current_frontend_image_ref="$(docker inspect --format '{{.Config.Image}}' "${frontend_container_id}" 2>/dev/null || true)"
  fi
}

add_rollback_candidate() {
  local backend_image_ref="${1:-}"
  local frontend_image_ref="${2:-}"
  local candidate=""
  local existing_candidate=""

  if [[ -z "${backend_image_ref}" || -z "${frontend_image_ref}" ]]; then
    return 0
  fi

  candidate="${backend_image_ref}|${frontend_image_ref}"

  for existing_candidate in "${rollback_candidates[@]}"; do
    if [[ "${existing_candidate}" == "${candidate}" ]]; then
      return 0
    fi
  done

  rollback_candidates+=("${candidate}")
}

load_rollback_candidates() {
  local history_entry=""
  local history_backend_image_ref=""
  local history_frontend_image_ref=""

  capture_current_image_refs
  add_rollback_candidate "${current_backend_image_ref}" "${current_frontend_image_ref}"
  add_rollback_candidate \
    "$(read_state_value DEPLOYED_BACKEND_IMAGE_REF)" \
    "$(read_state_value DEPLOYED_FRONTEND_IMAGE_REF)"

  if [[ -f "${history_file}" ]]; then
    while IFS= read -r history_entry; do
      history_backend_image_ref="${history_entry%%|*}"
      history_frontend_image_ref="${history_entry#*|}"
      add_rollback_candidate "${history_backend_image_ref}" "${history_frontend_image_ref}"
    done < "${history_file}"
  fi
}

select_rollback_candidate() {
  local excluded_backend_image_ref="${1:-}"
  local excluded_frontend_image_ref="${2:-}"
  local candidate=""
  local candidate_backend_image_ref=""
  local candidate_frontend_image_ref=""

  rollback_backend_image_ref=""
  rollback_frontend_image_ref=""

  for candidate in "${rollback_candidates[@]}"; do
    candidate_backend_image_ref="${candidate%%|*}"
    candidate_frontend_image_ref="${candidate#*|}"

    if [[ "${candidate_backend_image_ref}" == "${excluded_backend_image_ref}" && "${candidate_frontend_image_ref}" == "${excluded_frontend_image_ref}" ]]; then
      continue
    fi

    rollback_backend_image_ref="${candidate_backend_image_ref}"
    rollback_frontend_image_ref="${candidate_frontend_image_ref}"
    return 0
  done

  return 1
}

persist_deploy_state() {
  local history_entry=""

  printf 'DEPLOYED_BACKEND_IMAGE_REF=%q\n' "${BACKEND_IMAGE_REF}" > "${state_file}"
  printf 'DEPLOYED_FRONTEND_IMAGE_REF=%q\n' "${FRONTEND_IMAGE_REF}" >> "${state_file}"

  history_entry="${BACKEND_IMAGE_REF}|${FRONTEND_IMAGE_REF}"
  {
    printf '%s\n' "${history_entry}"
    if [[ -f "${history_file}" ]]; then
      grep -vFx "${history_entry}" "${history_file}" || true
    fi
  } | awk 'NF && !seen[$0]++' | head -n 5 > "${history_file}.tmp"

  mv "${history_file}.tmp" "${history_file}"
}

login_registry() {
  local registry_host="${CONTAINER_REGISTRY%%/*}"

  if [[ -z "${registry_host}" ]]; then
    echo "Could not determine registry host from CONTAINER_REGISTRY=${CONTAINER_REGISTRY}" >&2
    exit 1
  fi

  printf '%s' "${DIGITALOCEAN_ACCESS_TOKEN}" | docker login "${registry_host}" --username "${DIGITALOCEAN_ACCESS_TOKEN}" --password-stdin >/dev/null
}

wait_for_service_health() {
  local service_name="${1:?service name is required}"
  local timeout_seconds="${2:-180}"
  local deadline=$((SECONDS + timeout_seconds))
  local container_id=""
  local health_status=""

  while (( SECONDS < deadline )); do
    container_id="$(docker compose --env-file "${production_env_file}" -f "${compose_file}" ps -q "${service_name}" 2>/dev/null || true)"
    if [[ -n "${container_id}" ]]; then
      health_status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${container_id}" 2>/dev/null || true)"
      if [[ "${health_status}" == "healthy" || "${health_status}" == "running" ]]; then
        echo "Service ${service_name} is ${health_status}"
        return 0
      fi
    fi
    sleep 5
  done

  echo "Service ${service_name} did not become healthy within ${timeout_seconds}s." >&2
  docker compose --env-file "${production_env_file}" -f "${compose_file}" ps >&2 || true
  return 1
}

apply_compose_stack() {
  local services=()
  local rendered_livekit_config="${repo_root}/infra/livekit.prod.generated.yaml"

  bash "${livekit_render_script}" "${repo_root}" "${production_env_file}" "${backend_env_file}" "${rendered_livekit_config}"
  bash "${livekit_validate_script}" "${repo_root}" "${production_env_file}" "${backend_env_file}" "${rendered_livekit_config}"

  mapfile -t services < <(
    docker compose --env-file "${production_env_file}" -f "${compose_file}" config --services
  )

  docker compose --env-file "${production_env_file}" -f "${compose_file}" pull "${services[@]}"
  docker compose --env-file "${production_env_file}" -f "${compose_file}" up -d --remove-orphans
  wait_for_service_health postgres 180
  wait_for_service_health redis 180
  wait_for_service_health minio 180
  wait_for_service_health livekit 180
  wait_for_service_health vosk 180
  wait_for_service_health backend 240
  docker compose --env-file "${production_env_file}" -f "${compose_file}" ps
}

perform_rollback() {
  if ! select_rollback_candidate "${current_backend_image_ref}" "${current_frontend_image_ref}"; then
    echo "No previous successful deployment is available for rollback." >&2
    exit 1
  fi

  export BACKEND_IMAGE_REF="${rollback_backend_image_ref}"
  export FRONTEND_IMAGE_REF="${rollback_frontend_image_ref}"

  echo "Rolling back backend to ${BACKEND_IMAGE_REF}" >&2
  echo "Rolling back frontend to ${FRONTEND_IMAGE_REF}" >&2

  apply_compose_stack
  persist_deploy_state
}

if [[ ! -f "${compose_file}" ]]; then
  echo "Missing compose file: ${compose_file}" >&2
  exit 1
fi

if [[ ! -f "${env_file_path}" ]]; then
  echo "Missing production env file: ${env_file_path}" >&2
  exit 1
fi

if [[ ! -f "${backend_env_path}" ]]; then
  echo "Missing backend production env file: ${backend_env_path}" >&2
  exit 1
fi

if [[ ! -f "${livekit_render_script}" ]]; then
  echo "Missing LiveKit render script: ${livekit_render_script}" >&2
  exit 1
fi

if [[ ! -f "${livekit_validate_script}" ]]; then
  echo "Missing LiveKit validation script: ${livekit_validate_script}" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required on the production host." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required on the production host." >&2
  exit 1
fi

if ! docker network inspect proxy >/dev/null 2>&1; then
  echo "Missing external Docker network: proxy" >&2
  echo "Create it once with: docker network create proxy" >&2
  exit 1
fi

cd "${repo_root}"

set -a
# shellcheck disable=SC1090
source "${env_file_path}"
set +a

required_vars=(
  CONTAINER_REGISTRY
  DIGITALOCEAN_ACCESS_TOKEN
)

for var_name in "${required_vars[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    echo "Missing required environment variable: ${var_name}" >&2
    exit 1
  fi
done

login_registry
load_rollback_candidates

if [[ "${deploy_action}" == "rollback-last-successful" ]]; then
  perform_rollback
  exit 0
fi

required_image_vars=(
  BACKEND_IMAGE_REF
  FRONTEND_IMAGE_REF
)

for var_name in "${required_image_vars[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    echo "Missing required environment variable: ${var_name}" >&2
    exit 1
  fi
done

if [[ -n "${release_label}" ]]; then
  echo "Deploying release label ${release_label}"
fi

echo "Deploying backend image ref: ${BACKEND_IMAGE_REF}"
echo "Deploying frontend image ref: ${FRONTEND_IMAGE_REF}"

apply_compose_stack
persist_deploy_state
