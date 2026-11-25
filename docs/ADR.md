# Architecture Decision Record — Job Portal API

**Team:** APIBP004 | **Approach:** A — REST-First + GraphQL Layer

---

## 1. Business Problem & Consumers

The Job Portal API solves the dual-sided marketplace problem: job seekers need to discover
relevant openings quickly and apply with minimal friction, while employers need to post
vacancies, track applicants, and schedule interviews efficiently. Geographic constraints
(commute distance, remote eligibility) and temporal constraints (interview availability, slot
conflicts) are first-class concerns that generic search cannot address.

Primary consumers: (a) a React-based single-page application used by candidates and
companies; (b) third-party job aggregators that pull listings via REST; (c) internal
analytics dashboards that use GraphQL to fetch composite views in a single round-trip.

---

## 2. Business Capabilities

1. **Job Discovery** — candidates can find jobs filtered by category, salary range, location,
   employment type, and status; results support both keyword search and radius-based
   geospatial queries.
2. **Geospatial Matching** — companies broadcast their office coordinates; candidates can
   search within a radius or bounding box, enabling commute-aware job hunting.
3. **Interview Scheduling** — companies create time slots attached to job postings; the
   system enforces non-overlapping bookings with deterministic conflict errors.
4. **Application Workflow** — candidates submit applications that move through a tracked
   status lifecycle (SUBMITTED → REVIEWED → SHORTLISTED → REJECTED / HIRED).
5. **Multi-Client Support** — the same domain logic is exposed via REST for standard HTTP
   clients and via GraphQL for flexible, field-selective queries with no over-fetching.

---

## 3. Resource Model

**Core resources:**

| Resource | Key Relationships |
|---|---|
| `User` | Root identity; owns a `Candidate` or `Company` profile |
| `Company` | Has many `Job` postings; linked to a `User` |
| `Job` | Belongs to a `Company`; has many `Application`s and `InterviewSlot`s; carries geospatial coordinates |
| Candidate | Belongs to a User; has many Applications and Resumes |
| Resume | Belongs to a Candidate; stores metadata for an authenticated download |
| `Application` | Links a `Candidate` to a `Job`; carries status lifecycle |
| `InterviewSlot` | Belongs to a `Job`; carries a time window; enforces non-overlap |

Job discovery and maps use Job. Calendar operations use separate InterviewSlot records; job-level startsAt/endsAt are not bookings. See the [ER diagram and field dictionary](entities.md) for persisted resources and constraints.

Flyway migrations are authoritative for local and Docker startup. Hibernate validates without creating/dropping tables. Foreign keys reject parent deletion while dependents exist. Demo/bulk seeding is explicit, deterministic, transactional, and recoverable; see [database setup](database-and-demo-data.md).

---

## 4. Approach Decision

We chose **Approach A (REST-First + GraphQL Layer)** because the existing codebase was a
Spring Boot REST service with mature JPA repositories and a working JWT system. REST's
CDN-friendly GET semantics and standard 4xx/5xx status codes also map naturally to our three
domain patterns (cached list pages, bbox-queryable map, 409-validated calendar slots).

The impedance mismatch is handled by routing GraphQL through `/graphql/query` while REST
stays under `/api/v1/`. Both layers call the same service beans, so business logic is
defined once. GraphQL's lack of granular HTTP caching is mitigated by shared in-process
service-layer caching.

---

## 5. Domain Pattern Mapping

| Domain Pattern | REST Endpoint | GraphQL Operation | Domain Entity |
|---|---|---|---|
| **List** | `GET /api/v1/jobs?status=&category=&min_price=&max_price=&sort=&order=&limit=&after=` | `jobs(first, after, status, category, minPrice, maxPrice)` | `Job` |
| **Map** | `GET /api/v1/jobs/map?latitude=&longitude=&radius_km=` or `?min_lat=&min_lon=&max_lat=&max_lon=` | `jobsNear(latitude, longitude, radiusKm)` / `jobsInBox(...)` | `Job` (coordinates) |
| **Calendar** | `GET /api/v1/slots?starts_at=&ends_at=` / `POST /api/v1/slots` (→ 409 on conflict) | `slots(startsAt, endsAt)` / `createSlot(input)` | `InterviewSlot` |

The List API uses sort-aware keyset pagination with ID tie-breakers and nulls last. Cursors carry the sort value, ID, and query context. Traversal is deterministic for unchanged data; it is not a snapshot across concurrent edits. The Map API applies Haversine filtering in-memory
after a SQL bounding-box pre-filter, avoiding PostGIS. The Calendar API enforces
non-overlapping `[startsAt, endsAt)` windows per job, returning HTTP 409 on conflict.

