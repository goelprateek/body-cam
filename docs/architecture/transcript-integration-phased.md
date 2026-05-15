# Transcript Integration Plan

## Goal

Add recording transcript support to the `body-cam` MVP so operators can read what was said in uploaded recordings from the operator console.

For system-level feature completion status, use:

- `docs/architecture/system-feature-catalog.md`

This document is the scoped transcript architecture and rollout reference. It can describe transcript-specific progress, but the authoritative system roadmap state must be updated in the system feature catalog.

The transcript capability should:

- stay open source
- remain self-hosted
- fit the current monolithic MVP architecture
- minimize server CPU and memory usage
- avoid blocking recording upload
- stay easy to upgrade to higher-quality transcription later
- treat raw STT output as intermediate data, not as the final stored transcript

## Recommended MVP Direction

Start with a low-resource transcript design built around:

- on-demand transcript generation
- a small backend transcript module
- a swappable transcription engine interface
- `Vosk` as the first production engine

Do not start by auto-transcribing every uploaded recording.

Why this is the right MVP direction:

- server resource usage stays predictable
- operators only spend CPU on recordings they actually inspect
- the backend and UI contracts can stabilize before higher-cost inference is introduced
- the same persistence and API design can later support `whisper.cpp`

## Long-Term Upgrade Direction

The design should be ready to move to `whisper.cpp` when higher transcript quality is needed.

That means the first implementation should not couple the system to Vosk-specific payloads or model assumptions.

Instead, we should keep stable internal abstractions around:

- transcript job lifecycle
- transcript engine identity
- raw STT segment output
- assembled transcript text
- assembled transcript segments with timestamps
- sentence and punctuation normalization
- engine metadata such as `engine`, `model`, and `languageCode`

If those pieces are designed cleanly now, moving from `Vosk` to `whisper.cpp` later becomes an engine swap, not a schema or frontend rewrite.

## Architecture Fit

This should stay aligned to the current repo architecture:

- Android uploads finalized recordings to the backend
- backend stores recording metadata and object keys
- object storage keeps the actual media
- operator console reads recording and transcript metadata from the backend

Transcript generation should be treated as post-recording enrichment, not part of the live streaming path.

That means:

- do not transcribe in the live session flow
- do not block `POST /api/recordings/upload`
- do not move media processing into the Spring Boot request thread
- do not introduce Kafka, workflow engines, or other heavy async infrastructure for MVP

The correct backend-owned transcript pipeline is:

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

The important correction is that `Vosk` output is not the final transcript artifact. It is only the raw recognition stage. The stored transcript should be the result of a transcript assembly service that cleans and aligns post-STT output for operator review and session playback.

Advanced pipeline stage summary:

- `Raw Word Timeline Storage` keeps engine-oriented word timing output available for downstream transcript processing.
- `Punctuation Restoration` converts raw STT text into readable sentence candidates.
- `Transcript Finalization` merges overlaps, removes duplicates, and builds the final playback timeline.
- `AI Summarization` creates concise operator-facing summaries from finalized transcript content.
- `Transcript Search Index` prepares fast keyword and time-aligned lookup.
- `Playback Synchronization` serves subtitle-style, timeline-safe transcript output to the frontend.
- `Retry And Recovery` requeues failed work and keeps the async pipeline fault tolerant.

## Guiding Principles

- resource efficiency first
- quality upgrade path second
- stable transcript contract from the beginning
- simple operations over automation-heavy design

Practical implications:

- first trigger should be manual, not automatic
- first engine should be lightweight, not best-possible quality
- first UI should show transcript status clearly
- transcript storage should already support timestamped segments

## Phased Rollout

## Phase Status Table

| Phase | Status | Notes |
| --- | --- | --- |
| 1 Transcript data model and read APIs | `Implemented` | Transcript tables, status model, and read APIs exist. |
| 2 Operator-initiated transcript requests | `Implemented` | Recording and session transcript generate endpoints are live. |
| 3 Low-resource engine integration | `Implemented` with extension | Vosk is implemented, a pluggable engine seam exists, and `faster-whisper` support is also present. |
| 4 Operator console transcript UX | `Implemented` | Transcript panel, status handling, subtitles, timestamp jumps, and session transcript review are live. |
| 5 Upgrade path to higher-quality engine | `Implemented` | Engine abstraction, config-driven engine selection, operator-driven regeneration, and the `faster-whisper` quality-upgrade path are in place. |
| 6 Operational hardening | `Partially Implemented` | Async transcript poller and session transcript search are in place. Retry flow, concurrency controls, and deeper metrics remain future work. |
| 7 Advanced post-STT pipeline | `Implemented` | The repo now has a post-processing seam, punctuation restoration, recording and session finalization, finalized search and playback projections, session summarization, and stage-aware retry metadata. |

