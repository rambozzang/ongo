# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**onGo** is a multi-platform creator management SaaS for Korean content creators. It enables uploading one video simultaneously to YouTube, TikTok, Instagram Reels, and Naver Clip, with AI-powered metadata optimization and unified analytics.

Reference: `CreFlow_Development_Guide_v2.0_1.md` contains the full specification (UI wireframes, business rules, DB schema, API design).

## Tech Stack

- **Backend**: Spring Boot 4.0.2 + JDK 25 + Kotlin, Gradle 9
- **DB Access**: jOOQ 3.19+ (sole DB access layer — no JPA/Hibernate)
- **AI**: Spring AI 2.x (Anthropic Claude as primary, OpenAI Whisper for STT)
- **Frontend**: Vue.js 3 + Vite + Tailwind CSS
- **Database**: PostgreSQL 16 (JSONB, partitioning, full-text search)
- **Cache**: Caffeine (in-process) + Bucket4j for rate limiting
- **Async**: Spring Events + Virtual Threads (no external MQ in Phase 1)
- **Storage**: MinIO (local dev) → AWS S3 (production)
- **File Upload**: Tus Protocol (resumable uploads)
- **CI/CD**: Jenkins + Docker + Gradle 9

## Architecture

Modular Monolith with Clean Architecture layers:

```
onGo-api/              # Controllers (presentation layer)
onGo-application/      # UseCases + Spring Event handlers
onGo-domain/           # Entities, Repository interfaces, domain logic
onGo-infrastructure/   # jOOQ repos, platform clients, Spring AI config, S3, payment
onGo-common/           # Shared utilities (ResData, etc.)
```

Key architectural decisions:

- **jOOQ only** — no JPA/Hibernate. All DB access through jOOQ type-safe queries with code generation from DB schema
- **Caffeine + Spring Events + Virtual Threads** — no Redis/RabbitMQ in Phase 1
- Platform API clients use `@HttpExchange` interfaces (youtube/, tiktok/, instagram/, naverclip/)
- AI calls use `ChatClient.builder()` with Structured Output for typed JSON responses

## API Response Convention

All API responses use the `ResData<T>` wrapper (defined in `onGo-common`):

```kotlin
data class ResData<T>(
    var success: Boolean = true,
    val message: String? = null,
    var data: T? = null,
    val error: String? = null,
)
```

Frontend TypeScript interface mirrors this structure.

## AI Credit System

AI features consume credits (deducted per call). Credit deduction order:

1. Free monthly credits first (reset on 1st of month)
2. Purchased credits (FIFO by expiration date)
3. When exhausted → `InsufficientCreditException` → only AI features disabled

Credit deduction uses **pessimistic locking** (`FOR UPDATE`) on `ai_credits` table for concurrency safety.

## Local Development

### Database (Docker)

```bash
docker-compose up -d    # Start PostgreSQL 16
docker-compose down     # Stop
```

Connection: `localhost:5432`, user `ongo_user`, password `ongo_password`, database `ongo_db`

### Build & Run

```bash
# Backend
./gradlew build
./gradlew bootRun

# Frontend
cd frontend
npm install
npm run dev

# jOOQ code generation (after DB schema changes)
./gradlew generateJooq
```

### API Documentation

Swagger UI available at `http://localhost:8777/swagger-ui.html` when backend is running.

## Key Business Rules

- Social login only (Google/Kakao OAuth 2.0), no email/password
- JWT: Access Token 30min, Refresh Token 7 days
- Platform tokens stored AES-256 encrypted
- File upload validation: extension → MIME type → file size (client + server dual validation)
- Upload status flow: `DRAFT → UPLOADING → [per-platform: PROCESSING/REVIEW → PUBLISHED/FAILED/REJECTED]`
- Plan tiers: Free / Starter (9,900 KRW) / Pro (19,900 KRW) / Business (49,900 KRW) with different limits on uploads, storage, connected platforms, and free AI credits

## API Prefix

All endpoints use `/api/v1/` prefix. Key groups: `auth/`, `channels/`, `videos/`, `schedules/`, `analytics/`, `ai/`, `credits/`, `subscriptions/`, `payments/`
