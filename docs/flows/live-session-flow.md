# Live Session Flow

## End-To-End Flow

```text
Field Worker App
  |
  | 1. login
  v
Spring Boot Auth API
  |
  | 2. create or resume session metadata
  v
PostgreSQL
  |
  | 3. request worker join token
  v
Spring Boot Session API
  |
  | 4. return LiveKit token + room name + URL
  v
Android App
  |
  | 5. publish camera and microphone
  v
LiveKit Room
  |
  | 6. operator login
  v
Angular Operator Console
  |
  | 7. operator requests join token
  v
Spring Boot Session API
  |
  | 8. return operator join token
  v
Angular Operator Console
  |
  | 9. connect to LiveKit room
  v
LiveKit Room
  |
  | 10. subscribe to remote audio and video tracks
  v
Operator sees and hears worker stream
```

## Control Flow Notes

- The backend is the trust boundary for room admission.
- The operator console never mints LiveKit tokens directly.
- Near real-time updates are a combination of:
  - immediate LiveKit media subscription for audio/video
  - short-interval REST refresh for session and recording metadata

## Failure Handling

```text
Join token request fails
  -> show API error in operator panel

LiveKit connect fails
  -> show room connection error
  -> keep selected session intact

Remote worker drops
  -> LiveKit room enters reconnect or participant disconnect state
  -> operator panel updates status
```