### Phase 1: Transcript Data Model And Read APIs

Objective:
Create the backend persistence and read model for transcripts before wiring any real speech engine.

Scope:

- add transcript tables linked to `recording_asset`
- add transcript lifecycle status tracking
- expose transcript read APIs to the operator console
- keep transcript generation stubbed or manual during this phase

Suggested schema:

- `recording_transcript`
  - `id`
  - `recording_id`
  - `status`
  - `engine`
  - `model`
  - `language_code`
  - `full_text`
  - `error_message`
  - `started_at`
  - `completed_at`
  - `created_at`
  - `updated_at`

- `recording_transcript_segment`
  - `id`
  - `transcript_id`
  - `segment_index`
  - `start_seconds`
  - `end_seconds`
  - `text`
  - `confidence` optional
  - `created_at`

Recommended initial statuses:

- `NOT_REQUESTED`
- `PENDING`
- `PROCESSING`
- `READY`
- `FAILED`

Why keep segments as rows instead of only JSON:

- easier ordering and playback alignment
- cleaner future transcript search
- stable shape for both Vosk and `whisper.cpp`

Suggested backend APIs:

- `GET /api/recordings/{recordingId}/transcript`
- `GET /api/recordings/{recordingId}/transcript/status`

Success criteria:

- backend can persist transcript placeholder records
- operator console can read transcript status and transcript payload shape
- no speech engine is required yet

Current status: `Implemented`

### Phase 2: Operator-Initiated Transcript Requests

Objective:
Add a low-resource transcript trigger that only runs when an operator asks for it.

Scope:

- add transcript generation request API
- add transcript request action in operator console
- create `PENDING` transcript row only when requested

Recommended trigger:

- operator opens a recording
- operator clicks `Generate Transcript`
- backend creates or reuses transcript job
- frontend shows transcript status and refreshes until ready

Suggested backend APIs:

- `POST /api/recordings/{recordingId}/transcript/generate`
- optional later: `POST /api/recordings/{recordingId}/transcript/retry`

Why manual trigger first:

- avoids processing recordings nobody reviews
- reduces background server load
- makes rollout easier to observe and debug

Success criteria:

- transcript generation is operator-driven
- no automatic compute cost is incurred for every upload
- transcript state is visible in the UI

Current status: `Implemented`

### Phase 3: Low-Resource Engine Integration With Vosk

Objective:
Provide the first real transcript engine using a lightweight, open-source CPU-friendly runtime.

Scope:

- add a transcript engine adapter interface in backend
- implement a `VoskTranscriptEngine`
- extract audio from video
- assemble transcript text and segments after STT output is returned

Recommended processing flow:

1. Operator requests transcript generation.
2. Backend creates or reuses a transcript row with `PENDING`.
3. A scheduled backend worker picks pending transcripts.
4. Worker marks transcript `PROCESSING`.
5. Worker downloads or streams media from object storage.
6. Worker extracts mono audio with the backend audio extraction layer.
7. Worker sends audio to the local Vosk-based transcription service.
8. Worker stores or passes forward the raw STT segments as intermediate output.
9. Transcript assembly service merges overlaps, removes duplicate words, builds sentences, restores punctuation, and aligns a clean timeline.
10. Worker stores the assembled transcript text and assembled timeline segments.
11. Worker marks transcript `READY` or `FAILED`.

Transcript assembly service responsibilities:

- merge overlapping transcript fragments from adjacent recording chunks
- remove duplicated words introduced by segment overlap or STT instability
- build readable sentence boundaries from chunked STT phrases
- restore punctuation for operator-readable transcript output
- generate one session-aligned timeline for transcript review, seek, subtitle export, and search

Advanced stage diagram:

