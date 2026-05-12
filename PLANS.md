
---

# PLANS.md

```md id="p3hv9v"
# MVP Delivery Plan

## Phase Status Table

| Phase | Area | Status | Notes |
| --- | --- | --- | --- |
| 1 | Infrastructure | `Implemented` | Compose split, LiveKit, MinIO, PostgreSQL, and production readiness wiring are in place. |
| 2 | Backend | `Implemented` | Auth, session APIs, recording APIs, LiveKit token generation, and transcript backend foundations are implemented. |
| 3 | Android | `Implemented` | Login, session start/stop, local recording, LiveKit publishing, and queued upload flow are implemented. |
| 4 | Frontend | `Implemented` | Operator login, dashboard, live session join, archive playback, and transcript review are implemented. |
| 5 | Stabilization | `In Progress` | Reliability hardening is ongoing through later recording and transcript phases. |
| 6 | Continuous Session Recording | `Implemented` first slice | Session timeline metadata, backend timeline APIs, continuous playback, and async export packaging are implemented. Merged session artifacts remain optional future work. |
| 7 | Recording And Transcript Robustness | `Partially Implemented` | Async transcript jobs, idempotent ingest, integrity states, queue visibility, session transcript search, and `10s` segments are implemented. A dedicated transcript assembly stage still remains to be added. |

# Phase 1 - Infrastructure

- setup Docker Compose
- split shared Compose base from local and production overlays
- setup PostgreSQL
- setup LiveKit
- setup coturn
- setup MinIO
- publish LiveKit on a dedicated production `wss://` subdomain for Android and operator clients

Success Criteria:
- all containers healthy
- LiveKit reachable
- production join responses return a public LiveKit `wss://` URL
- TURN working

---

# Phase 2 - Backend

- JWT login
- session APIs
- recording APIs
- LiveKit token generation

Success Criteria:
- APIs functional
- DB persistence working

---

# Phase 3 - Android

- login
- connect to LiveKit
- publish camera/audio
- end session

Success Criteria:
- live stream visible

---

# Phase 4 - Frontend

- login
- operator dashboard
- join session
- playback recordings

Success Criteria:
- operator can assist worker

---

# Phase 5 - Stabilization

- mobile network testing
- reconnect handling
- audio stabilization
- bug fixes

---

# Phase 6 - Continuous Session Recording

Reference doc:
- `docs/architecture/continuous-session-recording-phased.md`

Objective:
- preserve weak-network reliability by keeping chunked uploads
- let operators watch a session as one continuous recording instead of unrelated 30-second clips
- avoid eager backend video merging during ingest

Planned slices:
- Phase 6.1: add ordered segment timeline metadata to Android uploads and backend persistence
- Phase 6.2: add backend session recording timeline and manifest APIs
- Phase 6.3: add frontend continuous session playback over ordered segments
- Phase 6.4: add optional async merged export for evidence/download workflows

Phase status:

| Slice | Status | Notes |
| --- | --- | --- |
| 6.1 Timeline metadata foundation | `Implemented` | Android upload metadata and backend persistence are in place. |
| 6.2 Session timeline model | `Implemented` | `GET /api/sessions/{sessionId}/recordings/timeline` is live. |
| 6.3 Continuous frontend playback | `Implemented` | Session-based playback, transcript aggregation, and transcript-driven seek are live. |
| 6.4 Optional async merge/export | `Implemented` first slice | Async export packaging is implemented. Merged session artifacts remain future work. |

Success Criteria:
- Android can keep recording and queue uploads in poor connectivity
- backend stores session-aligned recording segments with deterministic ordering
- operator can open one session recording and watch it as a continuous timeline
- export/merge remains optional and asynchronous

---

# Phase 7 - Recording And Transcript Robustness

Reference docs:
- `docs/architecture/recording-transcript-roadmap.md`
- `docs/architecture/continuous-session-recording-phased.md`
- `docs/architecture/transcript-integration-phased.md`

Objective:
- harden the continuous-session recording and transcript flow for long-running field sessions
- remove synchronous transcript execution from the request path
- make uploads safe under retries, partial connectivity, and out-of-order arrivals
- prepare the system for higher-quality engines like `whisper.cpp` without redesigning the API contract
- ensure stored transcripts come from a post-STT assembly pipeline instead of near-direct engine output

Planned slices:
- Phase 7.1: async transcript job model
- Phase 7.1b: transcript assembly pipeline
- Phase 7.2: idempotent segment ingest and duplicate protection
- Phase 7.3: session integrity states and recovery UX
- Phase 7.4: smaller Android recording segments
- Phase 7.5: richer session transcript review and search

Phase status:

| Slice | Status | Notes |
| --- | --- | --- |
| 7.1 Async transcript job model | `Implemented` | Transcript requests queue `PENDING` work and a scheduled backend poller processes jobs. |
| 7.1b Transcript assembly pipeline | `Pending` | Raw STT output still needs a first-class assembly stage for overlap merge, dedupe, sentence building, punctuation restoration, and timeline generation. |
| 7.2 Idempotent segment ingest | `Implemented` | Upload reuse is keyed by `sessionId + segmentSequence`. |
| 7.3 Session integrity states and recovery UX | `Implemented` first slice | Timeline now returns `COMPLETE`, `PROCESSING_UPLOADS`, `PARTIAL`, and `HAS_GAPS`. Richer recovery UX is still future work. |
| 7.4 Smaller Android recording segments | `Implemented` | Android recording segments now roll every `10s`. |
| 7.5 Richer session transcript review and search | `Implemented` | Backend session search, timestamp jump, active transcript highlighting, low-confidence indicators, failed-interval review, and selective retry are implemented. |

Success Criteria:
- transcript generation returns quickly and completes in the background
- transcript pipeline is `queue -> audio extractor -> STT -> transcript assembler -> transcript DB`
- duplicate segment uploads do not corrupt session ordering
- operators can distinguish complete sessions from partial or gap-filled sessions
- the system is ready for a production engine swap without changing the user-facing transcript model
