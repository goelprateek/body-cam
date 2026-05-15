# Backend

Spring Boot monolith for:

- JWT authentication
- LiveKit access token generation
- session lifecycle metadata
- recording metadata and playback references

Package structure is feature-oriented to keep the MVP simple and easy to evolve.

## Build

- `./gradlew bootRun`
- `./gradlew test`
- `./gradlew bootJar`

## JWT Secret

- `APP_JWT_SECRET` must be at least 32 characters for `HS256`.
- The `local` Spring profile now provides a dev-only fallback secret for convenience.
- Non-local environments should set `APP_JWT_SECRET` explicitly instead of relying on defaults.

## Recording Upload Size

- Recording uploads currently use Spring multipart handling at `POST /api/recordings/upload`, with the Android app posting the finalized file to the backend first.
- The backend now defaults to a `512MB` upload cap via `APP_RECORDING_MAX_UPLOAD_SIZE`.
- Increase `APP_RECORDING_MAX_UPLOAD_SIZE` if field devices upload larger finalized segments.
- Keep the frontend proxy aligned by setting `NGINX_CLIENT_MAX_BODY_SIZE` to the same ceiling, for example `512m`.

## Recording Metadata

- Each uploaded clip still creates one `recording_asset` row tied to a session.
- Optional structured metadata can now be attached per clip and is stored in `recording_metadata`.
- The multipart upload endpoint accepts an optional JSON `metadata` part alongside `sessionId`, `durationSeconds`, and `file`.
- Current Android uploads can include `capturedAt`, `cameraFacing`, and best-effort location fields.
- The metadata model is designed to grow cleanly into thermal or other sensor capture through typed thermal columns plus flexible `sensorPayload` JSON.

## Transcript Runtime

- Transcript generation now uses a pluggable engine boundary selected by `APP_TRANSCRIPT_ENGINE`.
- `vosk` is the first engine and keeps the current self-hosted WebSocket flow.
- `faster-whisper` is now implemented as a second engine option for the planned quality-upgrade path.
- Transcript requests are now queued in the backend and processed asynchronously by a scheduled background runner instead of blocking the request path.
- Audio extraction is now handled through embedded JavaCV native bindings instead of shelling out to a host or container `ffmpeg` binary.
- That means local IDE runs and backend containers no longer require a separately installed `ffmpeg`.
- The transcript DB and API contract stays engine-neutral so a later move to `whisper.cpp` can remain a backend engine swap.
- The backend now also exposes session-level transcript aggregation so continuous session playback and transcript review follow the same ordered segment timeline.
- The transcript poll loop delay is configurable with `APP_TRANSCRIPT_POLL_DELAY_MS`, defaulting to `5000`.
- The `faster-whisper` engine expects an OpenAI-compatible self-hosted transcription endpoint, defaulting to `TRANSCRIPT_FASTER_WHISPER_URL=http://localhost:8001/v1/audio/transcriptions`.
- When you are ready to switch, set:
  `APP_TRANSCRIPT_ENGINE=faster-whisper`
  `TRANSCRIPT_FASTER_WHISPER_URL=...`
  `TRANSCRIPT_FASTER_WHISPER_MODEL=large-v3`
  `TRANSCRIPT_FASTER_WHISPER_TASK=transcribe`

## Database Migrations

- Flyway versioned migrations live in `src/main/resources/db/migration`.
- The app starts with `spring.jpa.hibernate.ddl-auto=validate`, so Flyway must create the schema before Hibernate initializes.
- On Spring Boot 4, the backend needs `org.springframework.boot:spring-boot-starter-flyway` so Boot actually auto-configures and runs Flyway at startup.
- `baseline-on-migrate` is intentionally not enabled for this project. On a partially populated local database, baselining can skip `V1__init.sql` and leave tables like `app_user` missing.
- If your local database was already started with the old baseline behavior, reset the local schema before retrying:
  - drop and recreate the `bodycam` database, or
  - remove the stale `flyway_schema_history` table and rerun the app only if you are sure the database is disposable.

## Seed Users

- `worker1 / worker123`
- `operator1 / operator123`

## Main APIs

- `POST /api/auth/login`
- `GET /api/auth/me`
- `GET /api/sessions`
- `POST /api/sessions` with `workerId`, `workerName`, and required `referenceNumber`
- `POST /api/sessions/{id}/join-token`
- `POST /api/sessions/{id}/end`
- `GET /api/recordings`
- `GET /api/recordings/{id}/playback-url`
- `GET /api/sessions/{id}/recordings/timeline`
- `GET /api/sessions/{id}/recordings/export-package`
- `POST /api/sessions/{id}/recordings/export-package`
- `GET /api/sessions/{id}/transcript`
- `GET /api/sessions/{id}/transcript/search?q=...`
- `GET /api/sessions/{id}/transcript/subtitles.vtt`
- `POST /api/sessions/{id}/transcript/generate`
- `POST /api/sessions/{id}/transcript/retry-failed`
- `GET /api/recordings/investigation-search?q=...`
- `GET /api/recordings/{id}/transcript`
- `GET /api/recordings/{id}/transcript/subtitles.vtt`
- `POST /api/recordings/{id}/transcript/generate`
- `POST /api/recordings`
- `POST /api/recordings/upload`
