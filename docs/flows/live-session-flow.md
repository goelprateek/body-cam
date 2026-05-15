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

## Operator-Created Browser Session Flow

```text
Angular Operator Console
  |
  | 1. create session metadata from the operator console
  v
Spring Boot Session API
  |
  | 2. persist active session
  v
PostgreSQL
  |
  | 3. create time-limited browser invite
  v
Spring Boot Session Invite API
  |
  | 4. return join path for viewer-only or publisher browser sharing
  v
Operator shares browser join link
  |
  | 5. browser participant opens invite page
  v
Browser Join Page
  |
  | 6. request public join token using invite token + participant name
  v
Spring Boot Session Invite API
  |
  | 7. validate invite and mint a LiveKit token scoped to the invite role
  v
LiveKit Room
  |
  | 8. browser participant either joins as viewer-only or publishes camera and microphone
  v
Angular Operator Console
  |
  | 9. operator joins the same room and monitors the browser participant stream
  v
Operator sees and hears browser participant stream
```

## Control Flow Notes

- The backend is the trust boundary for room admission.
- The operator console never mints LiveKit tokens directly.
- Each live session is now anchored to a required worker-supplied `referenceNumber`.
- The operator console should display the reference number together with the session creation timestamp as the reference datetime.
- Operator-created browser sessions should use time-limited invite tokens instead of exposing raw session ids for public room joins.
- Viewer invites do not require app login; possession of a valid, unexpired invite link is the admission mechanism.
- The backend remains the only component that signs LiveKit join tokens, so invite holders never receive the LiveKit API secret directly.
- Browser invite participants can now be split into viewer-only and publish-capable links, while operator console joins remain monitor-focused.
- Browser invite links are intentionally short-lived and currently expire after `10 minutes`.
- The operator share UI can revoke generated invite links before they expire.
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
