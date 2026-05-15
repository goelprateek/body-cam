# System-Wide Feature Catalog

## Purpose

This reference lists future features that can be implemented across the full `body-cam` system.

Unlike the recording and transcript roadmap, this document is broader. It covers:

- operator workflows
- worker mobile capabilities
- backend platform features
- evidence and compliance needs
- deployment and observability improvements

Use this as the higher-level future feature inventory for planning beyond the current MVP slices.

This document is the single source of truth for system-level feature and roadmap status.

Rules:

- when a system feature is completed in code, update this document
- when a feature becomes partially implemented, update this document
- scoped docs such as transcript, recording, frontend, or deploy references may explain implementation details, but they should not become the primary system-wide completion tracker
- if status wording differs between documents, this file wins

## Current Coverage Table

This table summarizes which major feature areas are already covered in code and which still remain.

| Area | Feature | Status | Current state |
| --- | --- | --- | --- |
| Worker Mobile Experience | Login, session start or stop, local recording, LiveKit publish | `Implemented` | Android worker flow is live. |
| Worker Mobile Experience | Upload queue visibility | `Implemented` | Android UI shows queue summary and pending upload counts. |
| Worker Mobile Experience | Smaller recording segments | `Implemented` | Android now records `10s` segments. |
| Worker Mobile Experience | GPS, device telemetry, failed-upload recovery actions, storage controls | `Pending` | These remain future Android hardening features. |
| Live Session Operations | Operator join and live monitoring | `Implemented` | Live session join and monitoring are in place. |
| Live Session Operations | Operator-created browser session sharing with viewer and publisher invites | `Implemented` | Operators can create a session, generate time-limited viewer or publisher browser invite links, and let a browser participant join the same LiveKit room with role-scoped media permissions. |
| Live Session Operations | Presence, reconnect visibility, in-session markers, escalation workflow | `Pending` | These live-operation features are not yet modeled fully. |
| Recording And Playback | Session timeline playback across ordered segments | `Implemented` | Backend timeline API and frontend continuous playback are live. |
| Recording And Playback | Gap-aware playback and integrity states | `Implemented` | Timeline integrity, explicit gap or overlap details, missing timing warnings, and playback review cues are surfaced in backend and operator UI. |
| Recording And Playback | Session bookmarks, notes, clip extraction, thumbnails, playback speed | `Pending` | These playback enhancements are still left. |
| Transcript And AI | Async transcript jobs | `Implemented` | Scheduled poller and queued transcript requests are in place. |
| Transcript And AI | Session transcript aggregation, timestamp seek, session search | `Implemented` | Search, timestamp navigation, and session transcript review are live. |
| Transcript And AI | Punctuation restoration, finalization, recovery stages, and session summarization | `Implemented` | Finalized transcript output now includes overlap cleanup, punctuation restoration, stage-aware processing, retry flows, and operator-facing session summaries from finalized text. |
| Transcript And AI | Cross-session transcript AI extraction, multi-language, redaction assistance | `Pending` | These advanced transcript features remain future work. |
| Operator Console | Archive, transcript review, live room, investigation search | `Implemented` | Core operator archive and review flows are already in code. |
| Operator Console | Incident workspace, saved filters, notes, notification center | `Pending` | These operator workflow features are still left. |
| Backend Platform | Idempotent ingest, transcript jobs, session integrity, export jobs | `Implemented` | These foundations are already present in the backend. |
| Backend Platform | Transcript stage tracking | `Implemented` | Transcript stage tracking now covers queued, transcribing, transcribed, punctuated, finalized, and failed states across backend and operator DTOs. |
| Backend Platform | Retention jobs, richer auth boundaries, repair flows | `Pending` | These platform-hardening features remain future work. |
| Evidence And Compliance | Export packaging with transcript artifacts | `Implemented` | Async export bundles are implemented. |
| Evidence And Compliance | Approval flows, chain of custody, retention policy controls, redaction workflows | `Pending` | These compliance-oriented features are still left. |
| Observability And Operations | Basic logging and transcript dependency readiness | `Implemented` | Logging, transcript readiness checks, an operator-visible health state, and a deploy smoke-check script path now exist. |
| Observability And Operations | Queue metrics, latency metrics, audit dashboards, alerting | `Pending` | These operational observability features are still left. |
| Infrastructure And Deployment | Local and production compose topology | `Implemented` | Docker-based local and production stack is in place. |
| Infrastructure And Deployment | Transcript deploy smoke-check path | `Implemented` | A backend readiness endpoint and `scripts/deploy/check-transcript-smoke.ps1` now provide a concrete transcript deploy smoke-check path. |
| Infrastructure And Deployment | Backup and restore playbooks, drift validation | `Pending` | These deployment hardening features remain future work. |
| Security And Access Control | Basic authenticated operator access | `Implemented` | Authenticated access exists. |
| Security And Access Control | Stronger role separation, export approval permissions, transcript auditing, secure sharing | `Partially Implemented` | Time-limited browser session invite sharing now exists with separate viewer and publisher links, but broader governance, revocation UX, and approval controls are still left. |

