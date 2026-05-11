# Live Session Flow

## End-To-End Flow

```text
Field Worker App
  |
  | 1. login
  | 2. capture reference number before stream start
  v
Spring Boot Auth API
  |
  | 3. create or resume session metadata with reference number
  v
PostgreSQL
  |
  | 4. request worker join token
  v
Spring Boot Session API
  |
  | 5. return LiveKit token + room name + URL
  v
Android App
  |
  | 6. publish camera and microphone
  v
LiveKit Room
  |
  | 7. operator login
  v
Angular Operator Console
  |
  | 8. operator requests join token
  v
Spring Boot Session API
  |
  | 9. return operator join token
  v
Angular Operator Console
  |
  | 10. connect to LiveKit room
  v
LiveKit Room
  |
  | 11. subscribe to remote audio and video tracks
  v
Operator sees and hears worker stream
```

## Control Flow Notes

- The backend is the trust boundary for room admission.
- The operator console never mints LiveKit tokens directly.
- Each live session is now anchored to a required worker-supplied `referenceNumber`.
- The operator console should display the reference number together with the session creation timestamp as the reference datetime.
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
