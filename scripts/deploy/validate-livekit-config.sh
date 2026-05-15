#!/usr/bin/env bash

set -euo pipefail

repo_root="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
infra_env_file="${2:-infra/.env.prod}"
backend_env_file="${3:-backend/.env.prod}"
rendered_config_file="${4:?rendered LiveKit config path is required}"
compose_fragment_file="${5:-${repo_root}/infra/compose/prod/infra.yml}"

infra_env_path="${repo_root}/${infra_env_file}"
backend_env_path="${repo_root}/${backend_env_file}"

if [[ ! -f "${infra_env_path}" ]]; then
  echo "Missing infra env file: ${infra_env_path}" >&2
  exit 1
fi

if [[ ! -f "${backend_env_path}" ]]; then
  echo "Missing backend env file: ${backend_env_path}" >&2
  exit 1
fi

if [[ ! -f "${rendered_config_file}" ]]; then
  echo "Missing rendered LiveKit config file: ${rendered_config_file}" >&2
  exit 1
fi

if [[ ! -f "${compose_fragment_file}" ]]; then
  echo "Missing LiveKit compose fragment: ${compose_fragment_file}" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${infra_env_path}"
# shellcheck disable=SC1090
source "${backend_env_path}"
set +a

if [[ -z "${LIVEKIT_UDP_PORT_RANGE:-}" ]]; then
  echo "LIVEKIT_UDP_PORT_RANGE is required for LiveKit validation." >&2
  exit 1
fi

if [[ ! "${LIVEKIT_UDP_PORT_RANGE}" =~ ^([0-9]+)-([0-9]+)$ ]]; then
  echo "LIVEKIT_UDP_PORT_RANGE must be in <start>-<end> format, got: ${LIVEKIT_UDP_PORT_RANGE}" >&2
  exit 1
fi

expected_start="${BASH_REMATCH[1]}"
expected_end="${BASH_REMATCH[2]}"

rendered_start="$(awk -F': ' '/port_range_start:/ {print $2; exit}' "${rendered_config_file}")"
rendered_end="$(awk -F': ' '/port_range_end:/ {print $2; exit}' "${rendered_config_file}")"

if [[ -z "${rendered_start}" || -z "${rendered_end}" ]]; then
  echo "Rendered LiveKit config is missing rtc.port_range_start or rtc.port_range_end." >&2
  exit 1
fi

if [[ "${rendered_start}" != "${expected_start}" || "${rendered_end}" != "${expected_end}" ]]; then
  echo "Rendered LiveKit UDP range ${rendered_start}-${rendered_end} does not match LIVEKIT_UDP_PORT_RANGE=${LIVEKIT_UDP_PORT_RANGE}." >&2
  exit 1
fi

expected_mapping='${LIVEKIT_UDP_PORT_RANGE:-50000-50100}:${LIVEKIT_UDP_PORT_RANGE:-50000-50100}/udp'

if ! grep -Fq "${expected_mapping}" "${compose_fragment_file}"; then
  echo "LiveKit compose fragment does not use the symmetric LIVEKIT_UDP_PORT_RANGE host/container mapping." >&2
  exit 1
fi

echo "Validated LiveKit UDP range ${LIVEKIT_UDP_PORT_RANGE} against rendered config and compose fragment."