## Advanced Transcript Pipeline

The transcript architecture is no longer just `STT -> DB`.

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

This means transcript features should be evaluated against the stage they belong to:

- recognition stages produce raw timing output
- post-processing stages produce readable sentence timelines
- summarization stages produce concise operator-facing summaries from finalized transcript artifacts
- search and playback stages consume finalized transcript artifacts
- recovery stages protect pipeline resilience

## Planning Guardrails

When choosing from this list:

- keep the backend monolithic unless scale truly forces a split
- preserve weak-network and offline-friendly worker behavior
- keep LiveKit focused on live media transport, not long-term evidence management
- keep Spring Boot as the control plane for metadata, auth, and operator workflows
- prefer compatibility-first additions over large rewrites

## 1-Month Roadmap

This is the recommended next 1-month feature wave to make the platform feel much more complete for recording, live session handling, and investigation while staying within the current monolithic MVP shape.

### 1-Month Priority Table

| Priority | Area | Feature | Why it matters in the next month |
| --- | --- | --- | --- |
| P0 | Transcript And AI | Punctuation restoration and transcript finalization | Makes transcript output readable and investigation-safe instead of near-raw STT text. |
| P0 | Recording And Playback | Richer gap-aware playback UX | Helps operators trust what they are seeing when a session is partial or has upload gaps. |
| P0 | Operator Console | Incident review workspace first slice | Gives operators one place to review timeline, transcript, integrity, and investigation context together. |
| P0 | Backend Platform | Transcript stage tracking and bounded retries | Makes transcript processing diagnosable and safer under failures. |
| P0 | Observability And Operations | Queue depth, transcript latency, upload failure metrics | Makes the platform operable under real field conditions. |
| P1 | Transcript And AI | AI session summary and incident summary | Speeds up long-session review and investigation triage. |
| P1 | Recording And Playback | Session bookmarks and operator notes | Turns passive playback into actionable investigation workflow. |
| P1 | Live Session Operations | Presence, reconnect, and live-quality indicators | Makes live monitoring feel dependable and modern. |
| P1 | Evidence And Compliance | Export request UI and stronger evidence package metadata | Makes handoff and case preparation more usable. |
| P1 | Worker Mobile Experience | Failed-upload diagnostics and recovery actions | Reduces field uncertainty when sync goes wrong. |

### Recommended 1-Month Delivery List

#### Recording And Playback

- richer gap-aware playback UI with visible gap markers and integrity badges
- timeline bookmarks for notable moments
- playback speed controls
- session-level thumbnail or preview strip first slice
- operator-created playback notes tied to session time

#### Live Session Operations

- explicit worker presence and reconnect state in the live room
- live session quality indicators for audio, video, and network state
- in-session event markers that can carry into the post-session timeline
- session watchlist or priority indicator for active incidents

#### Transcript And AI

