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
- `POST /api/sessions`
- `POST /api/sessions/{id}/join-token`
- `POST /api/sessions/{id}/end`
- `GET /api/recordings`
- `POST /api/recordings`
