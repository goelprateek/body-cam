# System Overview

## Purpose

`body-cam` is an MVP platform for a field worker to stream live audio and video while a backoffice operator joins the room, monitors the feed, and later reviews stored recordings.

## Primary Building Blocks

- `android-app/`
  Field worker app. Captures audio and video, stores media locally first, publishes to LiveKit for near real-time viewing, and uploads finalized recording files to the backend recording API. During an active session it can switch between front and back cameras, but all generated clips still remain under the same session.
- `backend/`
  Spring Boot API. Owns operator authentication, session metadata, recording metadata, LiveKit join token generation, recording upload handoff into MinIO, and the post-recording transcript pipeline. The backend groups uploaded clips by `sessionId`, stores each uploaded segment as a recording row for that session, supports an extensible per-clip metadata model for location, camera-facing, thermal, and future sensor data, and should treat STT output as intermediate data that must pass through transcript assembly before becoming the stored transcript artifact.
- `frontend/`
  Angular backoffice console. Operators sign in, inspect sessions, create browser-share sessions, choose between viewer-only and publisher links, join live rooms, and review recordings.
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
                                          +------+-------+
                                                 |
                                                 | queued transcript work
                                                 v
                                          +--------------+
                                          | Transcript   |
                                          | Queue        |
                                          +------+-------+
                                                 |
                                                 v
                                          +--------------+
                                          | Audio        |
                                          | Extractor    |
                                          +------+-------+
                                                 |
                                                 v
                                          +--------------+
                                          | Vosk STT     |
                                          | raw output   |
                                          +------+-------+
                                                 |
                                                 v
                                          +--------------+
                                          | Raw Word     |
                                          | Timeline     |
                                          +------+-------+
                                                 |
                                                 v
                                          +--------------+
                                          | Punctuation  |
                                          | Restoration  |
                                          +------+-------+
                                                 |
                                                 v
                                          +--------------+
                                          | Transcript   |
                                          | Finalization |
                                          +------+-------+
                                                 |
                      update summary, search,    | playback-ready transcript
                      and playback artifacts     | sentences and timeline
                     +-------------+-------------+-------------------+
                     |             |                                 |
                     v             v                                 v
            +----------------+ +------------------+         +------------------+
            | AI Summary     | | Transcript Search|         | Playback Sync    |
            | Artifacts      | | Index            |         | Payloads         |
            +--------+-------+ +--------+---------+         +---------+--------+
                     |                  |                             |
                     +------------------+-------------+---------------+
                                                    |
                                                    v
                                          +--------------+
                                          | Retry And    |
                                          | Recovery     |
                                          +------+-------+
                                                 |
                                   persist final | assembled transcript
                                   transcript    | and processing state
                                                 v
                                          +-----------+
                                          | PostgreSQL|
                                          | transcript|
                                          +-----------+
```

## MVP Boundaries

- Live media transport belongs to LiveKit.
- Recording objects belong to object storage.
- Session and recording metadata belong to Spring Boot plus PostgreSQL.
- The backend currently brokers recording file uploads into object storage, but it does not handle live media transport.
- Operators can now create a session directly in the console and mint a short-lived browser invite so a browser participant can either join as a viewer or publish camera or microphone without using the Android app. The operator share UI can also revoke those invite links before expiry.
- Raw STT output is not the final evidence artifact. The backend should persist transcript data only after punctuation restoration, transcript finalization, optional AI summarization, and playback-safe timeline alignment have completed.
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
