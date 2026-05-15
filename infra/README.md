# Infrastructure

The stack now uses the same split pattern as MeterManagement, but kept under `infra/`:

- `docker-compose.yml` for local infrastructure services only
- `docker-compose.prod.yml` for production
- `compose/local/db.yml` and `compose/prod/db.yml` for data-layer services like PostgreSQL and Redis
- `compose/local/infra.yml` and `compose/prod/infra.yml` for infrastructure services like MinIO, LiveKit, LiveKit config rendering, coturn, and transcript services such as `faster-whisper`
  - the local stack also includes a `vosk` service on port `2700` so transcript engine switching is easy during development
  - each faster-whisper stack also includes a one-shot `faster-whisper-model-sync` container that pre-downloads the selected model into the shared `transcript_data` volume only when the real HuggingFace cache directory for that model is not already present
- `compose/local/app.yml` and `compose/prod/app.yml` for backend and frontend services

Backend runtime config:

- local Docker runs do not start backend or frontend containers
- production backend container env comes from `backend/.env.prod`
- `backend/.env.local` is for direct backend runs outside Compose, such as IDE launches or local host execution
- `infra/.env.local` is only for local Compose infrastructure services like PostgreSQL, Redis, MinIO, LiveKit, and coturn
  - plus transcript image and publishing settings such as `TRANSCRIPT_IMAGE`, `TRANSCRIPT_PORT`, `TRANSCRIPT_MODEL`, `TRANSCRIPT_LOCAL_ONLY`, `VOSK_IMAGE`, and `VOSK_PORT`

Local runs:

- infrastructure only: `docker compose --env-file .env.local -f docker-compose.yml up -d`
  - this now starts the local `faster-whisper` service, exposed on `http://localhost:${TRANSCRIPT_PORT}/v1/audio/transcriptions`
  - it also starts local `vosk`, exposed on `ws://localhost:${VOSK_PORT}`
  - before runtime starts, `faster-whisper-model-sync` downloads `${TRANSCRIPT_MODEL}` into `transcript_data`
- backend on host: run from `backend/` using `backend/.env.local`
- frontend on host: run from `frontend/` against the host-run backend
- optional full local compose app stack remains defined in `compose/local/app.yml` under the `app` profile and can be started with `docker compose --env-file .env.local -f docker-compose.yml --profile app up -d`
  - in that mode the backend reaches `faster-whisper` over the internal Compose hostname `http://faster-whisper:9000/v1/audio/transcriptions`
  - if you switch `APP_TRANSCRIPT_ENGINE=vosk`, the backend can use `ws://vosk:2700` without extra compose edits
  - runtime `TRANSCRIPT_LOCAL_ONLY=true` can stay enabled because model warmup happens in the sync container

Production runs:

- `docker compose --env-file .env.prod -f docker-compose.prod.yml up -d`

Production-specific behavior:

- backend and frontend are attached to the external `proxy` network
- Traefik labels live only in `compose/prod/app.yml`
- PostgreSQL and MinIO are published to loopback by default
- LiveKit joins the external `proxy` network in production so `wss://<LIVEKIT_DOMAIN>` is reachable by Android and browser clients
- LiveKit signaling on port `7880` is loopback-bound by default and should be reached through Traefik, not directly from the internet
- LiveKit still keeps its RTC TCP and UDP ports published directly for media traffic
- `infra/livekit.yaml.template` is the single source for both local and production LiveKit config generation
- local and production Compose now render the runtime LiveKit config from `infra/livekit.yaml.template` through a one-shot `livekit-config` service before the `livekit` container starts
- production keeps `rtc.use_external_ip: true` via `infra/.env.prod`, while local can set `LIVEKIT_USE_EXTERNAL_IP=false` and provide `LIVEKIT_NODE_IP` for same-WiFi device testing
- local same-WiFi real-device testing is driven by `TURN_HOST`, `LIVEKIT_USE_EXTERNAL_IP`, and `LIVEKIT_NODE_IP` in `infra/.env.local`
- the host-run backend should publish a phone-reachable LiveKit join URL through `LIVEKIT_PUBLIC_URL` in `backend/.env.local`, for example `ws://192.168.x.x:7880`
- `LIVEKIT_UDP_PORT_RANGE` is now the single source for both Docker UDP publishing and the rendered LiveKit RTC port range; if Windows blocks a port in the default range, move the whole range together to a safer slice
- the local sample now uses `52000-52020` to reduce Windows UDP bind conflicts during development; production can keep a wider range where the host is under your control
- MinIO's S3 API is exposed through Traefik on `https://<MINIO_DOMAIN>`
- MinIO's web console can be exposed separately through Traefik on `https://<MINIO_CONSOLE_DOMAIN>` while the container port `9001` stays loopback-bound on the host
- coturn stays directly published for TURN traffic

