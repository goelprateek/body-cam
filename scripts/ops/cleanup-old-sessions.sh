#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="${APP_DIR:-$(cd "${script_dir}/../.." && pwd)}"
production_env_file="${PRODUCTION_ENV_FILE:-infra/.env.prod}"
backend_env_file="${BACKEND_ENV_FILE:-backend/.env.prod}"
compose_file="${repo_root}/infra/docker-compose.prod.yml"
sql_cleanup_file="${repo_root}/scripts/db/cleanup-old-sessions.sql"
private_network_name="${PRIVATE_NETWORK_NAME:-bodycam_private}"
retention_days="${RETENTION_DAYS:-30}"
batch_size="${BATCH_SIZE:-200}"
apply_changes="false"

usage() {
  cat <<'EOF'
Usage:
  scripts/ops/cleanup-old-sessions.sh [--days N] [--batch-size N] [--apply]

Behavior:
  - targets only sessions whose status is ENDED
  - uses COALESCE(ended_at, created_at) as the retention cutoff anchor
  - deletes recording/session rows from Postgres
  - deletes matching MinIO objects for those sessions
  - defaults to dry-run mode unless --apply is provided
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --days)
      retention_days="${2:?--days requires a value}"
      shift 2
      ;;
    --batch-size)
      batch_size="${2:?--batch-size requires a value}"
      shift 2
      ;;
    --apply)
      apply_changes="true"
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ ! "${retention_days}" =~ ^[0-9]+$ ]]; then
  echo "RETENTION_DAYS must be a non-negative integer." >&2
  exit 1
fi

if [[ ! "${batch_size}" =~ ^[1-9][0-9]*$ ]]; then
  echo "BATCH_SIZE must be a positive integer." >&2
  exit 1
fi

if [[ ! -f "${compose_file}" ]]; then
  echo "Missing compose file: ${compose_file}" >&2
  exit 1
fi

if [[ ! -f "${repo_root}/${production_env_file}" ]]; then
  echo "Missing production env file: ${repo_root}/${production_env_file}" >&2
  exit 1
fi

if [[ ! -f "${repo_root}/${backend_env_file}" ]]; then
  echo "Missing backend env file: ${repo_root}/${backend_env_file}" >&2
  exit 1
fi

if [[ ! -f "${sql_cleanup_file}" ]]; then
  echo "Missing SQL cleanup file: ${sql_cleanup_file}" >&2
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

if ! docker network inspect "${private_network_name}" >/dev/null 2>&1; then
  echo "Missing Docker network: ${private_network_name}" >&2
  exit 1
fi

set -a
. "${repo_root}/${production_env_file}"
. "${repo_root}/${backend_env_file}"
set +a

: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"
: "${MINIO_ROOT_USER:?MINIO_ROOT_USER is required}"
: "${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD is required}"
: "${MINIO_BUCKET:?MINIO_BUCKET is required}"

psql_query() {
  local sql="${1:?sql is required}"

  docker compose --env-file "${production_env_file}" -f "${compose_file}" exec -T \
    -e PGPASSWORD="${POSTGRES_PASSWORD}" \
    postgres \
    psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -v ON_ERROR_STOP=1 -At -F $'\t' -c "${sql}"
}

