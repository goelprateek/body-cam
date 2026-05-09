# Android App

Kotlin Android application for field workers.

Core responsibilities:

- login with backend-issued credentials
- join LiveKit room
- publish camera and microphone
- stop and reconnect sessions cleanly

MVVM is the default pattern for the mobile client.

Connection behavior:

- the worker UI no longer exposes backend or LiveKit URLs
- the app uses the configured backend base URL from `BuildConfig.DEFAULT_BACKEND_URL`
- the backend remains the source of truth for the LiveKit WebSocket URL returned during session join
- in production that returned LiveKit URL must be a public `wss://` subdomain such as `wss://livekit.example.com`, not an internal Docker hostname

Environment-specific build values:

- `android-app/app/build.gradle.kts` now reads per-flavor values from `android-app/config/dev.properties`, `android-app/config/staging.properties`, and `android-app/config/prod.properties`
- commit only the `*.properties.example` files; the real `*.properties` files are ignored for local overrides
- each file must define `DEFAULT_BACKEND_URL`
- Gradle loads the committed example first, then overlays the untracked local file if present

Example setup:

```properties
DEFAULT_BACKEND_URL=http://192.168.1.10:8080/
```

Build examples:

- `./gradlew assembleDevDebug`
- `./gradlew assembleStagingDebug`
- `./gradlew assembleProdRelease`