```text
Queued transcript job
  -> Audio extraction from recording segment
  -> Vosk STT with word timestamps
  -> Raw word timeline persistence
  -> Punctuation restoration
  -> Transcript finalization
  -> AI summarization
  -> Search indexing
  -> Playback synchronization payload
  -> Retry or recovery handling on failure
  -> Persist session-safe transcript artifacts
```

Why Vosk first:

- smaller models
- lower CPU and memory cost
- good enough for MVP validation
- easier to run on modest infrastructure

Known tradeoff:

- transcript quality may be lower than `whisper.cpp`, especially for noisy or mixed-quality field recordings

Success criteria:

- transcript generation works on modest server resources
- operator can get usable transcripts when needed
- engine output is treated as intermediate data and assembled into backend-owned transcript structures

Current status: `Implemented`

Implementation notes:

- `Vosk` is implemented as the first production engine
- a pluggable engine boundary is in place
- audio extraction now uses embedded JavaCV bindings instead of an external shell `ffmpeg` dependency
- the backend now processes transcript jobs asynchronously through a scheduled poller
- the remaining architecture gap is a dedicated transcript assembly stage instead of persisting near-direct STT output

### Phase 3.5: Advanced Post-STT Processing

Objective:
Turn raw STT output into readable, searchable, playback-safe transcript artifacts.

Scope:

- add punctuation restoration after raw STT output
- finalize transcript sentences and timeline entries
- generate AI summaries from finalized transcript artifacts
- update search indexing from finalized transcript sentences
- ensure playback reads timestamp-aligned finalized transcript rows
- introduce retry and recovery semantics per transcript-processing stage

Recommended advanced flow:

1. Persist raw transcript words or segment output after STT.
2. Restore punctuation and normalize text formatting.
3. Merge chunk overlaps and remove duplicate words.
4. Detect sentence boundaries using pauses, chunk boundaries, and transcript length.
5. Persist finalized sentence timeline entries.
6. Generate AI summary artifacts from finalized transcript text.
7. Build or refresh transcript search indexes.
8. Expose playback-ready transcript entries for subtitle-style synchronization.
9. Requeue recoverable failures with bounded retries and backoff.

Stage responsibilities:

- `PunctuationRestorationService`
  - restore punctuation
  - capitalize sentence starts
  - normalize spacing and formatting

- `TranscriptFinalizationService`
  - merge chunk overlap
  - remove duplicate words
  - build sentence-oriented timeline rows
  - produce playback-ready transcript entries

- `TranscriptSummaryService`
  - generate short and medium transcript summaries
  - derive key events, incidents, or action items later if needed
  - operate only on finalized transcript text, not raw STT fragments

- transcript search projection
  - support keyword search
  - support session transcript lookup
  - support time-range aligned search results
  - currently implemented inside `RecordingTranscriptService.searchSessionTranscript(...)`

- transcript playback projection
  - return timestamp-aligned transcript output
  - support active-line highlighting and transcript seek
  - support subtitle-style playback responses
  - currently implemented through `buildSubtitleVtt(...)`, `buildSessionSubtitleVtt(...)`, and session transcript DTO aggregation

- `TranscriptRecoveryService`
  - retry recoverable failures
  - track chunk or stage processing status
  - requeue incomplete work with backoff

Success criteria:

- raw STT output is no longer treated as the final operator-facing transcript
- finalized transcript rows are readable and deduplicated
- AI summary artifacts are generated from finalized transcript content
- search and playback read from finalized artifacts
- recoverable stage failures can be retried without redoing unrelated successful work

Current status: `Implemented`

Implementation notes:

- `TranscriptPostProcessingService` now owns the seam between raw engine output and persisted transcript artifacts
- `PunctuationRestorationService` and `TranscriptFinalizationService` are implemented as first-class backend stages
- `TranscriptSummaryService` now derives short summary, incident summary, and keyword projections from finalized session transcript text
- transcript search and playback already read finalized artifacts through `RecordingTranscriptService`, `buildSubtitleVtt(...)`, `buildSessionSubtitleVtt(...)`, and the session transcript projection returned to the frontend
- `TranscriptRecoveryService` now classifies recoverable failures, bounded retries are persisted, and stage-aware metadata is exposed through transcript DTOs
- the implementation keeps the public transcript contract stable even though search and playback are still integrated into the main transcript service rather than split into separate `TranscriptSearchService` or `TranscriptPlaybackService` classes

