
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
