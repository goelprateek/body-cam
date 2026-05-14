# Recording And Transcript Roadmap

## Purpose

This reference captures:

- the most important remaining gaps in the continuous-session recording and transcript flow
- the future roadmap for making the system more robust
- the larger feature list that can be implemented over time without losing the MVP architecture direction

System-wide implementation status should be treated as authoritative in:

- `docs/architecture/system-feature-catalog.md`

This document can keep recording and transcript-specific status notes for local planning, but completed feature state must be reflected in the system feature catalog.

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

One important remaining correction is transcript post-processing shape:

- today the pipeline is still too close to `segment audio -> STT output -> DB`
- the target shape should be `Transcription Queue -> Audio Extractor -> Vosk STT -> Raw Timeline -> Punctuation Restoration -> Transcript Finalization -> Search Index -> Playback Sync -> Retry And Recovery -> Transcript DB`
- the stored transcript should come from assembly output, not directly from raw STT fragments

Target advanced transcript pipeline:

```text
Transcription Queue
  -> Audio Extractor
  -> Vosk STT
  -> Raw Word Timeline Storage
  -> Punctuation Restoration
  -> Transcript Finalization
  -> AI Summarization
  -> Transcript Search Index
  -> Playback Synchronization
  -> Retry And Recovery
  -> Transcript DB
```

## Phase Status Table

| Phase | Status | Notes |
| --- | --- | --- |
| 7.1 Async transcript job model | `Implemented` | Transcript requests queue work and a scheduled backend poller processes jobs. |
| 7.2 Idempotent segment ingest | `Implemented` | Upload reuse is keyed by `sessionId + segmentSequence`. |
| 7.3 Session integrity and recovery UX | `Implemented` first slice | Integrity states are returned in the timeline response. |
| 7.4 Smaller segment duration rollout | `Implemented` | Android now records `10s` segments for lower retry cost in weak-network conditions. |
| 7.5 Session-level transcript review UX | `Implemented` | Backend-backed search, timestamp jump, active highlighting, low-confidence indicators, failed-interval review, selective retry, and queue visibility are implemented. |
| 8.1 Whisper-ready execution path | `Partially Implemented` | Engine abstraction and `faster-whisper` are in place; `whisper.cpp` itself is not yet implemented. |
| 8.2 Export and evidence packaging | `Implemented` | Async session export packaging is implemented with downloadable evidence bundles. |
| 8.3 Recording search and investigation UX | `Implemented` | Cross-session investigation search, jump-to-match review, and archive investigation UX are implemented. |
| 8.4 Operational observability | `Partially Implemented` | Logging and basic prod readiness exist; deeper metrics and alerts remain future work. |

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
- post-STT assembly is still too lightweight and not yet modeled as a first-class pipeline stage

Required direction:

- move transcript execution to a backend-owned async job model
- keep request APIs as orchestration only
- track per-segment and per-session progress
- support retry of failed segments without regenerating the whole session
- add a transcript assembly stage between STT output and persisted transcript rows
- model punctuation restoration, finalization, indexing, playback sync, and recovery as distinct pipeline responsibilities

Target outcomes:

- `POST /api/sessions/{id}/transcript/generate` returns quickly
- transcript work continues in the background
- UI can show `PENDING`, `PROCESSING`, partial progress, and `FAILED` details cleanly
- operator-facing transcript rows come from assembled, deduplicated, sentence-safe output

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
- add transcript assembly as a first-class backend stage

Suggested additions:

- transcript job timestamps
- transcript attempt count
- last failure code and message
- worker concurrency limits
- retry endpoint for failed work
- assembly metrics such as overlap merges, dedupe corrections, and punctuation pass completion
- explicit processing states such as `TRANSCRIBED`, `PUNCTUATED`, and `FINALIZED`
- search-index freshness and playback-payload generation metrics

Why this matters:

- this is the single most important step for long-session reliability
- it also prepares the system for `whisper.cpp` or `faster-whisper` workloads
- it prevents noisy raw STT fragments from becoming the stored session transcript contract

Current status:

- implemented with a backend scheduled poller and queued transcript requests
- still intentionally single-runner and simple
- transcript assembly is still an architecture gap and should be introduced before treating the transcript model as final

Suggested advanced stage responsibilities:

- punctuation restoration
- sentence finalization and overlap merge
- AI summary generation from finalized transcript text
- transcript sentence indexing
- playback-synchronized transcript projection
- stage-aware retry and recovery

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

Current status:

- implemented at `10s` segment duration to reduce retry size and improve weak-network resilience
- additional battery and upload-frequency tuning can still happen later if field testing suggests `5s` or another value is better

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

- transcript search within a session is implemented through the backend search endpoint
- playback-timestamp transcript navigation, active highlighting, and low-confidence indicators are in place
- failed and missing transcript intervals can now be reviewed and retried from the operator console

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
- a stable transcript assembly contract that sits between engine output and persisted transcript artifacts
- stable post-STT stages so search and playback do not depend on engine-specific raw output

### Phase 8.1b - AI Transcript Summarization

Scope:

- generate concise summaries from finalized session transcript content
- support long-session review with short summaries and incident summaries
- keep summarization asynchronous and optional

Required shape:

- summarization reads finalized transcript artifacts, not raw STT output
- summary generation is backend-owned and pluggable by model or provider
- failure to summarize does not block transcript readiness, search, subtitles, or playback

Possible outputs:

- short session summary
- incident summary
- highlight bullets
- optional action-item extraction later

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

Current status:

- async session export packaging is implemented
- downloadable evidence bundles now include session summary, recording timeline, transcript JSON, transcript text, and subtitles when available
- merged MP4 export remains optional future work rather than a dependency for this phase

### Phase 8.3 - Recording Search And Investigation UX

Scope:

- help operators investigate long sessions faster

Suggested additions:

- transcript keyword search
- jump-to-match playback
- speaker or event markers later if engines support them
- timeline bookmarks
- important-event tagging

Current status:

- investigation search now works across sessions for references, worker names, room names, and transcript text
- operators can jump from a search hit directly into the matching session playback and transcript context
- deeper investigation features like bookmarks and event tagging remain future work

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