### Phase 4: Operator Console Transcript UX

Objective:
Show transcript content where operators already review recordings.

Scope:

- add transcript state to the recordings view
- show transcript text and timestamped segments
- allow segment click-to-seek in playback

Recommended MVP UI:

- recordings list:
  - transcript badge: `Not Requested`, `Pending`, `Processing`, `Ready`, `Failed`

- recording details panel:
  - generate transcript button when no transcript exists
  - transcript status banner
  - full transcript text
  - timestamped segments
  - retry action only if backend supports it

Recommended interaction pattern:

- operator selects recording
- frontend loads playback URL as today
- frontend also loads transcript status and transcript body
- if transcript is not requested, show `Generate Transcript`
- if transcript is processing, show status placeholder
- if transcript is ready, show segments with clickable timestamps

Success criteria:

- operator can read transcript while reviewing video
- operator can jump playback by transcript timestamp
- transcript failure does not break playback

Current status: `Implemented`

### Phase 5: Upgrade Path To A Higher-Quality Engine

Objective:
Improve transcript quality later without redesigning the backend or frontend.

Scope:

- keep a pluggable transcript engine seam
- keep existing transcript schema and APIs unchanged
- allow engine selection through config
- optionally regenerate selected transcripts using the higher-quality engine

Recommended engine interface shape:

- `TranscriptEngine`
  - `engineName()`
  - `defaultModel()`
  - `transcribe(audioFile, options)`

Recommended normalized result shape:

- detected language
- raw segments from the engine
- assembled full text
- assembled ordered timeline segments
- optional confidence
- engine metadata

The normalized contract should distinguish between:

- engine output, which is recognition-oriented and may contain overlap noise
- assembled output, which is operator-facing and timeline-safe

Suggested upgrade path:

1. keep Vosk as default
2. add a higher-quality implementation beside it
3. make engine configurable by environment
4. optionally add a regenerate flow for important recordings

Success criteria:

- backend storage and APIs do not change
- operator console does not care which engine produced the transcript
- higher quality can be adopted incrementally

Current status: `Implemented`

Implementation notes:

- engine abstraction is in place
- `faster-whisper` is implemented as the higher-quality engine option
- transcript generation endpoints accept an optional engine override for recording and session regeneration flows
- the operator console can switch between available engines without any transcript schema or DTO fork
- transcript diagnostics expose the registered backend engine options so runtime readiness stays visible

### Phase 6: Operational Hardening

Objective:
Improve usability and reliability once the transcript path is proven.

Scope:

- retry endpoint for failed transcripts
- transcript search inside a recording
- background throttling and concurrency limits
- transcript job metrics and logs

Suggested additions:

- `POST /api/recordings/{recordingId}/transcript/retry`
- transcript search term highlighting
- maximum concurrent transcript worker count
- timeout and retry policy
- admin-only engine selection later if needed
- stage-specific retries for punctuation, finalization, indexing, and playback-payload generation
- explicit processing states such as `TRANSCRIBED`, `PUNCTUATED`, and `FINALIZED`

Success criteria:

- transcript processing remains stable under repeated operator use
- failures are diagnosable
- the system can safely scale from low-resource MVP usage

Current status: `Partially Implemented`

Implementation notes:

- queued transcript processing is implemented
- session transcript search is implemented
- operator transcript review now exposes low-confidence transcript indicators
- session transcript review now exposes failed or missing clip intervals with selective retry
- stage-aware recovery metadata, retry counters, and recoverable requeue logic are implemented
- explicit worker concurrency controls and richer metrics remain future work

### Phase 7: Advanced Post-STT Pipeline Integration

Objective:
Integrate the advanced transcript pipeline into the current codebase in small, compatible slices without replacing the working transcript APIs all at once.

Why this phase needs sub-phases:

- the current backend already has a stable orchestration path in `RecordingTranscriptService`
- search, subtitle export, session aggregation, and investigation lookup already depend on `recording_transcript` and `recording_transcript_segment`
- a big-bang rewrite would risk breaking transcript review, subtitles, export, and investigation features together