---

## 6. Composite Payload Comparison

**Scenario:** Fetch a job with its company name and all interview slots.

**REST — 2 round-trips:**
```
GET /api/v1/jobs/5          → 312 bytes (job object, no company name, no slots)
GET /api/v1/slots?job_id=5  → 487 bytes (slot list)
Total: 2 round-trips, ~799 bytes payload
```

**GraphQL — 1 round-trip:**
```graphql
query {
  job(id: "5") {
    title status location minSalary maxSalary
    company { companyName industry }
    applications { status }
  }
}
```
Response: ~490 bytes — no over-fetch of unused fields, single request.

GraphQL saves one network round-trip and 39% bytes for this composite view. The DataLoader
batch-loads all company references in a single SQL query, so the GraphQL resolver issues
exactly 2 SQL statements regardless of how many jobs are in the result set.

---

## 7. Error Catalogue

| Code | Cause | HTTP Status | GraphQL extensions.code |
|---|---|---|---|
| `RESOURCE_NOT_FOUND` | Generic resource not found | 404 | RESOURCE_NOT_FOUND |
| `JOB_NOT_FOUND` | Job ID does not exist | 404 | JOB_NOT_FOUND |
| `COMPANY_NOT_FOUND` | Company ID does not exist | 404 | COMPANY_NOT_FOUND |
| `VALIDATION_ERROR` | Request body fails bean validation | 400 | VALIDATION_ERROR |
| `UNAUTHORIZED` | Missing or expired JWT token | 401 | UNAUTHORIZED |
| `FORBIDDEN` | Token valid but role insufficient | 403 | FORBIDDEN |
| `DUPLICATE_RESOURCE` | Email already registered / duplicate key | 409 | DUPLICATE_RESOURCE |
| `APPLICATION_EXISTS` | Candidate already applied for this job | 409 | APPLICATION_EXISTS |
| `SLOT_CONFLICT` | Interview slot overlaps with existing booking | 409 | SLOT_CONFLICT |
| `RATE_LIMITED` | Too many requests in the current window | 429 | RATE_LIMITED |
| `INVALID_CREDENTIALS` | Username/password mismatch at login | 401 | INVALID_CREDENTIALS |
| `INVALID_TOKEN` | JWT signature invalid or malformed | 401 | INVALID_TOKEN |
| `INVALID_DATE_RANGE` | `ends_at` before `starts_at` | 422 | INVALID_DATE_RANGE |
| `SALARY_RANGE_INVALID` | `min_price` greater than `max_price` | 422 | SALARY_RANGE_INVALID |
| `INTERNAL_ERROR` | Unhandled server exception | 500 | INTERNAL_ERROR |

---

## 8. Design Decisions

**Decision 1 — Cursor-based pagination over offset pagination.**
We rejected `page`/`size` offset pagination because concurrent inserts cause page drift.
Keyset pagination on a stable primary key eliminates this; the opaque cursor can change
implementation without breaking clients.

**Decision 2 — Haversine in-memory over PostGIS.**
PostGIS adds a DB extension that complicates Docker and CI. We pre-filter a SQL bounding
box then apply Haversine in Java — accurate to within 0.5% for city-scale queries with no
heavyweight dependency.

**Decision 3 — DataLoader for GraphQL N+1 prevention.**
A naive resolver for 20 jobs with `company` fields would issue 21 SQL queries. The
`MappedBatchLoader` batches all `companyId` values into one `WHERE id IN (...)` query,
registered per request to avoid cross-user cache pollution.

---

## 9. Future Evolution

1. **API v2** — a `/api/v2/` prefix could introduce breaking changes (e.g., structured
   `salary` object) while keeping v1 live. The GraphQL schema already marks `salaryRange`
   `@deprecated` to guide clients toward structured fields before a v2 migration.

2. **Real-time slot availability** — publish booking events to a WebSocket endpoint so
   clients receive live updates without polling.

3. **Elasticsearch for full-text search** — expose a `q=` free-text parameter alongside
   existing structured filters for relevance at scale.

---

## 10. Business Value

1. **Reduced time-to-hire** — the Map API lets candidates filter jobs by commute radius,
   reducing irrelevant applications and shortening the recruiter's review queue.

2. **Zero double-booking** — server-side slot conflict prevention eliminates manual
   availability checks, reducing scheduling errors and no-shows.

3. **Ecosystem extensibility** — REST and GraphQL together serve mobile apps, web clients,
   and aggregators in the style each prefers, without duplicating business logic.
