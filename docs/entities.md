# Entity model and data dictionary

The seven domain tables are created by [V1__job_portal_schema.sql](../backend/src/main/resources/db/migration/V1__job_portal_schema.sql). Flyway migrates before JPA starts; Hibernate validates mappings. Java entities keep scalar relationship IDs, while the migration enforces foreign keys. REST uses camelCase; SQL uses snake_case. These tables list persisted fields, including fields not writable through every API.

## Relationships

~~~mermaid
erDiagram
    USERS ||--o| CANDIDATES : "user_id unique"
    USERS ||--o| COMPANIES : "user_id unique"
    COMPANIES ||--o{ JOBS : company_id
    CANDIDATES ||--o{ APPLICATIONS : candidate_id
    JOBS ||--o{ APPLICATIONS : job_id
    CANDIDATES ||--o{ RESUMES : candidate_id
    JOBS ||--o{ INTERVIEW_SLOTS : job_id
    CANDIDATES o|--o{ INTERVIEW_SLOTS : "nullable candidate_id"
~~~

A user can have zero or one profile of each type at database level. Application policy permits candidate profiles only for CANDIDATE accounts and company profiles only for COMPANY accounts. The database does not enforce that cross-table role policy or prevent both profile types for one user through direct SQL.

**Identifiers are not interchangeable.** User 1001 may own candidate 2001; an application or slot must reference candidate 2001. Each table has its own identity sequence; IDs need not start at 1 or be contiguous. Interview-slot IDs are 64-bit; other domain IDs are 32-bit. GraphQL ID values serialize as strings.

All FKs use default NO ACTION behavior: deleting a referenced parent is rejected. Delete dependents explicitly first. REST returns 400 DATA_INTEGRITY_ERROR when a database constraint blocks deletion; GraphQL uses errors[].extensions.code=DATA_INTEGRITY_ERROR. No implicit cascade bypasses authorization or file cleanup. Delete resumes through their endpoint to remove files; SQL deletion removes metadata only.

## User — users

| Field | SQL type | Nullable | Key/default/meaning |
| --- | --- | --- | --- |
| id | integer | No | Generated PK |
| email | varchar(255) | No | Unique login identifier; database comparison is case-sensitive |
| password | varchar(255) | No | BCrypt hash; never returned by APIs |
| role | varchar(255) | No | DB check: CANDIDATE, COMPANY, ADMIN |
| created_at | timestamp | Yes | SQL default CURRENT_TIMESTAMP; Java initializes LocalDateTime |

Public registration accepts CANDIDATE and COMPANY only. REST user responses omit password. GraphQL AuthPayload exposes token, email, and role, not a User object.

## Candidate — candidates

| Field | SQL type | Nullable | Key/default/meaning |
| --- | --- | --- | --- |
| id | integer | No | Generated PK |
| user_id | integer | No | Unique FK to users.id |
| full_name | varchar(255) | No | API length 2–255 |
| phone | varchar(255) | Yes | Nonempty API input requires at least 10 digits |
| location | varchar(255) | Yes | Human-readable place, not coordinates |
| experience | varchar(255) | Yes | Free-text summary |

Candidate CRUD is REST-only. Candidate IDs also appear in applications, resumes, and authorized slot responses.

## Company — companies

| Field | SQL type | Nullable | Key/default/meaning |
| --- | --- | --- | --- |
| id | integer | No | Generated PK |
| user_id | integer | No | Unique FK to users.id |
| company_name | varchar(255) | No | API length 2–255 |
| industry | varchar(255) | Yes | Free text |
| address | varchar(255) | Yes | Postal address |
| approved | boolean | Yes | SQL/Java default false; only ADMIN may set through REST |

GraphQL exposes id, companyName, industry, address, approved, and nested jobs. GraphQL declares approved non-null, so direct SQL must not insert null although the current entity/database allow it. Approval does not currently gate publication.

## Job — jobs

| Field | SQL type | Nullable | Key/default/meaning |
| --- | --- | --- | --- |
| id | integer | No | Generated PK |
| company_id | integer | No | FK to companies.id |
| title | varchar(255) | No | API length 3–255 |
| description | text | Yes | API requires nonblank text of at least 20 characters |
| skills_required | text | Yes | Free-text skills |
| salary_range | varchar(255) | Yes | Deprecated display; application generates it from numeric bounds when present |
| location | varchar(255) | Yes | Human-readable place |
| employment_type | varchar(255) | Yes | API validates documented spellings; no DB enum check |
| created_at | timestamp | Yes | SQL default CURRENT_TIMESTAMP; Java initializes LocalDateTime |
| latitude | double precision | Yes | Both coordinates null or both present; -90..90 |
| longitude | double precision | Yes | -180..180 |
| min_salary | integer | Yes | Whole USD per year, nonnegative |
| max_salary | integer | Yes | Whole USD per year, nonnegative; at least min_salary when both present |
| category | varchar(255) | Yes | Free text |
| status | varchar(20) | No | Default OPEN; DB check OPEN, CLOSED, DRAFT |
| starts_at | timestamp | Yes | Optional job-level time |
| ends_at | timestamp | Yes | Optional job-level time |

Job times are **not InterviewSlot records**. Creating a slot does not populate these columns; seeded jobs leave them null. GraphQL resolves company and private applications separately. Maps expose a property subset. REST and GraphQL persist the documented editable fields; ID/createdAt are server-managed. See [job discovery](job-discovery.md) for USD annual salary units, generated legacy display, and update semantics. V2 normalizes existing priced display strings; direct SQL can still bypass application-level display generation.

## Application — applications

| Field | SQL type | Nullable | Key/default/meaning |
| --- | --- | --- | --- |
| id | integer | No | Generated PK |
| candidate_id | integer | No | FK to candidates.id |
| job_id | integer | No | FK to jobs.id |
| status | varchar(255) | Yes | Default SUBMITTED; DB check SUBMITTED, REVIEWED, SHORTLISTED, REJECTED, HIRED |
| applied_at | timestamp | Yes | SQL default CURRENT_TIMESTAMP; server assigns on REST creation |

The candidate_id/job_id pair is unique. REST returns all fields and assigns candidate/status/time independently of untrusted input. GraphQL exposes id, status, and appliedAt through authorized Job.applications; no application mutations exist. The service requires an OPEN job on submission and enforces SUBMITTED → REVIEWED → SHORTLISTED → HIRED, with REJECTED allowed from each nonterminal state. These cross-row and transition rules are enforced by the service, not SQL checks.

## Resume — resumes

| Field | SQL type | Nullable | Key/default/meaning |
| --- | --- | --- | --- |
| id | integer | No | Generated PK |
| candidate_id | integer | No | FK to candidates.id; multiple resumes allowed |
| file_path | varchar(500) | No | Opaque storage reference, not file bytes |
| uploaded_at | timestamp | Yes | SQL default CURRENT_TIMESTAMP; Java initializes LocalDateTime |

Resume metadata/file operations are REST-only. Download through the authenticated endpoint. Files live in the configured upload directory, persisted in Compose's resume_data volume. SQL cannot guarantee referenced files exist.

## InterviewSlot — interview_slots

| Field | SQL type | Nullable | Key/default/meaning |
| --- | --- | --- | --- |
| id | bigint | No | Generated PK |
| job_id | integer | No | FK to jobs.id |
| candidate_id | integer | Yes | FK to candidates.id; null means unassigned |
| starts_at | timestamp | No | Slot start |
| ends_at | timestamp | No | DB check: strictly later than starts_at |
| status | varchar(30) | No | Default SCHEDULED; no DB enum check |
| created_at | timestamp | Yes | SQL default CURRENT_TIMESTAMP; Java initializes LocalDateTime |

REST/GraphQL redact candidateId unless the caller is the assigned candidate, job owner, or ADMIN. The application creates SCHEDULED slots and deletes on cancellation; no cancellation history exists. Service creation locks the parent job row until commit to serialize same-job overlap checks, including when no slots exist. Direct SQL bypasses this service rule; there is no SQL exclusion constraint. A candidate need not have an application. Assigned and unassigned reservations both block the interval; adjacent intervals are allowed. See [scheduling and lifecycle](scheduling-and-applications.md).

## Defaults, enforcement, and internal tables

Domain timestamps retain PostgreSQL timestamp without time zone / Java LocalDateTime, with a UTC interpretation. Input offsets normalize to UTC, output includes Z, and server clocks explicitly use UTC. Existing values are interpreted as UTC without shifting stored data. SQL timestamp defaults depend on the database session timezone; external writers must set their session to UTC. SQL defaults apply when columns are omitted, not explicit nulls. API validation is stricter than some nullable columns; direct SQL can create values that fail non-null GraphQL fields, such as null company approval or application status.

The migration enforces displayed FKs, email/profile/application uniqueness, user/job/application state values, coordinate/salary bounds, and ordered slot intervals. It does not enforce ownership, cross-table role matching, transitions, or overlap across rows.

Internal tables are not API resources:

- flyway_schema_history records migration versions/checksums.
- demo_seed_records maps stable seed_key to resource_type/resource_id, with uniqueness for each mapped resource. A generic FK cannot target multiple domain tables; stale entries deliberately allow deleted records to be recovered.
- demo_seed_state contains one lock/configuration row. random_seed and base_time prevent mixing incompatible datasets; row locking serializes seed runs.

After V1 is applied, add a new migration instead of editing it. The manual psql wrapper includes V1 rather than maintaining a second schema.