Phase 7 should therefore be delivered through additive sub-phases that preserve the current contract while moving processing behind better internal stages.

#### Phase 7 Sub-Phase Status Table

| Sub-Phase | Status | Current code fit |
| --- | --- | --- |
| 7.1 Post-processing orchestration seam | `Implemented` | `RecordingTranscriptService.processClaimedTranscript(...)` now routes engine output through dedicated post-processing services before persistence. |
| 7.2 Punctuation restoration layer | `Implemented` | A dedicated punctuation restoration service now normalizes segment text before transcript rows are persisted. |
| 7.3 Transcript finalization and overlap merge | `Implemented` | Per-recording cleanup, overlap trimming, repeated-word collapse, degenerate-output rejection, and session-level finalization in `aggregateSessionTranscript(...)` are in place. |
| 7.4 Search and playback projection hardening | `Implemented` | Search, subtitles, session DTOs, export, and investigation lookup now read finalized transcript artifacts from the backend-owned transcript pipeline. |
| 7.5 Recovery and stage-aware processing state | `Implemented` | Stage markers, retry counters, last-error-stage capture, and bounded recoverable requeue logic are persisted and surfaced through transcript APIs. |

#### Phase 7.1: Post-Processing Orchestration Seam

Objective:
Create a clean internal seam between engine output and persisted transcript rows.

What to integrate into current code:

- keep `RecordingTranscriptEngine.generate(...)` returning engine-oriented output
- stop treating `RecordingTranscriptGenerationResult.fullText()` as the final transcript source of truth
- insert a post-processing step between `engine.generate(...)` and `finalizeTranscriptSuccess(...)`

Best current integration points:

- `RecordingTranscriptService.processClaimedTranscript(...)`
- `RecordingTranscriptService.finalizeTranscriptSuccess(...)`
- `RecordingTranscriptService.toSegments(...)`

Recommended implementation shape:

1. Add a new internal service such as `TranscriptPostProcessingService`.
2. Pass `RecordingTranscriptGenerationResult` plus recording metadata into it.
3. Return a processed result object that still fits the existing `RecordingTranscript` persistence model.
4. Keep the current controller and DTO contracts unchanged.

Why this is the first sub-phase:

- it introduces the seam once
- punctuation, finalization, and future engine normalization can all plug into that seam
- it minimizes churn in controllers, repositories, and frontend consumers

#### Phase 7.2: Punctuation Restoration Layer

Objective:
Turn raw STT text into readable sentence-like transcript text before persistence.

What to integrate into current code:

- create a dedicated `PunctuationRestorationService`
- run it on `TranscriptSegmentPayload` output before `RecordingTranscriptSegment` rows are created
- keep the result in the same `recording_transcript_segment.text` column initially

Best current integration points:

- `RecordingTranscriptService.finalizeTranscriptSuccess(...)`
- `RecordingTranscriptService.toSegments(...)`
- `RecordingTranscriptSupport.joinSegmentText(...)`

Recommended first slice:

1. Add heuristic punctuation restoration with no schema changes.
2. Restore sentence capitalization and spacing.
3. Rebuild `fullText` from punctuated segments instead of raw engine text.

Compatibility benefit:

- `RecordingTranscriptResponse`
- `SessionTranscriptResponse`
- `buildSubtitleVtt(...)`
- `buildSessionSubtitleVtt(...)`

all continue to work while transcript readability improves immediately.

#### Phase 7.3: Transcript Finalization And Overlap Merge

Objective:
Remove duplicate words, merge overlap, and produce timeline-safe transcript segments.

What to integrate into current code:

- add a dedicated `TranscriptFinalizationService`
- start by finalizing per-recording transcript segments
- then extend finalization logic to session aggregation where chunk overlap crosses recording boundaries

Best current integration points:

- per-recording stage:
  - `RecordingTranscriptService.finalizeTranscriptSuccess(...)`
  - `RecordingTranscriptService.toSegments(...)`
- session stage:
  - `RecordingTranscriptService.aggregateSessionTranscript(...)`
  - `RecordingTranscriptService.mapSessionSegment(...)`

Recommended rollout:

