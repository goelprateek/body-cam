# Recording Sync Flow

## Intent

The MVP recording design assumes the field device stores media locally first, then uploads or syncs recording objects while the backend keeps track of metadata and playback location.

## Flow Diagram

```text
Android Capture Pipeline
  |
  | 1. capture audio/video locally
  v
Local Device Storage
  |
  | 2. segment or finalize recording file
  v
Sync Worker
  |
  | 3. upload recording object
  v
MinIO / S3 Bucket
  |
  | 4. notify backend with recording metadata
  v
Spring Boot Recording API
  |
  | 5. persist playback URL, object key, duration, session link
  v
PostgreSQL
  |
  | 6. operator console refreshes recording list
  v
Angular Operator Console
  |
  | 7. operator opens playback URL in HTML5 video player
  v
Replay in browser
```

## Sync Expectations

- device-first local persistence protects against unstable mobile connectivity
- object storage is the durable home for replay assets
- backend metadata should be small and explicit
- the operator UI should tolerate recordings that exist before duration is finalized

## Operational States

```text
CAPTURING
  -> local file open on device

QUEUED_FOR_UPLOAD
  -> file closed and waiting for connectivity

UPLOADED
  -> object exists in MinIO

REGISTERED
  -> backend recording row created or updated

PLAYABLE
  -> playback URL available in operator console
```
