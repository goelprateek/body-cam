# BODY CAM MVP - FULL PROJECT GENERATION INSTRUCTIONS

# Objective

Generate a production-quality MVP for a remote assistance bodycam platform.

The system enables:
- field workers to stream live audio/video
- control room operators to assist remotely
- recording and replay of sessions
- self-hosted deployment using Docker

This is a 1-week MVP.
Focus on:
- simplicity
- reliability
- working streaming
- fast delivery

Avoid overengineering.

---

# Required Technology Stack

## Backend
- Java 25
- Spring Boot 4
- Maven
- PostgreSQL
- JWT authentication
- Flyway migrations

## Frontend
- Angular 21
- TypeScript
- Angular standalone components
- LiveKit Web SDK

## Android App
- Kotlin
- MVVM
- CameraX
- LiveKit Android SDK
- Retrofit

## Infrastructure
- Docker Compose
- LiveKit
- coturn
- MinIO
- PostgreSQL

---

# High-Level Architecture

```text
Android Bodycam App
        ↓
     LiveKit
        ↓
Angular Dashboard

Spring Boot Backend
        ↓
PostgreSQL

LiveKit Recording
        ↓
MinIO Storage

coturn
(TURN/STUN relay)
```

---

# Critical Architecture Rules

## NEVER

- Do NOT create microservices
- Do NOT use Kubernetes
- Do NOT implement OAuth2 Authorization Server
- Do NOT use Kafka
- Do NOT process media in Spring Boot
- Do NOT build custom WebRTC stack
- Do NOT introduce CQRS/Event Sourcing
- Do NOT create excessive abstractions

---

# Architecture Principles

- monolithic backend
- feature-based package structure
- Docker-first deployment
- LiveKit handles media
- Spring Boot handles metadata
- Angular handles operator UI
- Android handles bodycam streaming

---

# Generate Complete Directory Structure

```text
body-cam/
│
├── .vscode/
├── .codex/
├── backend/
├── frontend/
├── android-app/
├── infra/
├── docs/
├── scripts/
│
├── AGENTS.md
├── PLANS.md
├── README.md
└── body-cam.code-workspace
```

---

# Generate Backend Structure

```text
backend/
│
├── src/main/java/com/company/bodycam/
│   │
│   ├── config/
│   ├── auth/
│   ├── session/
│   ├── recording/
│   ├── common/
│   └── BodyCamApplication.java
│
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│
├── Dockerfile
├── pom.xml
└── README.md
```

---

# Generate Infrastructure

Create Docker Compose setup for:
- postgres
- minio
- livekit
- coturn
- backend

---

# MVP Success Criteria

The MVP is successful ONLY IF:
- Android app streams video
- operator can watch stream
- two-way audio works
- recordings are stored
- recordings can be replayed
- all services run in Docker Compose
- backend APIs function correctly

---

# Final Rules

Prefer:
- simple code
- explicit code
- readable code

Avoid:
- enterprise overengineering
- unnecessary abstractions
- speculative architecture

Deliver a COMPLETE runnable MVP foundation.