candidate_sql="
with candidate_sessions as (
    select
        session_row.id,
        session_row.reference_number,
        session_row.worker_name,
        coalesce(session_row.ended_at, session_row.created_at) as retention_anchor
    from live_session session_row
    where session_row.status = 'ENDED'
      and coalesce(session_row.ended_at, session_row.created_at) < current_timestamp - interval '${retention_days} days'
    order by coalesce(session_row.ended_at, session_row.created_at), session_row.id
    limit ${batch_size}
)
select
    candidate.id,
    candidate.reference_number,
    candidate.worker_name,
    to_char(candidate.retention_anchor at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"'),
    (select count(*) from recording_asset asset where asset.session_id = candidate.id),
    (select count(*) from recording_transcript transcript join recording_asset asset on asset.id = transcript.recording_id where asset.session_id = candidate.id),
    (select count(*) from session_recording_export export_row where export_row.session_id = candidate.id),
    (select count(*) from session_invite invite where invite.session_id = candidate.id)
from candidate_sessions candidate;
"

mapfile -t candidate_rows < <(psql_query "${candidate_sql}")

if [[ ${#candidate_rows[@]} -eq 0 ]]; then
  echo "No ended sessions are older than ${retention_days} days. Nothing to clean."
  exit 0
fi

session_ids=()
summary_lines=()

for row in "${candidate_rows[@]}"; do
  IFS=$'\t' read -r session_id reference_number worker_name retention_anchor recording_count transcript_count export_count invite_count <<< "${row}"
  session_ids+=("${session_id}")
  summary_lines+=("${session_id}|${reference_number}|${worker_name}|${retention_anchor}|${recording_count}|${transcript_count}|${export_count}|${invite_count}")
done

session_ids_csv="$(IFS=,; echo "${session_ids[*]}")"

aggregate_sql="
with cleanup_sessions as (
    select unnest(string_to_array('${session_ids_csv}', ','))::uuid as session_id
)
select
    (select count(*) from cleanup_sessions),
    (select count(*) from recording_asset asset join cleanup_sessions candidate on candidate.session_id = asset.session_id),
    (select count(*) from recording_metadata metadata join recording_asset asset on asset.id = metadata.recording_id join cleanup_sessions candidate on candidate.session_id = asset.session_id),
    (select count(*) from recording_transcript transcript join recording_asset asset on asset.id = transcript.recording_id join cleanup_sessions candidate on candidate.session_id = asset.session_id),
    (select count(*) from recording_transcript_segment segment join recording_transcript transcript on transcript.id = segment.transcript_id join recording_asset asset on asset.id = transcript.recording_id join cleanup_sessions candidate on candidate.session_id = asset.session_id),
    (select count(*) from session_recording_export export_row join cleanup_sessions candidate on candidate.session_id = export_row.session_id),
    (select count(*) from session_invite invite join cleanup_sessions candidate on candidate.session_id = invite.session_id);
"

aggregate_row="$(psql_query "${aggregate_sql}")"
IFS=$'\t' read -r total_sessions total_recordings total_metadata total_transcripts total_segments total_exports total_invites <<< "${aggregate_row}"

nonstandard_keys_sql="
with cleanup_sessions as (
    select unnest(string_to_array('${session_ids_csv}', ','))::uuid as session_id
)
select object_key
from (
    select asset.object_key
    from recording_asset asset
    join cleanup_sessions candidate on candidate.session_id = asset.session_id
    where asset.object_key not like ('sessions/' || asset.session_id::text || '/%')

    union

    select export_row.object_key
    from session_recording_export export_row
    join cleanup_sessions candidate on candidate.session_id = export_row.session_id
    where export_row.object_key is not null
      and export_row.object_key not like ('exports/sessions/' || export_row.session_id::text || '/%')
) unexpected_keys
order by object_key;
"

mapfile -t nonstandard_keys < <(psql_query "${nonstandard_keys_sql}")

echo "Cleanup candidate summary"
echo "  retention days : ${retention_days}"
echo "  batch size     : ${batch_size}"
echo "  mode           : $([[ "${apply_changes}" == "true" ]] && echo "APPLY" || echo "DRY RUN")"
echo "  sessions       : ${total_sessions}"
echo "  recordings     : ${total_recordings}"
echo "  metadata rows  : ${total_metadata}"
echo "  transcripts    : ${total_transcripts}"
echo "  transcript seg : ${total_segments}"
echo "  exports        : ${total_exports}"
echo "  invites        : ${total_invites}"

echo
printf '%-36s  %-18s  %-18s  %-20s  %10s  %11s  %7s  %7s\n' \
  "Session ID" "Reference" "Worker" "Retention Anchor UTC" "Recordings" "Transcripts" "Exports" "Invites"
for line in "${summary_lines[@]}"; do
  IFS='|' read -r session_id reference_number worker_name retention_anchor recording_count transcript_count export_count invite_count <<< "${line}"
  printf '%-36s  %-18s  %-18s  %-20s  %10s  %11s  %7s  %7s\n' \
    "${session_id}" "${reference_number}" "${worker_name}" "${retention_anchor}" \
    "${recording_count}" "${transcript_count}" "${export_count}" "${invite_count}"
done

if [[ ${#nonstandard_keys[@]} -gt 0 ]]; then
  echo
  echo "Nonstandard object keys that will also be removed explicitly:"
  for object_key in "${nonstandard_keys[@]}"; do
    echo "  ${object_key}"
  done
fi

if [[ "${apply_changes}" != "true" ]]; then
  echo
  echo "Dry run only. Re-run with --apply to remove the data and MinIO objects."
  exit 0
fi

cleanup_dir="$(mktemp -d)"
trap 'rm -rf "${cleanup_dir}"' EXIT

session_ids_file="${cleanup_dir}/session_ids.txt"
nonstandard_keys_file="${cleanup_dir}/nonstandard_keys.txt"

printf '%s\n' "${session_ids[@]}" > "${session_ids_file}"
if [[ ${#nonstandard_keys[@]} -gt 0 ]]; then
  printf '%s\n' "${nonstandard_keys[@]}" > "${nonstandard_keys_file}"
else
  : > "${nonstandard_keys_file}"
fi

echo
echo "Deleting MinIO objects from bucket ${MINIO_BUCKET}..."
docker run --rm \
  --network "${private_network_name}" \
  -e MINIO_ROOT_USER="${MINIO_ROOT_USER}" \
  -e MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD}" \
  -e MINIO_BUCKET="${MINIO_BUCKET}" \
  -v "${session_ids_file}:/work/session_ids.txt:ro" \
  -v "${nonstandard_keys_file}:/work/nonstandard_keys.txt:ro" \
  --entrypoint /bin/sh \
  minio/mc \
  -lc '
    set -euo pipefail
    mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null

    while IFS= read -r session_id; do
      [ -n "$session_id" ] || continue
      mc rm --force --recursive "local/$MINIO_BUCKET/sessions/$session_id" || true
      mc rm --force --recursive "local/$MINIO_BUCKET/exports/sessions/$session_id" || true
    done < /work/session_ids.txt

    while IFS= read -r object_key; do
      [ -n "$object_key" ] || continue
      mc rm --force "local/$MINIO_BUCKET/$object_key" || true
    done < /work/nonstandard_keys.txt
  '

echo "Deleting Postgres rows..."
docker compose --env-file "${production_env_file}" -f "${compose_file}" exec -T \
  -e PGPASSWORD="${POSTGRES_PASSWORD}" \
  postgres \
  psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -v ON_ERROR_STOP=1 \
  -v "session_ids_csv=${session_ids_csv}" \
  -f "/dev/stdin" < "${sql_cleanup_file}"

echo "Cleanup completed for ${total_sessions} ended sessions."
