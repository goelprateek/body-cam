# Transcript Integration Plan

## Goal

Add recording transcript support to the `body-cam` MVP so operators can read what was said in uploaded recordings from the operator console.

The transcript capability should:

- stay open source
- remain self-hosted
- fit the current monolithic MVP architecture
- minimize server CPU and memory usage
- avoid blocking recording upload
- stay easy to upgrade to higher-quality transcription later

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
- transcript text
- transcript segments with timestamps
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

### Phase 3: Low-Resource Engine Integration With Vosk

Objective:
Provide the first real transcript engine using a lightweight, open-source CPU-friendly runtime.

Scope:

- add a transcript engine adapter interface in backend
- implement a `VoskTranscriptEngine`
- extract audio from video with `ffmpeg`
- store transcript text and segments

Recommended processing flow:

1. Operator requests transcript generation.
2. Backend creates or reuses a transcript row with `PENDING`.
3. A scheduled backend worker picks pending transcripts.
4. Worker marks transcript `PROCESSING`.
5. Worker downloads or streams media from object storage.
6. Worker extracts mono audio with `ffmpeg`.
7. Worker sends audio to the local Vosk-based transcription service.
8. Worker normalizes the result into the repo transcript model.
9. Worker stores transcript text and segments.
10. Worker marks transcript `READY` or `FAILED`.

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
- engine output is normalized into backend-owned transcript structures

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

### Phase 5: Upgrade Path To `whisper.cpp`

Objective:
Improve transcript quality later without redesigning the backend or frontend.

Scope:

- add a `WhisperCppTranscriptEngine`
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
- full text
- ordered segments
- optional confidence
- engine metadata

Suggested upgrade path:

1. keep Vosk as default
2. add `whisper.cpp` implementation beside it
3. make engine configurable by environment
4. optionally add a regenerate flow for important recordings

Success criteria:

- backend storage and APIs do not change
- operator console does not care which engine produced the transcript
- higher quality can be adopted incrementally

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

Success criteria:

- transcript processing remains stable under repeated operator use
- failures are diagnosable
- the system can safely scale from low-resource MVP usage

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

### Quality Upgrade Recommendation: `whisper.cpp`

Use `whisper.cpp` as the planned quality upgrade engine.

Recommended reasons:

- higher expected quality on difficult recordings than Vosk
- CPU-friendly relative to heavier Python-based model-serving stacks
- pure C or C++ deployment model
- quantized models help resource control

Use `whisper.cpp` when:

- transcript usefulness is proven
- the team is ready to spend more CPU or memory for better accuracy
- selected recordings need higher quality than Vosk can provide

### Not Recommended For This MVP First Pass

- `faster-whisper` as the starting engine when server capacity is already a concern
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
  - call `ffmpeg`
  - call transcript engine
  - persist normalized results

- transcript engine adapter:
  - hide Vosk-specific and `whisper.cpp`-specific details
  - return a backend-owned normalized transcript model

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
