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
   skills, and status; results support both keyword search and radius-based
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

## 6. Equivalent REST and GraphQL Views

Scenario: fetch a job's ID/title/company name and every interview slot overlapping the same UTC window. Slots are a top-level query; neither transport supports a job_id calendar filter. Filter the returned slots by jobId in the client if the view requires only that job.

REST uses three requests with IDs captured from previous responses:

~~~text
GET /api/v1/jobs/{jobId}
GET /api/v1/companies/{companyId}     # companyId from the job response
GET /api/v1/slots?starts_at=2030-01-10T10:00:00Z&ends_at=2030-01-10T11:00:00Z
~~~

GraphQL uses one request with the same job ID and window:

~~~graphql
query JobAndCalendar($id: ID!, $start: String!, $end: String!) {
  job(id: $id) { id title company { id companyName } }
  slots(startsAt: $start, endsAt: $end) { id jobId startsAt endsAt status }
}
~~~

The executable query is [examples/job-and-calendar.graphql](examples/job-and-calendar.graphql). The [walkthrough](api-walkthrough.md) executes both forms and compares their shared fields and slot sets. Request counts describe these specific APIs; no payload-size or fixed SQL-count claim is made. DataLoader batches company references when those fields are requested, while actual database work depends on query shape, pagination, authorization, and caches.

---

## 7. Error Catalogue

REST returns the shared error body; GraphQL business failures generally use HTTP 200 and errors[].extensions.code. Authentication/rate-limit filters can return transport-level errors before GraphQL execution.

| Code | REST status | Cause |
| --- | --- | --- |
| RESOURCE_NOT_FOUND | 404 | Missing resource or related profile |
| VALIDATION_ERROR | 400 | Invalid fields, coordinates, salary bounds, or dates |
| INVALID_REQUEST | 400 | Malformed JSON, invalid type, or enum |
| UNAUTHORIZED | 401 | Missing/invalid authentication or incorrect login |
| FORBIDDEN | 403 | Role or ownership does not permit the operation |
| DUPLICATE_RESOURCE | 409 | Existing email or candidate/job application |
| SLOT_CONFLICT | 409 | Same-job reservation overlaps |
| APPLICATION_CONFLICT | 409 | Job not OPEN on submission or invalid status transition |
| DATA_INTEGRITY_ERROR | 400 | Database constraints reject the operation |
| PAYLOAD_TOO_LARGE | 413 | Resume or multipart request exceeds its limit |
| UNSUPPORTED_MEDIA_TYPE | 415 | Wrong request content type |
| METHOD_NOT_ALLOWED | 405 | Unsupported HTTP method |
| RATE_LIMITED | 429 | Per-IP quota exceeded; Retry-After is returned |
| INTERNAL_ERROR | 500 | Unexpected server failure |

See [API contracts](api-contracts.md) and the per-operation Swagger responses for exact messages and applicable codes. GraphQL validation errors generated by the GraphQL engine may not have an application error code.

---

## 8. Design Decisions

**Decision 1 — Cursor-based pagination over offset pagination.**
We rejected `page`/`size` offset pagination because concurrent inserts cause page drift.
Sort-aware keyset pagination uses an ID tie-breaker for deterministic traversal of unchanged data. Concurrent edits can still move records across pages; cursors are versioned and unsupported versions must restart from the first page.

**Decision 2 — Haversine in-memory over PostGIS.**
PostGIS adds a DB extension that complicates Docker and CI. We pre-filter a SQL bounding
box then apply spherical Haversine distance in Java with Earth radius 6371 km. This is straight-line distance, not travel distance.

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

2. **Same-job conflict prevention** — service writes serialize overlapping reservations for a job; cross-job candidate conflicts are not checked.

3. **Ecosystem extensibility** — REST and GraphQL together serve mobile apps, web clients,
   and aggregators in the style each prefers, without duplicating business logic.


## UTC timestamps and transactional workflow rules

API timestamps normalize offset inputs to UTC and serialize with Z. Offset-free inputs are interpreted as UTC for compatibility. Existing timestamp-without-time-zone columns are retained and explicitly interpreted as UTC; no automatic historical timezone shift is possible without knowing their original timezone. Server clocks explicitly use UTC. External writers using SQL timestamp defaults must set their session timezone to UTC.

Interview-slot creation locks the parent job row until commit, then checks same-job overlap with half-open intervals. This serializes simultaneous first reservations as well as subsequent ones without a PostgreSQL-only exclusion extension. The guarantee applies to service writes; direct SQL must follow the same locking protocol. Application submission uses the same parent lock for duplicate checks and OPEN-job eligibility, and status changes lock the application row. Notifications are queued only after a successful commit and remain best-effort without a durable outbox.
