
---

# PLANS.md

```md id="p3hv9v"
# MVP Delivery Plan

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

Current implementation status:
- Phase 6.1 is implemented
- Phase 6.2 is implemented with `GET /api/sessions/{sessionId}/recordings/timeline`
- Phase 6.3 is implemented with session-based frontend playback and session transcript aggregation
- Phase 6.4 remains pending

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

Planned slices:
- Phase 7.1: async transcript job model
- Phase 7.2: idempotent segment ingest and duplicate protection
- Phase 7.3: session integrity states and recovery UX
- Phase 7.4: smaller Android recording segments
- Phase 7.5: richer session transcript review and search

Current status:
- Phase 7.1 is implemented with queued transcript requests and a backend scheduled worker
- Phase 7.2 is implemented with idempotent segment upload reuse on `sessionId + segmentSequence`
- Phase 7.3 is implemented at the first slice with session integrity states in the timeline response
- Phase 7.5 is partially implemented with Android upload queue visibility and session transcript search
- Phase 7.4 remains pending

Success Criteria:
- transcript generation returns quickly and completes in the background
- duplicate segment uploads do not corrupt session ordering
- operators can distinguish complete sessions from partial or gap-filled sessions
- the system is ready for a production engine swap without changing the user-facing transcript model
