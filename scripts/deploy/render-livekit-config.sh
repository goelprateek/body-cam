#!/usr/bin/env bash

set -euo pipefail

repo_root="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
infra_env_file="${2:-infra/.env.prod}"
backend_env_file="${3:-backend/.env.prod}"
output_file="${4:-${repo_root}/infra/livekit.prod.generated.yaml}"
template_file="${repo_root}/infra/livekit.yaml.template"
infra_env_path="${repo_root}/${infra_env_file}"
backend_env_path="${repo_root}/${backend_env_file}"

if [[ ! -f "${template_file}" ]]; then
  echo "Missing LiveKit template file: ${template_file}" >&2
  exit 1
fi

if [[ ! -f "${infra_env_path}" ]]; then
  echo "Missing infra env file: ${infra_env_path}" >&2
  exit 1
fi

if [[ ! -f "${backend_env_path}" ]]; then
  echo "Missing backend env file: ${backend_env_path}" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${infra_env_path}"
# shellcheck disable=SC1090
source "${backend_env_path}"
set +a

required_vars=(
  LIVEKIT_USE_EXTERNAL_IP
  LIVEKIT_UDP_PORT_RANGE
  TURN_HOST
  TURN_USERNAME
  TURN_PASSWORD
  LIVEKIT_API_KEY
  LIVEKIT_API_SECRET
)

for var_name in "${required_vars[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    echo "Missing required environment variable for LiveKit config: ${var_name}" >&2
    exit 1
  fi
done

if [[ ! "${LIVEKIT_UDP_PORT_RANGE}" =~ ^([0-9]+)-([0-9]+)$ ]]; then
  echo "LIVEKIT_UDP_PORT_RANGE must be in <start>-<end> format, got: ${LIVEKIT_UDP_PORT_RANGE}" >&2
  exit 1
fi

livekit_udp_port_start="${BASH_REMATCH[1]}"
livekit_udp_port_end="${BASH_REMATCH[2]}"

if (( livekit_udp_port_start > livekit_udp_port_end )); then
  echo "LIVEKIT_UDP_PORT_RANGE start must be <= end, got: ${LIVEKIT_UDP_PORT_RANGE}" >&2
  exit 1
fi

node_ip_block=""
if [[ -n "${LIVEKIT_NODE_IP:-}" ]]; then
  node_ip_block="  node_ip: ${LIVEKIT_NODE_IP}"
fi

template_content="$(<"${template_file}")"
template_content="${template_content//'${LIVEKIT_UDP_PORT_START}'/${livekit_udp_port_start}}"
template_content="${template_content//'${LIVEKIT_UDP_PORT_END}'/${livekit_udp_port_end}}"
template_content="${template_content//'${LIVEKIT_USE_EXTERNAL_IP}'/${LIVEKIT_USE_EXTERNAL_IP}}"
template_content="${template_content//'${LIVEKIT_NODE_IP_BLOCK}'/${node_ip_block}}"
template_content="${template_content//'${TURN_HOST}'/${TURN_HOST}}"
template_content="${template_content//'${TURN_USERNAME}'/${TURN_USERNAME}}"
template_content="${template_content//'${TURN_PASSWORD}'/${TURN_PASSWORD}}"
template_content="${template_content//'${LIVEKIT_API_KEY}'/${LIVEKIT_API_KEY}}"
template_content="${template_content//'${LIVEKIT_API_SECRET}'/${LIVEKIT_API_SECRET}}"

printf '%s\n' "${template_content}" > "${output_file}"
