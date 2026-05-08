# Body Cam MVP

`body-cam` is a remote assistance MVP for field workers and backoffice operators. A worker streams live audio and video from Android, the backend brokers auth and session metadata, and the operator console joins the LiveKit room for near real-time monitoring and replay.

## Repository Layout

- `android-app/` Android field app for capture, local persistence, and LiveKit publishing
- `backend/` Spring Boot API for auth, session state, recording metadata, and LiveKit token issuance
- `frontend/` Angular operator console using Angular Material plus LiveKit client
- `infra/` local and production Docker Compose stacks
- `docs/` project documentation, architecture notes, and ASCII flow diagrams
- `scripts/` deployment and helper scripts

## Core Architecture

```text
Android Worker App
  -> publishes live media to LiveKit
  -> stores recording files locally first

Spring Boot Backend
  -> authenticates operators and workers
  -> stores session and recording metadata
  -> issues LiveKit join tokens

Angular Operator Console
  -> signs operators in
  -> shows active sessions
  -> joins live rooms
  -> replays stored recordings
```

## Frontend Notes

- Angular standalone components
- Angular Material for shell, cards, forms, actions, and loading states
- LiveKit browser client for room join and remote track subscription
- short-interval polling for session and recording metadata

## Compose Entry Points

- Local stack: `infra/docker-compose.yml`
- Production stack: `infra/docker-compose.prod.yml`

Each entrypoint is standalone and loads smaller service fragments from `infra/compose/local/` or `infra/compose/prod/`.

## Documentation

- docs index: [docs/README.md](/H:/workspace/body-cam/docs/README.md)
- system overview: [docs/architecture/system-overview.md](/H:/workspace/body-cam/docs/architecture/system-overview.md)
- frontend architecture: [docs/architecture/frontend-operator-console.md](/H:/workspace/body-cam/docs/architecture/frontend-operator-console.md)
- live session flow: [docs/flows/live-session-flow.md](/H:/workspace/body-cam/docs/flows/live-session-flow.md)
- recording sync flow: [docs/flows/recording-sync-flow.md](/H:/workspace/body-cam/docs/flows/recording-sync-flow.md)

## Repo Hygiene

- `.codex/` is ignored for Git sync
- workspace-local IDE folders stay out of remote pushes
- production and local env files are excluded from version control

## Next MVP Steps

1. Add route guards and role-aware operator views in the Angular app.
2. Complete Android publish and reconnect behavior against the current backend contracts.
3. Finalize recording upload lifecycle and retention handling in storage.
4. Add operational smoke checks for live session join and replay verification.
