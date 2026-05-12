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

## Planning Guardrails

When choosing from this list:

- keep the backend monolithic unless scale truly forces a split
- preserve weak-network and offline-friendly worker behavior
- keep LiveKit focused on live media transport, not long-term evidence management
- keep Spring Boot as the control plane for metadata, auth, and operator workflows
- prefer compatibility-first additions over large rewrites

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
| Gap-aware playback | `Partially Implemented` | Timeline gap state is surfaced, but richer recovery and visualization remain future work. |
| Upload queue visibility on Android | `Implemented` | Android worker UI shows queue summary and pending upload counts. |
| Transcript search within a session | `Implemented` | Backend session transcript search API and frontend session transcript filtering are live. |
| Production transcript smoke checks | `Pending` | Prod readiness is improved, but transcript smoke verification is not yet part of the deploy gate. |

### Near-Term

Features with the best MVP hardening value:

- async transcript jobs
- idempotent recording ingest
- session integrity states
- gap-aware playback
- upload queue visibility on Android
- transcript search within a session
- production transcript smoke checks

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

For recording and transcript-specific sequencing, prefer:

- `docs/architecture/continuous-session-recording-phased.md`
- `docs/architecture/transcript-integration-phased.md`
- `docs/architecture/recording-transcript-roadmap.md`