- punctuation restoration
- transcript sentence finalization and overlap dedupe
- transcript stage tracking with retry visibility
- AI short summary for long sessions
- incident summary for investigation review
- keyword or entity extraction first slice for investigation support

#### Investigation Workflow

- incident review workspace first slice combining:
  - continuous playback
  - transcript panel
  - integrity warnings
  - search hits
  - bookmarks and notes
- saved filters for recordings and investigations
- stronger jump-to-match investigation flow
- transcript and playback side-by-side review polish

#### Worker Mobile Experience

- failed-upload diagnostics with clearer reason states
- manual retry or retry-now action for blocked uploads
- incident marker button during recording
- low-storage warning first slice
- battery and network quality capture into recording metadata

#### Evidence And Export

- evidence export request UI in the operator console
- stronger session summary inside export bundles
- evidence package checksum metadata
- retention-state visibility per session

#### Observability And Operations

- transcript queue depth metrics
- transcript processing duration metrics
- punctuation and finalization latency metrics
- upload success or failure counters
- operator-visible health indicators for critical services
- basic alerting for repeated mobile upload failures or transcript engine outages

### Suggested Weekly Sequencing

#### Week 1

- transcript finalization seam
- punctuation restoration
- transcript stage tracking first slice
- queue and transcript latency metrics

#### Week 2

- gap-aware playback UI improvements
- operator bookmarks and notes
- live session presence and reconnect indicators

#### Week 3

- AI short summary and incident summary
- incident review workspace first slice
- export request UI

#### Week 4

- failed-upload diagnostics on Android
- operator-visible service health
- investigation polish, saved filters, and release hardening

### What This 1-Month Roadmap Should Achieve

By the end of this wave, the platform should feel closer to a complete modern product in three ways:

- recording becomes more trustworthy because integrity, gaps, and recovery are visible
- live sessions become more operable because presence, reconnect, and quality state are exposed
- investigation becomes faster because summaries, bookmarks, notes, and side-by-side transcript review reduce time-to-understanding

## Feature Groups

### 1. Worker Mobile Experience

Future Android features:

- smaller recording segments for safer upload retry behavior
- visible upload queue status per session
- upload retry diagnostics and failed-upload recovery actions
- incident marker button during active recording
- GPS and device telemetry attachment to recording segments
- low-storage warning and retention controls
- optional local media encryption at rest
- background sync policy tuning for weak-signal conditions
- camera health indicators
- battery, thermal, and network quality capture during recording
- offline session resume after app restart or device reboot
- configurable camera presets for field workflows

### 2. Live Session Operations

Future live-session features:

- stronger operator join and assist controls
- session presence and participant health indicators
- worker reconnect and operator reconnect visibility
- in-session event markers
- operator-issued guidance or checklist prompts
- richer session timeline with live and post-session milestones
- escalation workflow for incident sessions
- session watchlist or priority handling
- live session quality indicators for audio, video, and network state

### 3. Recording And Playback

Future recording features:

- session completeness states
- gap-aware playback UI
- timeline bookmarks
- operator-created playback notes
- multi-camera or alternate camera-view support later
- playback speed controls
- clip extraction from long sessions
- export-ready evidence packages
- session-level thumbnails or preview strips
- retention-state visibility per session
- integrity verification for stored recording objects

### 4. Transcript And AI

Future transcript and AI features:

- async transcript job execution
- punctuation restoration
- transcript sentence finalization
- transcript summarization for long sessions
- incident or action summary generation
- transcript retry for failed segments or sessions
- transcript search across one session
- transcript search across many sessions
- timestamp-aware transcript navigation
- confidence or quality indicators
- engine comparison mode for evaluation
- `whisper.cpp` or `faster-whisper` production migration
- transcript regeneration with a newer engine
- summarization for long sessions
- entity or keyword extraction for investigations
- language detection and multi-language support
- redaction assistance for transcript exports

### 5. Operator Console

Future frontend and operator workflow features:

