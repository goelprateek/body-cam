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

## Seed Users

- `worker1 / worker123`
- `operator1 / operator123`

## Main APIs

- `POST /api/auth/login`
- `GET /api/auth/me`
- `GET /api/sessions`
- `POST /api/sessions`
- `POST /api/sessions/{id}/join-token`
- `POST /api/sessions/{id}/end`
- `GET /api/recordings`
- `POST /api/recordings`
