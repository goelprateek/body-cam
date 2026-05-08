# System Overview

## Purpose

`body-cam` is an MVP platform for a field worker to stream live audio and video while a backoffice operator joins the room, monitors the feed, and later reviews stored recordings.

## Primary Building Blocks

- `android-app/`
  Field worker app. Captures audio and video, stores media locally first, and publishes to LiveKit for near real-time viewing.
- `backend/`
  Spring Boot API. Owns operator authentication, session metadata, recording metadata, and LiveKit join token generation.
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
                 +-----------+   |        +------+-------+
                                 |               |
                                 |               | local files
                                 |               v
                                 |        +--------------+
                                 +------->| MinIO / S3   |
                                          | recordings   |
                                          +--------------+
```

## MVP Boundaries

- Live media transport belongs to LiveKit.
- Recording objects belong to object storage.
- Session and recording metadata belong to Spring Boot plus PostgreSQL.
- The backend should not process media streams directly.
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
