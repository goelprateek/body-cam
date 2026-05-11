# Continuous Session Recording Phased Plan

## Goal

Support long-running bodycam sessions, for example 20 to 30 minutes, while:

- preserving weak-network and offline resilience
- keeping device and backend resource usage predictable
- allowing operators to view one session as a continuous playback timeline
- avoiding expensive ingest-time video merging

## Current State

Today the Android app records and uploads fixed MP4 clips, currently around 30 seconds each.
The backend stores each upload as an independent `recording_asset`.
The frontend archive then treats each recording as a separate playable item.

That design is reliable for upload and retry, but it does not represent one long session recording as a continuous playback object.

## Core Recommendation

Keep segmented recording and upload.
Do not merge every segment into one large backend video during ingest.

Instead:

- Android records smaller ordered chunks
- each chunk is uploaded independently
- backend stores chunks as ordered session-aligned segments
- backend exposes a session playback manifest or timeline
- frontend plays the session as one logical recording
- optional server-side merge remains a later async export concern only

This keeps the ingest path cheap and reliable while still delivering a continuous playback experience.

## Implemented So Far

The current codebase now covers the Phase 6.1 foundation and part of Phase 6.2:

- Android uploads segment timeline metadata with each recording chunk
- backend persists segment ordering and session-relative timing
- backend exposes `GET /api/sessions/{sessionId}/recordings/timeline`
- frontend now loads session timelines and plays across ordered segments
- backend now exposes session transcript aggregation and session-wide transcript generation

This means the system can now describe a session as an ordered timeline, present it as a session-based playback experience, and aggregate transcript state across that same session. Subtitle tracks remain segment-specific during active playback, which is acceptable for the current player architecture.

## Why This Is The Right Fit

### Weak Network Conditions

Smaller chunks are safer than a single large file:

- less data is lost per failed upload
- retries are cheaper
- queued uploads can resume gradually
- recording can continue offline while sync catches up later

### Backend Efficiency

Merging on every upload would:

- add CPU and I/O cost
- make ingest slower
- complicate out-of-order arrivals
- make partial session visibility harder

### Playback Experience

Operators do not need one physically merged file to get one logical playback experience.
They need an ordered session timeline with clean segment transitions.

## Target End State

### Android

Android keeps recording locally in bounded segments, ideally `5s` to `10s` instead of `30s`, and uploads them through a durable background queue.

Each segment should carry:

- `sessionId`
- `segmentSequence`
- `segmentStartedAt`
- `segmentEndedAt`
- `sessionElapsedStartMs`
- `sessionElapsedEndMs`
- `durationSeconds`
- codec or container metadata when useful
- optional checksum

### Backend

Backend stores:

- the uploaded media object in MinIO
- an ordered session recording segment record in the database

Backend should expose:

- session recording list or summary
- session playback manifest or timeline
- segment playback URLs
- gap or partial-upload state

### Frontend

Frontend should render one session recording timeline and play across ordered segments automatically.

The UI should stop presenting every segment as though it were a separate archival video when the operator intent is "play the session."

## Phased Rollout

## Phase 6.1 - Timeline Metadata Foundation

Objective:
Preserve current segmented storage, but make every uploaded clip timeline-aware.

Scope:

- Android sends ordered segment metadata
- backend persists segment ordering and session-relative timing
- current clip playback remains unchanged

Recommended data to add first:

- `segmentSequence`
- `segmentStartedAt`
- `segmentEndedAt`
- `sessionElapsedStartMs`
- `sessionElapsedEndMs`

Success criteria:

- every uploaded clip can be placed deterministically within a session timeline
- uploads remain backward compatible for older app behavior

Current status:

- implemented in Android upload metadata
- implemented in backend persistence and API DTOs

## Phase 6.2 - Session Recording Timeline Model

Objective:
Represent one recording session as a logical playback object backed by many stored segments.

Scope:

- add session-level recording summary or aggregate
- add ordered session segment read model
- add manifest API for playback

Possible API shape:

- `GET /api/sessions/{sessionId}/recordings/timeline`
- `GET /api/sessions/{sessionId}/recordings/manifest`

Manifest should include:

