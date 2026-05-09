# Infrastructure

The stack now uses the same split pattern as MeterManagement, but kept under `infra/`:

- `docker-compose.yml` for local development
- `docker-compose.prod.yml` for production
- `compose/local/*.yml` and `compose/prod/*.yml` for service-only fragments

Backend runtime config:

- local backend container env comes from `backend/.env.local`
- production backend container env comes from `backend/.env.prod`
- the backend service now uses Compose `env_file` instead of an inline `environment:` block

Local runs:

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
- `infra/livekit.prod.yaml` enables `rtc.use_external_ip: true` so LiveKit advertises the host public IP instead of Docker-internal addresses during ICE negotiation
- `infra/livekit.prod.yaml` also advertises the public TURN server on `turn-bodycam.idti.dev:3478` so browser and mobile clients receive coturn fallback candidates during session join
- `infra/livekit.local.yaml` also enables `rtc.use_external_ip: true` for real-device testing against a locally Dockerized LiveKit server that is reached through the host public IP
- MinIO's S3 API is exposed through Traefik on `https://<MINIO_DOMAIN>` while the MinIO console stays loopback-bound on port `9001`
- coturn stays directly published for TURN traffic

Production DNS and TLS contract:

- `LIVEKIT_DOMAIN` must resolve to the reverse proxy host in the same way as `API_DOMAIN` and `FRONTEND_DOMAIN`
- `LIVEKIT_PUBLIC_URL` must be the full `wss://...` URL for that same host because the backend returns it during session join
- `MINIO_DOMAIN` must resolve to the reverse proxy host when `MINIO_PUBLIC_URL` points at a public MinIO endpoint
- `MINIO_PUBLIC_URL` should normally be `https://<MINIO_DOMAIN>` so browser playback URLs resolve without exposing port `9000` directly
- `backend/.env.prod` and `infra/.env.prod` must agree on `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, and the public LiveKit host
- production Compose now injects `LIVEKIT_PUBLIC_URL`, `LIVEKIT_API_KEY`, and `LIVEKIT_API_SECRET` into the backend container from `infra/.env.prod`
- production Compose also injects `MINIO_PUBLIC_URL`, `MINIO_BUCKET`, and MinIO credentials into the backend container from `infra/.env.prod`
- the production LiveKit config file is `infra/livekit.prod.yaml`; keep its `keys:` block aligned with the backend and infra env files before deploy

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
- `infra/livekit.prod.yaml`
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