- session-centric archive and investigation views
- saved filters for sessions and recordings
- role-aware dashboards
- incident review workspace
- transcript and playback side-by-side review tools
- evidence export request UI
- operator notes and handoff comments
- session tags and categories
- queue views for transcript, export, or review tasks
- stronger selected-state and timeline navigation polish
- notification center for failed uploads, transcript issues, and export readiness

### 6. Backend Platform

Future backend features:

- idempotent recording segment ingest
- session integrity state model
- background transcript jobs
- transcript processing stage tracking
- export jobs
- retention and purge jobs
- audit trail improvements
- richer operator authorization boundaries
- configurable per-tenant or per-organization policy later if the product expands
- object integrity verification
- safe replay or repair flows for partial session metadata
- API rate limiting and abuse protection
- more explicit error catalog coverage across recording and transcript paths

### 7. Evidence And Compliance

Future evidence features:

- case or incident linkage for sessions
- operator annotations and evidence notes
- export approval flow
- downloadable transcript, subtitle, and manifest artifacts
- chain-of-custody metadata
- evidence package checksum generation
- retention policy controls
- purge and legal-hold semantics later
- access audit history for evidence views and exports
- redaction workflows for video and transcript sharing

### 8. Observability And Operations

Future operational features:

- transcript queue depth metrics
- punctuation latency metrics
- transcript finalization latency metrics
- session completeness metrics
- upload success and failure metrics
- engine-specific transcript error metrics
- production transcript smoke tests after deploy
- storage-capacity monitoring
- object-store health and latency monitoring
- background job audit dashboards
- operator-visible health status for critical services
- structured logging around evidence and transcript events
- alerting for repeated mobile upload failures or transcript engine outages

### 9. Infrastructure And Deployment

Future infrastructure features:

- transcript engine readiness validation during deploy
- production sizing guidance for `whisper.cpp`
- backup and restore playbooks for metadata and object storage
- safer rollout checks for compose updates
- image hardening and dependency review
- deployment smoke checks for recording, transcript, and playback paths
- storage lifecycle policy validation
- environment drift checks between examples and real env files
- disaster-recovery runbooks

### 10. Security And Access Control

Future security features:

- stronger operator role separation
- session access scoping
- export approval permissions
- time-limited playback URLs
- transcript access auditing
- secure evidence sharing flows
- object access hardening
- secret rotation guidance
- tamper-evidence improvements for exported packages

## Suggested Priority Buckets

## Near-Term Status Table

| Feature | Status | Notes |
| --- | --- | --- |
| Async transcript jobs | `Implemented` | Backend scheduled poller and queued transcript requests are in place. |
| Idempotent recording ingest | `Implemented` | Duplicate upload reuse is keyed by `sessionId + segmentSequence`. |
| Session integrity states | `Implemented` | Timeline returns integrity status and gap-related indicators. |
| Gap-aware playback | `Implemented` | Timeline gap state, duplicate sequence warnings, overlap markers, missing timing details, and review cues are surfaced in operator playback. |
| Upload queue visibility on Android | `Implemented` | Android worker UI shows queue summary and pending upload counts. |
| Transcript search within a session | `Implemented` | Backend session transcript search API and frontend session transcript filtering are live. |
| Advanced transcript post-processing stages | `Implemented` | Queueing, STT, punctuation restoration, overlap dedupe, finalized persistence, stage visibility, and session summaries are in place in the current monolith. |
| Production transcript smoke checks | `Implemented` | The backend smoke-check endpoint and deploy script path are both in place for transcript readiness verification. |

### Near-Term

Features with the best MVP hardening value:

- async transcript jobs
- punctuation restoration and transcript finalization
- idempotent recording ingest
- session integrity states
- gap-aware playback
- upload queue visibility on Android
- transcript search within a session
- production transcript smoke checks

### Near-Term Single PR Bundle

Recommended single-PR style bundle for the next near-term implementation wave:

Current progress as of `2026-05-13`:

- implemented transcript post-processing seam after STT
- implemented punctuation restoration and overlap-dedupe finalization first slice
- implemented transcript processing stage metadata and operator-visible stage status
- implemented transcript smoke-check endpoint path
- implemented deploy smoke-check script path
- implemented richer playback integrity warning details and explicit gap detail markers in the operator console
- implemented session transcript summaries from finalized transcript text