- logical session recording id
- total known duration
- ordered segments
- per-segment playback URL or object reference
- per-segment offsets
- partial or gap indicators

Success criteria:

- backend can describe one session as one ordered timeline
- backend can do so without merging media files

Current status:

- `GET /api/sessions/{sessionId}/recordings/timeline` is implemented
- the response includes ordered segments, total duration, and a gap indicator
- a distinct playback manifest format remains optional future work

## Phase 6.3 - Continuous Frontend Playback

Objective:
Allow operators to watch one session as one continuous recording.

Scope:

- frontend consumes session manifest
- player auto-advances between segments
- scrubber reflects total session timeline
- transcript and future evidence features can bind to session time instead of clip-only time

Success criteria:

- operator sees one session playback experience
- clip boundaries are operationally hidden as much as possible

Current status:

- the recordings page now groups archive items by session instead of by uploaded clip
- the frontend loads `GET /api/sessions/{sessionId}/recordings/timeline`
- playback auto-advances across ordered segments
- transcript actions now target the session as a whole while subtitles still follow the active segment

## Phase 6.4 - Optional Async Merge Or Export

Objective:
Support evidence export or shareable long-form downloads when needed.

Scope:

- async backend export job
- optional merged MP4 or archive package
- no effect on ingest path

Use this for:

- evidence packaging
- external handoff
- offline archival export

Do not use this as the default ingest-time behavior.

Success criteria:

- merged download is available when explicitly requested
- ingest and ordinary playback do not depend on merge completion

## Recommended Technical Direction

### Segment Duration

Move from `30s` to `5s` or `10s` segments in a later rollout once timeline-based playback is in place.

Reason:

- much safer under weak LTE
- lower retry penalty
- easier progressive upload visibility

### Storage Model

Keep one object per segment in MinIO.
Do not try to rewrite or append the same remote object during active recording.

### Ordering Model

Prefer explicit sequence and session-relative offsets instead of relying only on upload time or DB insertion order.

### Playback Model

Prefer a manifest or timeline-based playback approach over eager server-side concatenation.

### Merge Strategy

Merge only in asynchronous export paths, not in normal capture ingest.

## Non-Goals For The First Slice

Do not do these in Phase 6.1:

- replace segmented upload with one huge file
- force backend ingest-time video merging
- require strong connectivity during recording
- redesign transcript storage around merged videos
- add heavy queue infrastructure or workflow engines

## Implementation Notes For This Repo

The current codebase already has the right starting point for weak-network recording because:

- Android records local segments
- WorkManager handles deferred upload
- backend already stores independent recording assets

What is missing is the session timeline model, not the reliability model.

So the implementation priority should be:

1. timeline metadata
2. session manifest APIs
3. continuous playback UI
4. optional async merge

## Current Code References

- Android segment metadata capture:
  - `android-app/app/src/main/java/com/kriyanshtech/bodycam/LiveCaptureManager.kt`
  - `android-app/app/src/main/java/com/kriyanshtech/bodycam/UploadRecordingWorker.kt`
  - `android-app/app/src/main/java/com/kriyanshtech/bodycam/ApiModels.kt`
- Backend persistence and timeline API:
  - `backend/src/main/java/com/kriyanshtech/bodycam/recording/entity/RecordingMetadata.java`
  - `backend/src/main/java/com/kriyanshtech/bodycam/recording/service/RecordingService.java`
  - `backend/src/main/java/com/kriyanshtech/bodycam/recording/controller/SessionRecordingTimelineController.java`
  - `backend/src/main/resources/db/migration/V7__add_recording_segment_timeline_metadata.sql`
- Backend session transcript aggregation:
  - `backend/src/main/java/com/kriyanshtech/bodycam/recording/service/RecordingTranscriptService.java`
  - `backend/src/main/java/com/kriyanshtech/bodycam/recording/controller/SessionRecordingTranscriptController.java`
  - `backend/src/main/java/com/kriyanshtech/bodycam/recording/dto/SessionTranscriptResponse.java`
- Frontend continuous session playback:
  - `frontend/src/app/features/operations/operator-api.service.ts`
  - `frontend/src/app/features/operations/operator.models.ts`
  - `frontend/src/app/features/recordings/recordings-page.component.ts`
