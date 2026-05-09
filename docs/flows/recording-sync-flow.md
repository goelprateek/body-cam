# Recording Sync Flow

## Intent

The current MVP recording design stores media locally on the field device first, then uploads finalized clip files to the backend recording API. The backend writes each clip to MinIO and stores recording metadata keyed to the active backend session. If the user flips between front and back cameras, the app closes the current clip cleanly, switches the lens, starts a new clip, and continues uploading under the same `sessionId`. Each clip may also carry optional structured metadata such as location, camera-facing, and future thermal or sensor context.

## Flow Diagram

```text
Android Capture Pipeline
  |
  | 1. capture audio/video locally while session is active
  v
Local Device Storage
  |
  | 2. finalize a clip on timer, stop, or camera flip
  v
Sync Worker
  |
  | 3. upload clip to backend with sessionId + optional duration
  |    + optional metadata JSON
  v
Spring Boot Recording API
  |
  | 4. write clip object to MinIO using session-scoped object key
  |    sessions/<sessionId>/<clip-id>.mp4
  |    and persist clip metadata if present
  v
MinIO / S3 Bucket
  |
  | 5. backend saves one recording row for that clip under the same session
  |    plus optional `recording_metadata`
  v
PostgreSQL
  |
  | 6. operator console lists all clips for the session
  v
Angular Operator Console
  |
  | 7. operator opens playback URL for the chosen clip
  v
Replay in browser
```

## Camera Flip Behavior

- Camera flipping is an in-session operation, not a new-session operation.
- The Android app keeps the same `sessionId` from `ActiveSessionConfig` for every upload before and after a flip.
- A flip creates a clip boundary:
  current clip finalizes -> upload queued -> camera rebinds -> next clip starts on the new lens.
- The backend therefore stores multiple recording rows for one session when the device changes camera or when the segmenter finalizes another clip.
- The operator console should treat these as separate clips under one session timeline, not as separate sessions.

## Metadata Model

- Core clip storage stays in `recording_asset`.
- Optional extensible clip context lives in `recording_metadata`.
- Query-friendly fields such as latitude, longitude, capture time, camera-facing, and thermal summary are stored as typed columns.
- More variable device or sensor details can flow through `sensorPayload`.
- This split keeps the core recording table stable while allowing future capture enrichments without redesigning the session model.

## Sync Expectations

- device-first local persistence protects against unstable mobile connectivity
- object storage is the durable home for replay assets
- the current backend path owns both object upload and metadata persistence
- session identity remains stable across segmented clips and camera flips
- metadata should remain clip-scoped so session continuity and clip context do not get conflated
- the operator UI should tolerate recordings that exist before duration is finalized

## Operational States

```text
CAPTURING
  -> local file open on device

QUEUED_FOR_UPLOAD
  -> file closed and waiting for connectivity

SWITCHING_CAMERA
  -> current clip is finalized, upload is queued, camera rebinds, same session continues

UPLOADING
  -> Android app is posting the clip to the backend recording API with the current sessionId and optional metadata

UPLOADED
  -> backend has stored the object in MinIO

REGISTERED
  -> backend recording row and optional metadata row created for the uploaded clip under the same session

PLAYABLE
  -> playback URL available in operator console
```