1. Normalize and deduplicate segment text inside one recording first.
2. Preserve the existing `recording_transcript_segment` table as the persisted output.
3. Add optional session-level finalization in the aggregation path for overlap between adjacent recording chunks.
4. Only introduce a new `transcript_sentences` table if the current row model becomes too limiting.

Current implementation notes:

- the backend now rejects obviously degenerate long-form STT results, such as repeated single-word output across multiple long segments, instead of marking them `READY`
- `RecordingTranscriptService.aggregateSessionTranscript(...)` finalizes ordered session transcript segments through the post-processing seam before building full text and summary projections

Why this fits the current repo:

- `aggregateSessionTranscript(...)` already computes session-relative offsets
- `SessionTranscriptSegmentResponse` already gives us a session timeline DTO
- investigation search and subtitle generation already read from transcript segments, so we can improve quality without a contract reset

#### Phase 7.4: Search And Playback Projection Hardening

Objective:
Move search and playback to consume finalized transcript artifacts consistently.

What is already present:

- `searchSessionTranscript(...)`
- `buildSubtitleVtt(...)`
- `buildSessionSubtitleVtt(...)`
- `SessionRecordingTranscriptController`
- `RecordingInvestigationService` transcript text lookup

Current implementation notes:

- `searchSessionTranscript(...)` searches the finalized session transcript projection returned by `getSessionTranscript(...)`
- `buildSubtitleVtt(...)` serves finalized persisted recording transcript segments
- `buildSessionSubtitleVtt(...)` serves finalized session-level transcript segments after aggregation cleanup
- `RecordingInvestigationService` searches persisted transcript segments that are stored only after recording-level transcript finalization succeeds

Best current integration points:

- `RecordingTranscriptService.searchSessionTranscript(...)`
- `RecordingInvestigationService`
- `RecordingTranscriptService.buildSubtitleVtt(...)`
- `RecordingTranscriptService.buildSessionSubtitleVtt(...)`

Recommended rollout:

1. Keep existing APIs and DTOs.
2. Switch internal text source from raw persisted text to finalized text.
3. If needed later, add a dedicated sentence projection table or search index without changing the public endpoints first.

#### Phase 7.4b: AI Transcript Summarization

Objective:
Generate concise operator-facing summaries from finalized session transcript content.

Why this belongs after finalization:

- summarization should read clean sentence-level transcript output
- raw STT fragments are too noisy for reliable summaries
- summaries should align with the same transcript version used for search, playback, and export

What to integrate into current code:

- add a Spring AI-backed transcript summarization service
- generate summaries from `SessionTranscriptResponse.fullText()` or an equivalent finalized session transcript projection
- keep summarization optional so transcript review does not fail when the model is unavailable

Best current integration points:

- `RecordingTranscriptService.getSessionTranscript(...)`
- `RecordingTranscriptService.aggregateSessionTranscript(...)`
- `SessionRecordingExportService`
- future investigation or archive summary views

Recommended first slice:

1. Generate a short session summary after session transcript finalization succeeds.
2. Store the summary as additive transcript metadata or a companion summary table.
3. Expose it first through session transcript or export flows, not through a separate orchestration system.
4. Keep the model pluggable so higher-quality summarizers can be swapped later.

Current implementation notes:

- `TranscriptSummaryService` now supports Spring AI-backed summarization through `spring-ai-starter-model-openai`
- AI summarization is gated by `APP_TRANSCRIPT_SUMMARY_AI_ENABLED` and `SPRING_AI_MODEL_CHAT`, so the feature can be enabled only when a chat model endpoint is configured
- the AI path uses structured JSON output mapping and falls back to the existing heuristic summary path if the model call is unavailable or returns unusable output
- AI-backed summaries are cached by finalized transcript fingerprint so repeated session reads do not call the model again for unchanged transcript text
- `RecordingTranscriptService.aggregateSessionTranscript(...)` attaches those summary fields directly to `SessionTranscriptResponse`
- `SessionRecordingExportService` includes the finalized transcript projection in export artifacts without introducing a second summary orchestration layer

MVP summary outputs can include:

- short summary
- incident summary
- optional bullet highlights

Guardrails:

- do not summarize directly from raw STT output
- do not block recording upload or transcript generation request APIs
- do not require summarization for search, playback, or subtitle generation to work
- keep summarization backend-owned and self-hostable by default