**PR Goal**

Harden the existing recording and transcript experience so operators can trust partial sessions, read cleaner transcripts, and verify transcript readiness after deploy.

**Include In One Cohesive PR**

- gap-aware playback UX first complete slice
- transcript punctuation restoration and transcript finalization first slice
- stage-aware transcript processing visibility first slice
- production transcript smoke-check path

**Single PR Scope**

Backend:

- add transcript post-processing seam after STT and before persisted transcript output
- add punctuation restoration and finalized transcript text generation
- add additive transcript stage metadata for `TRANSCRIBED`, `PUNCTUATED`, and `FINALIZED` mapping
- add or document transcript smoke-check endpoint or verification flow for deploy validation

Frontend:

- show richer session integrity badges and gap markers in playback
- surface partial-session or missing-interval warnings more clearly
- keep transcript review bound to finalized text output
- expose clearer transcript processing state where useful

Ops and deploy:

- add transcript smoke-check step to deploy validation guidance
- track queue depth or transcript-processing duration in the first operational slice

**Why These Belong Together**

- they all harden the same operator journey:
  recording upload -> session playback -> transcript review -> deploy confidence
- they improve trust without changing the repo's core MVP shape
- they avoid scattering small fixes across unrelated PRs

**Out Of Scope For This Single PR**

- AI summarization
- multi-language transcription
- bookmarks and operator notes
- incident workspace
- export approval or compliance workflows

**Done Criteria**

- transcript text shown in the operator console is cleaner than raw STT output
- gap or integrity state is visible during session playback
- transcript processing state is clearer in backend or UI flow
- deploy guidance includes a transcript smoke-check path
- `system-feature-catalog.md` is updated after implementation lands

Implementation note:

- this near-term PR-style slice is now landed in code
- model-backed AI summarization, deploy-gate automation, and richer operational metrics remain follow-up work

### Mid-Term

Features that improve operational depth and investigation workflows:

- evidence export packages
- session bookmarks and annotations
- transcript regeneration with stronger engines
- operator incident review workspace
- richer observability dashboards
- retention and audit controls

### Longer-Term

Features for a more mature platform:

- case management linkage
- transcript summarization and extraction
- multi-language transcription
- advanced redaction workflows
- stronger compliance and governance flows
- more advanced device telemetry and field-state analytics

## Suggested Use

Use this document when:

- creating future phases in `PLANS.md`
- deciding what comes after MVP stabilization
- grouping features into implementation waves
- reviewing whether a new request belongs to recording, transcript, operator UX, or platform hardening

## Reference Documents

Use these supporting references alongside this catalog:

- `docs/architecture/system-overview.md`
  - use for the end-to-end platform picture and system boundaries
- `docs/architecture/frontend-operator-console.md`
  - use for operator console structure, UX zones, and frontend extension direction
- `docs/architecture/transcript-integration-phased.md`
  - use for transcript architecture, phased integration, and backend integration seams
- `docs/architecture/continuous-session-recording-phased.md`
  - use for continuous recording, ordered segment playback, and session timeline design
- `docs/architecture/recording-transcript-roadmap.md`
  - use for recording and transcript-specific roadmap detail and implementation gaps
- `docs/flows/live-session-flow.md`
  - use for live-session movement through backend and LiveKit flows
- `docs/flows/recording-sync-flow.md`
  - use for recording upload, queue, and sync behavior walkthroughs

Reference rule:

- this file is the authoritative system-level feature and roadmap tracker
- the supporting references explain architecture, scoped rollout detail, or flow behavior
- when implementation status changes, update this file first and then align the scoped references if needed

For recording and transcript-specific sequencing detail, prefer:

- `docs/architecture/continuous-session-recording-phased.md`
- `docs/architecture/transcript-integration-phased.md`
- `docs/architecture/recording-transcript-roadmap.md`
