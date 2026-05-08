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
- LiveKit and coturn stay directly published for media traffic

The deploy workflow should sync only deployment assets:

- `infra/docker-compose.prod.yml`
- `infra/compose/`
- `infra/livekit.yaml`
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
