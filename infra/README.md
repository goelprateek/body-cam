# Infrastructure

The stack now uses the same split pattern as MeterManagement, but kept under `infra/`:

- `docker-compose.yml` for local development
- `docker-compose.prod.yml` for production
- `compose/local/*.yml` and `compose/prod/*.yml` for service-only fragments

Backend runtime config:

- local backend container env comes from `backend/.env.docker.local`
- production backend container env comes from `backend/.env.prod`
- `backend/.env.local` remains for direct backend runs outside Compose, such as IDE launches or local host execution

Local runs:

- `bash scripts/deploy/render-livekit-config.sh "$(pwd)" infra/.env.local backend/.env.docker.local infra/livekit.local.generated.yaml`
- `docker compose --env-file .env.local -f docker-compose.yml up -d --build`

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
- the local and production flows render `infra/livekit.local.generated.yaml` and `infra/livekit.prod.generated.yaml` from that shared template
- production keeps `rtc.use_external_ip: true` via `infra/.env.prod`, while local can set `LIVEKIT_USE_EXTERNAL_IP=false` and provide `LIVEKIT_NODE_IP` for same-WiFi device testing
- local same-WiFi real-device testing is driven by `TURN_HOST`, `LIVEKIT_USE_EXTERNAL_IP`, and `LIVEKIT_NODE_IP` in `infra/.env.local`
- MinIO's S3 API is exposed through Traefik on `https://<MINIO_DOMAIN>` while the MinIO console stays loopback-bound on port `9001`
- coturn stays directly published for TURN traffic

Production DNS and TLS contract:

- `LIVEKIT_DOMAIN` must resolve to the reverse proxy host in the same way as `API_DOMAIN` and `FRONTEND_DOMAIN`
- `LIVEKIT_PUBLIC_URL` must be the full `wss://...` URL for that same host because the backend returns it during session join
- `MINIO_DOMAIN` must resolve to the reverse proxy host when `MINIO_PUBLIC_URL` points at a public MinIO endpoint
- `MINIO_PUBLIC_URL` should normally be `https://<MINIO_DOMAIN>` so browser playback URLs resolve without exposing port `9000` directly
- `infra/.env.local` and `infra/.env.prod` should keep only infra and compose variables
- `backend/.env.docker.local` and `backend/.env.prod` should keep application runtime values, including JWT, LiveKit, storage, and backend DB connection settings
- the shared LiveKit template file is `infra/livekit.yaml.template`; rendered runtime files are generated per environment and should not be edited by hand

Production firewall ports:

- allow `80/tcp` and `443/tcp` for the reverse proxy entrypoints
- allow `3478/udp` and `3478/tcp` for coturn
- allow `7881/tcp` for LiveKit TCP media fallback
- allow `50000-50100/udp` for LiveKit RTP media
- do not expose `7880/tcp` publicly when Traefik is serving the LiveKit `wss://` endpoint
- keep PostgreSQL and MinIO loopback-only unless there is an explicit operational reason to publish them

The deploy workflow should sync only deployment assets:

- `infra/docker-compose.prod.yml`
- `infra/compose/`
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
