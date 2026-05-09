# System Overview

## Purpose

`body-cam` is an MVP platform for a field worker to stream live audio and video while a backoffice operator joins the room, monitors the feed, and later reviews stored recordings.

## Primary Building Blocks

- `android-app/`
  Field worker app. Captures audio and video, stores media locally first, publishes to LiveKit for near real-time viewing, and uploads finalized recording files to the backend recording API. During an active session it can switch between front and back cameras, but all generated clips still remain under the same session.
- `backend/`
  Spring Boot API. Owns operator authentication, session metadata, recording metadata, LiveKit join token generation, and the current recording upload handoff into MinIO. The backend groups uploaded clips by `sessionId`, stores each uploaded segment as a recording row for that session, and supports an extensible per-clip metadata model for location, camera-facing, thermal, and future sensor data.
- `frontend/`
  Angular backoffice console. Operators sign in, inspect sessions, join live rooms, and review recordings.
- `infra/`
  Docker Compose topology for local and production environments, including LiveKit, MinIO, PostgreSQL, coturn, and runtime services.

## Architecture Diagram

```text
                         +----------------------+
                         |   Backoffice User    |
                         +----------+-----------+
                                    |
                                    v
                         +----------------------+
                         | Angular Operator UI  |
                         | frontend/            |
                         +-----+-----------+----+
                               |           |
                    HTTPS REST |           | WebRTC / WS
                               v           v
                    +----------------+   +----------------+
                    | Spring Boot    |   | LiveKit Server |
                    | backend/       |   | media control  |
                    +---+--------+---+   +--------+-------+
                        |        |               |
            metadata    |        | tokens        | live media
                        v        |               v
                 +-----------+   |        +--------------+
                 | PostgreSQL|   |        | Android App  |
                 | sessions  |   |        | field worker |
                 +-----+-----+   |        +------+-------+
                       ^         |               |
                       |         |               | local file segments
                       |         |               | back camera / front camera
                       |         |               v
                       |         |        +--------------+
                       |         |        | Sync Worker  |
                       |         |        | same session |
                       |         |        +------+-------+
                       |         |               |
                       |         +---------------+
                       |                         | multipart upload with sessionId
                       |                         | plus optional metadata JSON
                       |                         v
                       |                  +--------------+
                       +------------------| Recording API|
                                          | backend/     |
                                          +------+-------+
                                                 |
                                persist row per  | store object with session path
                                clip + sessionId | sessions/<sessionId>/<clip>.mp4
                                persist metadata | location / camera / thermal
                                                 v
                                          +--------------+
                                          | MinIO / S3   |
                                          | recordings   |
                                          +--------------+
```

## MVP Boundaries

- Live media transport belongs to LiveKit.
- Recording objects belong to object storage.
- Session and recording metadata belong to Spring Boot plus PostgreSQL.
- The backend currently brokers recording file uploads into object storage, but it does not handle live media transport.
- Camera flips happen inside one live session. The app may finalize one clip and start the next on the other lens, but all resulting clips still belong to the same backend session.
- Per-clip metadata is the extension point for future device context such as GPS and thermal measurements; new fields should attach to the clip, not create a new session type.
- The frontend should stay service-based and operationally simple.

## Deployment Shape

```text
Local:
  docker-compose.yml
    -> postgres
    -> minio
    -> livekit
    -> coturn
    -> backend
    -> frontend

Production:
  docker-compose.prod.yml
    -> image-based backend/frontend
    -> Traefik-exposed operator routes
    -> internal stateful services
```