Production DNS and TLS contract:

- `LIVEKIT_DOMAIN` must resolve to the reverse proxy host in the same way as `API_DOMAIN` and `FRONTEND_DOMAIN`
- `LIVEKIT_PUBLIC_URL` must be the full `wss://...` URL for that same host because the backend returns it during session join
- `MINIO_DOMAIN` must resolve to the reverse proxy host when `MINIO_PUBLIC_URL` points at a public MinIO endpoint
- `MINIO_CONSOLE_DOMAIN` should resolve to the same reverse proxy host when the MinIO console is meant to be reachable from the browser
- `MINIO_PUBLIC_URL` should normally be `https://<MINIO_DOMAIN>` so browser playback URLs resolve without exposing port `9000` directly
- `MINIO_REGION` should stay aligned between environments; for the current MinIO setup we default it to `us-east-1` so presigned playback URL generation does not depend on runtime region discovery through the public endpoint
- `infra/.env.local` and `infra/.env.prod` should keep only infra and compose variables
- `backend/.env.local` is host-run only
- `backend/.env.prod` keeps production backend runtime values, including JWT, LiveKit, storage, and backend DB connection settings
- `infra/.env.prod` should carry the production image selection contract through `BACKEND_IMAGE_REF` and `FRONTEND_IMAGE_REF`; the deploy workflow overrides those to immutable digests during rollout
- production backend logs are written to `/app/logs` inside the container and persisted through the `backend_logs` volume mounted by `compose/prod/app.yml`
- the shared LiveKit template file is `infra/livekit.yaml.template`; the rendered runtime file now lives inside the `livekit_config` Docker volume rather than as a required host file
- production deployment now prevalidates the rendered LiveKit config against `LIVEKIT_UDP_PORT_RANGE` and the compose UDP mapping before any stack update proceeds

Production firewall ports:

- allow `80/tcp` and `443/tcp` for the reverse proxy entrypoints
- allow `3478/udp` and `3478/tcp` for coturn
- allow `7881/tcp` for LiveKit TCP media fallback
- allow `50000-50100/udp` for LiveKit RTP media
- do not expose `7880/tcp` publicly when Traefik is serving the LiveKit `wss://` endpoint
- keep PostgreSQL and MinIO loopback-only unless there is an explicit operational reason to publish them

The deploy workflow should sync only deployment assets:

- `infra/docker-compose.prod.yml`
- `infra/compose/prod/`
- `infra/livekit.yaml.template`
- `scripts/deploy/`

GitHub Actions production deployment expects these repository variables:

- `APP_DIR`
- `CONTAINER_REGISTRY`
- `SSH_HOST`
- `SSH_PORT`
- `SSH_USER`
- `DEPLOY_ENV_FILE`

And these repository secrets:

- `SSH_PRIVATE_KEY`
- `SSH_KNOWN_HOSTS`
- `DIGITALOCEAN_ACCESS_TOKEN`

`DIGITALOCEAN_ACCESS_TOKEN` should be provided by the GitHub repository or environment secret, not committed in `infra/.env.prod`.
