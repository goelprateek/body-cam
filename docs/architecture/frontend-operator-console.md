# Frontend Operator Console

## Goal

The operator console is the backoffice surface for:

- authenticating operators
- listing active field sessions
- joining a live worker room
- listening to remote audio
- viewing remote video
- reviewing previously stored recordings

## Component Structure

```text
AppComponent
|
+-- DashboardPageComponent
    |
    +-- OperatorApiService
    |   |
    |   +-- /api/auth/login
    |   +-- /api/auth/me
    |   +-- /api/sessions
    |   +-- /api/sessions/{id}/join-token
    |   +-- /api/sessions/{id}/end
    |   +-- /api/recordings
    |
    +-- LiveRoomService
        |
        +-- LiveKit Room
            +-- remote video track
            +-- remote audio track
            +-- participant presence
            +-- reconnect state
```

## UI Zones

```text
+---------------------------------------------------------------+
| Toolbar                                                       |
| Body Cam Operator Console                                     |
+----------------------+---------------------------+------------+
| Operator Access      | Field Session Monitor     | Recordings |
| - API endpoint       | - live status             | - replay   |
| - login/logout       | - video stage             | - filters  |
| - operator label     | - session cards           | - list     |
+----------------------+---------------------------+------------+
```

## Material Usage

The frontend now uses Angular Material for:

- toolbar shell
- cards
- form fields
- inputs
- buttons
- dividers
- progress bars

This keeps the console aligned with Angular-native accessibility and interaction patterns while still allowing project-specific styling.

## Runtime Behavior

```text
Startup
  -> restore access token from localStorage
  -> call /api/auth/me if token exists
  -> start 10 second refresh loop after auth succeeds

Join stream
  -> operator selects session
  -> frontend requests join token from backend
  -> backend returns LiveKit URL + token
  -> frontend connects to LiveKit room
  -> audio/video tracks attach to DOM hosts

Leave stream
  -> frontend disconnects room
  -> attached media elements are removed
```

## Future Frontend Extensions

- route guards for authenticated and role-aware views
- session filtering and search
- explicit worker presence badges
- recording retention and download controls
- operator notes and escalation workflow