#### Phase 7.5: Recovery And Stage-Aware Processing State

Objective:
Add stage-level retry and clearer processing state without replacing the current queue model.

What is already present:

- `RecordingTranscriptScheduler.processPendingTranscripts()`
- `RecordingTranscriptService.retryFailedSessionTranscript(...)`
- `RecordingTranscriptStatus` with `PENDING`, `PROCESSING`, `READY`, and `FAILED`

Current implementation notes:

- `RecordingTranscriptProcessingStage` now includes `QUEUED`, `TRANSCRIBING`, `TRANSCRIBED`, `PUNCTUATED`, `FINALIZED`, and `FAILED`
- `recording_transcript` now persists `last_error_stage` and `retry_count`
- `RecordingTranscriptService.finalizeTranscriptFailure(...)` uses `TranscriptRecoveryService` to classify recoverable failures and requeue bounded retries
- transcript DTOs expose stage, retry count, and last error stage at both recording and session aggregation level

Best current integration points:

- `RecordingTranscriptScheduler`
- `RecordingTranscriptService.claimNextPendingTranscript(...)`
- `RecordingTranscriptService.finalizeTranscriptFailure(...)`
- `backend/src/main/resources/db/migration/V6__create_recording_transcript_tables.sql`

Recommended rollout:

1. Extend persistence with additive stage-tracking fields or a companion processing-job table.
2. Keep `RecordingTranscriptStatus` as the external high-level contract for now.
3. Map detailed internal states back to the existing public statuses until the frontend needs more detail.
4. Add bounded retry count and recoverable failure classification before introducing worker concurrency.

This avoids breaking:

- recording transcript status badges
- session transcript aggregate status
- current retry endpoints and operator flows

#### Recommended Delivery Order Inside Current Code

If we want the safest integration path for the existing repo, Stage 7 should be delivered in this order:

1. Add the orchestration seam in `RecordingTranscriptService`.
2. Add punctuation restoration with no schema change.
3. Add per-recording finalization and dedupe.
4. Switch search, subtitles, and investigation lookup to finalized artifacts.
5. Add stage-aware recovery metadata and bounded retries.
6. Add AI summarization on top of finalized transcript artifacts.
7. Only after that, decide whether a new sentence table or search index table is necessary.

#### Practical First Patch For Implementation

The first code change that best unlocks Phase 7 is:

- create a new post-processing service package under `backend/src/main/java/com/kriyanshtech/bodycam/recording/transcript/`
- introduce a processed transcript result type
- call that service from `RecordingTranscriptService.finalizeTranscriptSuccess(...)`

That gives the current codebase a stable integration seam without forcing a controller, DTO, or schema redesign in the same PR.

## Open-Source Technology Recommendation

### Primary MVP Recommendation: `Vosk`

Use `Vosk` as the first production engine.

Recommended reasons:

- lightweight and offline
- open source and self-hostable
- small model footprint
- appropriate when server resource is the primary concern

Use case fit:

- good for first operator-visible transcript rollout
- good for validating whether transcripts help the workflow
- good when CPU and RAM budgets are tight

Known limitation:

- Vosk can degrade badly on mixed trailer audio, music-heavy soundtracks, overlapping speakers, and noisy field recordings
- when raw Vosk output collapses into repeated low-information text, the backend should treat it as invalid raw STT and fail finalization instead of persisting misleading transcript text

### Quality Upgrade Recommendation: `faster-whisper`

Use `faster-whisper` as the current planned quality upgrade engine.

Recommended reasons:

- higher expected quality on difficult recordings than Vosk
- already implemented behind the repo's transcript engine seam
- exposed through the same transcript persistence and API contract as Vosk
- can be self-hosted behind an OpenAI-compatible transcription endpoint

Use `faster-whisper` when:

- transcript usefulness is proven
- the team is ready to spend more CPU or memory for better accuracy
- selected recordings need higher quality than Vosk can provide

### Not Recommended For This MVP First Pass

- auto-transcription of every upload
- live transcription during active stream
- speaker diarization
- translation workflows
- multi-service event choreography

## Backend Design Notes

Keep transcription responsibilities in a dedicated backend module, for example:

- `backend/.../transcript/controller`
- `backend/.../transcript/service`
- `backend/.../transcript/entity`
- `backend/.../transcript/repository`

