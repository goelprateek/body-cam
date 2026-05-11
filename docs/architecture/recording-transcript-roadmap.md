# Recording And Transcript Roadmap

## Purpose

This reference captures:

- the most important remaining gaps in the continuous-session recording and transcript flow
- the future roadmap for making the system more robust
- the larger feature list that can be implemented over time without losing the MVP architecture direction

It should be read together with:

- `docs/architecture/continuous-session-recording-phased.md`
- `docs/architecture/transcript-integration-phased.md`

## Current Baseline

The repo already supports:

- Android segmented recording uploads with session timeline metadata
- backend session recording timelines
- frontend session-based continuous playback across ordered segments
- pluggable transcript engines
- session-level transcript aggregation
- queued backend transcript processing through a background poller
- idempotent segment upload reuse by session and segment sequence
- session integrity states in the timeline response
- Android upload queue visibility during active and post-stop sync
- session transcript search in the operator experience
- transcript timestamp driven playback jumps in the recordings view
- production transcript dependency readiness checks for the current Vosk path

This is a strong MVP baseline, but it is not yet the final robust operating shape.

## Design Guardrails

Keep these constraints while extending the system:

- stay monolithic for backend control-plane work
- keep media files in object storage as ordered segments
- do not merge video during ingest
- keep transcript engines pluggable
- avoid introducing heavy workflow infrastructure too early
- prefer compatibility-first slices before cleanup or removal
- keep weak-network and offline field conditions as a primary design input

## Highest Priority Gaps

These are the most important remaining implementation gaps from an operational point of view.

### Priority 0 - Transcript Execution Lifecycle

Current gap:

- the request path is now asynchronous, but transcript execution is still single-runner and intentionally simple
- failed intervals still need a more targeted retry model

Required direction:

- move transcript execution to a backend-owned async job model
- keep request APIs as orchestration only
- track per-segment and per-session progress
- support retry of failed segments without regenerating the whole session

Target outcomes:

- `POST /api/sessions/{id}/transcript/generate` returns quickly
- transcript work continues in the background
- UI can show `PENDING`, `PROCESSING`, partial progress, and `FAILED` details cleanly

### Priority 0 - Upload Idempotency And Duplicate Protection

Current gap:

- duplicate upload reuse now exists for `sessionId + segmentSequence`, but conflict handling is still lightweight

Required direction:

- make segment ingest idempotent on a deterministic session key
- reject or reuse duplicates by `sessionId + segmentSequence`
- optionally verify checksum or size before reusing an existing segment

Target outcomes:

- retries are safe
- timeline gaps and duplicates are explicit
- session ordering stays clean under weak connectivity

### Priority 0 - Partial Session Integrity

Current gap:

- the timeline now exposes integrity state, but recovery semantics are still basic

Required direction:

- represent missing, late, duplicate, and failed segments explicitly
- expose session integrity state in API responses
- let operators understand whether they are viewing a complete or partial session

Target outcomes:

- sessions can be labeled `COMPLETE`, `PARTIAL`, `HAS_GAPS`, or `PROCESSING_UPLOADS`
- transcript generation can skip or flag missing media intervals instead of failing unclearly

## Near-Term Roadmap

### Phase 7.1 - Async Transcript Job Model

Scope:

- add a backend transcript job runner
- claim and process segment transcripts in the background
- aggregate session progress from segment-level jobs
- preserve the existing pluggable engine interface

Suggested additions:

- transcript job timestamps
- transcript attempt count
- last failure code and message
- worker concurrency limits
- retry endpoint for failed work

Why this matters:

- this is the single most important step for long-session reliability
- it also prepares the system for `whisper.cpp` or `faster-whisper` workloads

Current status:

- implemented with a backend scheduled poller and queued transcript requests
- still intentionally single-runner and simple

### Phase 7.2 - Idempotent Segment Ingest

Scope:

- enforce uniqueness for session segment identity
- support safe duplicate retries from Android WorkManager
- improve logging around accepted, reused, and conflicting uploads

Suggested additions:

- unique DB constraint on session segment identity
- duplicate-upload response behavior that is explicit and stable
- checksum or file-size conflict detection

Current status:

- implemented with a recording asset idempotency key derived from `sessionId + segmentSequence`
- checksum or payload conflict handling remains future work

### Phase 7.3 - Session Integrity And Recovery UX

Scope:

- enrich session timeline with integrity states
- show gap markers and missing ranges in the frontend
- let operators distinguish complete playback from best-available playback

Suggested additions:

- missing segment ranges
- duplicate segment warnings
- late-arrival reconciliation
- operator-visible integrity badges

Current status:

- implemented at the first slice with `COMPLETE`, `PROCESSING_UPLOADS`, `PARTIAL`, and `HAS_GAPS`
- richer recovery and gap explanation is still pending

