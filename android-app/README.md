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