Suggested responsibilities:

- transcript service:
  - create transcript requests
  - load transcript results
  - update status
  - choose engine from configuration

- transcript worker:
  - poll pending rows
  - claim work
  - extract audio
  - call transcript engine
  - persist raw STT timeline output
  - hand engine output to punctuation and finalization stages
  - update search and playback-facing transcript artifacts
  - persist assembled transcript results

- transcript assembly service:
  - merge overlapping transcript fragments
  - remove duplicate words
  - build sentence-oriented transcript output
  - restore punctuation
  - generate the persisted timeline used by search, subtitles, and session review

- punctuation restoration service:
  - clean raw transcript text
  - restore sentence punctuation
  - capitalize transcript output

- transcript search service:
  - index finalized transcript rows
  - support keyword and time-aligned search

- transcript summarization service:
  - generate summary artifacts from finalized transcript content
  - support short summary and incident summary variants
  - feed export and investigation views without changing transcript playback contracts

- transcript playback service:
  - serve playback-synchronized transcript sentences
  - support subtitle and active-line use cases

- transcript recovery service:
  - requeue recoverable failures
  - track retry count and failure reason by stage

- transcript engine adapter:
  - hide Vosk-specific and `whisper.cpp`-specific details
  - return engine-native or lightly normalized STT output for assembly

Avoid putting transcription logic directly into recording controller methods.

## Infra Design Notes

For MVP, keep the deployment small.

Suggested infrastructure components:

- backend
- postgres
- minio
- livekit
- optional lightweight transcription service

Recommended MVP deployment shape:

- backend creates transcript jobs only on operator request
- backend scheduled worker processes jobs with low concurrency
- transcription runtime stays local and self-hosted

Optional helper runtime:

- `ffmpeg` installed in backend image, or
- `ffmpeg` available in a worker-friendly sidecar image if separation becomes necessary

Resource-control recommendations:

- process one transcript at a time initially
- cap max worker concurrency through config
- avoid scanning all recordings for missing transcripts
- allow transcript generation only through explicit request in phase 1

## Logging And Observability

Add transcript logs similar to the session and recording paths:

- transcript generation requested
- transcript job created or reused
- transcript worker claimed job
- media fetch started
- audio extraction started and completed
- transcription started and completed
- transcript persisted
- transcript failed with clear error

Suggested identifiers in every log line:

- `recordingId`
- `transcriptId`
- `sessionId`
- `objectKey`
- `status`
- `engine`
- `model`

Recommended extra metrics for resource-sensitive rollout:

- transcript queue size
- transcript processing duration
- transcript failure count
- transcript worker concurrency

## Security And Privacy Notes

For MVP, prefer fully self-hosted transcription.

Why:

- recordings may contain sensitive field audio
- transcript text may be operationally sensitive
- keeping media and transcript generation inside the project stack reduces exposure

If external speech APIs are considered later, treat that as a separate architecture decision.

## Recommended First Implementation Slice

The first buildable slice should be:

1. add transcript tables and entities
2. add transcript status and transcript read endpoints
3. add transcript generation request endpoint
4. wire operator console transcript panel and `Generate Transcript` action
5. add stub worker that stores sample transcript results for UI validation
6. replace stub with real Vosk integration
7. add engine interface seam so `whisper.cpp` can be plugged in later

This order reduces integration risk because schema, status handling, and UI behavior stabilize before real inference is introduced.

## Explicit Deferrals

Do not include these in the first MVP transcript implementation:

- transcript generation during recording upload
- automatic transcript generation for every recording
- live transcription for active sessions
- speaker diarization
- multilingual translation workflows
- transcript editing workflows
- cross-recording full-text indexing
- real-time subtitle overlay in LiveKit

## Final Recommendation

For this repo, the best phased path is:

- Phase 1: transcript schema plus read APIs
- Phase 2: operator-initiated transcript requests
- Phase 3: low-resource Vosk integration
- Phase 4: operator transcript viewer with timestamp jumps
- Phase 5: quality upgrade path to `whisper.cpp`
- Phase 6: retry, throttling, and hardening

That gives the project a low-resource MVP path today while keeping the design clean and ready for higher-quality `whisper.cpp` transcription later.