### Phase 7.4 - Smaller Segment Duration Rollout

Scope:

- move Android from `30s` segments toward `5s` to `10s`
- validate storage, upload frequency, and battery tradeoffs

Why later and not first:

- the timeline model is already in place
- idempotent ingest and async transcript execution should land first so the system can absorb higher segment counts safely

### Phase 7.5 - Session-Level Transcript Review UX

Scope:

- improve transcript review beyond the first working experience
- keep transcript and playback aligned to one session timeline

Suggested additions:

- active transcript highlighting while video plays
- search within a session transcript
- transcript confidence or warning indicators
- collapse or expand transcript by segment boundaries
- filter to failed or missing transcript intervals

Current status:

- transcript search within a session is implemented
- playback-timestamp transcript navigation and active highlighting are already in place
- richer filtering and confidence UX remain future work

## Mid-Term Roadmap

### Phase 8.1 - Whisper-Ready Execution Path

Scope:

- keep `vosk` as one engine
- add a production-ready `faster-whisper` or `whisper.cpp` engine path
- preserve the backend-owned normalized transcript model

Required shape:

- engine selection by config
- engine capability metadata
- model name and language tracking
- consistent session transcript output regardless of engine

### Phase 8.2 - Export And Evidence Packaging

Scope:

- add async session export
- optionally generate merged MP4, manifest bundle, transcript file, and metadata package

Possible outputs:

- merged MP4
- session transcript `.txt`
- session subtitles `.vtt`
- manifest JSON
- evidence metadata summary

Important guardrail:

- export must stay asynchronous and separate from ingest

### Phase 8.3 - Recording Search And Investigation UX

Scope:

- help operators investigate long sessions faster

Suggested additions:

- transcript keyword search
- jump-to-match playback
- speaker or event markers later if engines support them
- timeline bookmarks
- important-event tagging

### Phase 8.4 - Operational Observability

Scope:

- make recording and transcript pipelines easier to operate in production

Suggested additions:

- upload success and failure counters
- session completeness metrics
- transcript queue depth
- transcript processing duration
- per-engine error rate
- per-session processing audit trail

## Longer-Term Roadmap

### Phase 9.1 - Offline-First Hardening

Suggested additions:

- Android local retention policy
- backpressure behavior when storage is low
- upload prioritization by recency or incident severity
- better recovery after device reboot or app kill

### Phase 9.2 - Security And Governance

Suggested additions:

- transcript access auditing
- export access approval flow
- signed or time-limited playback URLs
- stronger retention and purge policies
- object integrity verification

### Phase 9.3 - Evidence And Casework Features

Suggested additions:

- case association for a session
- operator notes and annotations
- redaction workflow for export
- transcript excerpt sharing
- evidence package checksum and chain-of-custody metadata

## Feature Catalog

This is the broader feature inventory that can be implemented over time.

### Android Features

- smaller segment duration rollout
- persistent upload queue inspection UI
- visible pending upload count per session
- upload retry and failure diagnostics
- low-storage warnings and retention controls
- incident or marker button during recording
- optional local encryption for stored segments

### Backend Features

- async transcript runner
- segment ingest idempotency
- duplicate and gap reconciliation
- transcript retry endpoints
- transcript search endpoints
- export jobs and downloadable artifacts
- retention enforcement
- stronger session integrity states

### Frontend Features

- session completeness badges
- transcript search and highlighting
- timeline gap indicators
- session bookmarks and markers
- export request UI
- transcript retry UI for failed intervals
- evidence metadata panel

### Infrastructure And Pipeline Features

- transcript smoke checks after deploy
- model asset or engine readiness verification
- storage capacity alarms
- queue-depth alerts
- backup and restore validation for recording metadata
- production sizing notes for `whisper.cpp` migration

### AI And Transcript Features

- engine comparison mode for evaluation
- language detection improvements
- optional segment confidence display
- batch regenerate transcript with a better engine
- entity extraction later for investigations
- summarization later for long sessions

## Suggested Delivery Order

If the goal is most value with least architectural regret, the best order is:

1. async transcript jobs
2. segment ingest idempotency
3. session integrity states and UX
4. smaller Android segments
5. transcript search and richer session review
6. async export or evidence packaging
7. `whisper.cpp` or `faster-whisper` production hardening

## What Not To Do Yet

Avoid these until the roadmap above is farther along:

- merge video during normal ingest
- introduce Kafka or a large workflow platform
- redesign the data model around one huge session file
- add live transcription into the active session path
- overfit the API contract to one speech engine

## Reference Update Rule

When continuous-session recording or transcript work advances, update this file together with:

- `docs/architecture/continuous-session-recording-phased.md`
- `docs/architecture/transcript-integration-phased.md`
- `PLANS.md`

That keeps implementation status, future roadmap, and architecture guidance aligned.
