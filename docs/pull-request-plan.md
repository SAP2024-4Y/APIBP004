# Implementation plan

Related issues are grouped into seven pull requests, including the merged registration fix. Issue numbers below are the GitHub issue numbers for SAP2024-4Y/APIBP004. Each implementation PR includes its relevant documentation and regression tests.

| PR | Scope | GitHub issues |
| --- | --- | --- |
| 1 | Restrict public registration roles — merged as PR #34 | #27 |
| 2 | Shared REST and GraphQL authorization, resource ownership, and private fields — merged as PR #35 | #25, #26 |
| 3 | Merged as PR #36 — API contracts: Swagger/OpenAPI and GraphQL schema consistency, descriptions, examples, validation, errors, resume handling, and rate-limit documentation | #6, #7, #8, #9, #10, #11, #16, #30, #31 |
| 4 | Merged as PR #37 — Database schema, entity documentation, valid seed relationships, and bulk data | #12, #13, #14, #15 |
| 5 | Merged as PR #38 — Job fields, salary semantics, filtering, pagination, and geographic search | #17, #18, #19, #20, #21, #22, #29 |
| 6 | Merged as PR #39 — UTC timestamps, scheduling rules, and application lifecycle | #23, #24, #28 |
| 7 | CI, remaining contract coverage, and executable API walkthrough | #32, #33 |

Create each branch from the updated main branch after the preceding PR is merged. A PR closes an issue only when its acceptance criteria are satisfied. Supporting changes in an earlier PR may contribute to an issue without closing it.

## PR 2

Title: **Enforce resource ownership across REST and GraphQL**

Branch: `fix/resource-ownership-and-graphql-auth`

Description:

REST and GraphQL now use a shared policy to enforce both role and resource ownership. Candidates can manage their own profiles, applications, resumes, and bookings. Companies can manage their own jobs and access applicants to those jobs. Administrative access remains explicit.

The change prevents owner-ID overrides and self-service role escalation, protects GraphQL mutations and nested application fields, and redacts candidate IDs in public slot responses. Request identity is retained across asynchronous GraphQL field resolution. GraphQL authorization errors use the active Kickstart error handler and expose stable UNAUTHORIZED/FORBIDDEN codes.

The access matrix and manual verification steps are in `docs/authorization.md`. Database-backed tests exercise actual HTTP requests with JWTs, cross-owner attempts, persisted-state checks, and GraphQL queries/mutations.

Validation: `mvn -o -f backend/pom.xml test` — 49 tests passed. The OpenAPI YAML parses successfully.

Closes #25
Closes #26

## PR 3

Title: **Align API documentation with runtime contracts and validation**

Branch: `fix/api-contracts-and-documentation`

Description:

The generated Swagger contract now documents all 47 REST operations with use cases, consumers, role/ownership rules, request schemas, success examples, and applicable errors. The published OpenAPI snapshot and GraphQL SDL are checked against runtime output. GraphQL documentation includes an executable query for jobs within 5 km and explicitly identifies unsupported operations and current limitations.

REST controllers, security, and rate limiting now share a consistent error body with stable codes. Validated request DTOs and shared job validation reject malformed fields, invalid statuses, salary bounds, and geographic parameters with client errors. Resume handling validates file constraints, returns 413 for oversized uploads and 404 for missing files, supports authenticated binary downloads, and removes newly written files when metadata persistence fails. User password updates accept validated input without exposing passwords. Rejected job updates no longer modify cached data.

Manual verification and documentation maintenance instructions: `docs/api-contracts.md`. GraphQL examples: `docs/graphql-examples.md`. Validation uses isolated H2 databases and temporary files; Docker/PostgreSQL manual verification remains a separate reviewer step.

Validation: `mvn -o -f backend/pom.xml test` — 67 tests passed, zero failures/errors/skips, including contract snapshot comparison without regeneration and HTTP CORS/rate-limit coverage.

Closes #6
Closes #7
Closes #8
Closes #9
Closes #10
Closes #11
Closes #16
Closes #30
Closes #31

## PR 4

Title: **Unify database migrations and add reproducible bulk demo data**

Branch: fix/database-schema-and-demo-data

Description:

Flyway now provides one maintained schema for local and Docker startup, and Hibernate validates it without dropping data. The migration includes every current entity field, foreign keys, uniqueness checks, and relevant value constraints. Parent deletion is rejected while dependents exist, with documented REST and GraphQL errors. An ER diagram and complete data dictionary describe resource IDs, relationships, defaults, nullability, and current enforcement limits.

The replacement seeder supports explicit off/demo/bulk modes, configurable counts, fixed random/time settings, applications, and downloadable synthetic PDF resumes. A transactional seed ledger uses actual persisted IDs, preserves existing records/passwords, repairs missing records/files, and serializes concurrent runs. Default bulk data includes 1,000 jobs, 100 companies, 500 candidates, 2,000 applications, 500 slots, and 500 resumes, with fixtures on both sides of a known 5 km boundary.

Compose persists database and resume data and supports isolated projects without fixed container names. Existing nonempty databases without Flyway history require explicit handling; they are not silently baselined or overwritten. Setup, reset, credentials, and manual checks are documented in docs/database-and-demo-data.md.

Validation: 77 tests passed in the full suite, including contract snapshot consistency and migrated H2 schemas. All 10 schema/seed tests also passed against an isolated PostgreSQL 15 container. The bulk Compose configuration validates. The temporary PostgreSQL container was removed after verification; the user's existing containers and volumes were not modified.

Closes #12
Closes #13
Closes #14
Closes #15

## PR 5

Title: **Align job writes and fix search, pagination, and geographic discovery**

Branch: `fix/job-discovery-and-pagination`

Description:

REST and GraphQL now accept the complete editable job fields and share validation and update behavior. Updates preserve omitted or null fields. Numeric salary bounds determine the display text, with a data migration to update existing numeric salaries while preserving legacy-only text. Salary units and filter behavior are documented as whole USD annual base salary.

Job searches combine text, status, category, and salary filters. Cursor pagination supports all five sort fields in both directions, with deterministic ID tie-breakers and nulls last. Cursors validate their resource, filters, and ordering and continue after deletion of the boundary record. GraphQL company pagination now advances correctly.

Geographic discovery includes the 5 km boundary, poles, and dateline crossings. REST GeoJSON and GraphQL queries share the same distance rules. Updated Swagger, GraphQL schema, and examples describe supported fields, salary semantics, and geographic behavior.

Compatibility changes: GET /api/v1/jobs always returns a page envelope, including text searches. Existing base64-ID cursors must be replaced by restarting pagination. Invalid sort/order values and blank search terms now return validation errors. REST PUT preserves omitted/null fields and does not support clearing fields with null. Deprecated title/location search endpoints retain their array responses.

Validation: `mvn -o -f backend/pom.xml test` passed all 97 tests with no failures, errors, or skips, including contract snapshot comparison without regeneration. The 19 discovery integration cases and the additional salary migration test also passed against isolated PostgreSQL 15. Surefire reported a delayed fork shutdown after the full suite; Maven returned BUILD SUCCESS. Manual requests and expected behavior are in `docs/job-discovery.md`.

Closes #17
Closes #18
Closes #19
Closes #20
Closes #21
Closes #22
Closes #29

## PR 6

Title: **Normalize UTC timestamps and enforce scheduling and application workflows**

Branch: fix/utc-scheduling-and-application-lifecycle

Description:

REST and GraphQL normalize timestamp offsets to UTC and return timestamps with Z. Offset-free inputs remain supported with an explicit UTC interpretation. Server-generated timestamps use UTC independently of the JVM timezone. Existing timestamp columns and historical values are preserved and interpreted as UTC.

Interview-slot creation validates references and locks the parent job row before checking overlap, preventing simultaneous requests from creating conflicting reservations. Assigned and unassigned reservations both block their interval. Calendar windows use half-open boundaries, allow adjacent slots, and reject incomplete or invalid bounds. Job time metadata remains independent of interview slots.

Application submission requires an OPEN job, assigns status/time in the service, and serializes duplicate checks. Status changes follow SUBMITTED → REVIEWED → SHORTLISTED → HIRED, with rejection allowed from each nonterminal state. Repeating a status is a no-op; invalid transitions return 409 APPLICATION_CONFLICT. Application row locks protect concurrent reviews. Email notifications start only after successful commit and remain best-effort.

Swagger, GraphQL documentation, the data dictionary, and manual examples describe timestamp compatibility, slot reservation/deletion rules, transition permissions, and notification behavior. The status-update request remains a raw JSON enum string. Historical timezone correction and durable email delivery are not included.

Validation: 108 tests passed with zero failures, errors, or skips under Asia/Kolkata, including contract snapshot comparison without regeneration. All 11 scheduling/application integration tests also passed against isolated PostgreSQL 15 under America/New_York, covering offset normalization, concurrent slot creation, duplicate applications, competing terminal reviews, and rollback notifications. Maven reported a delayed fork shutdown in the full suite but returned BUILD SUCCESS. Manual checks: docs/scheduling-and-applications.md.

Closes #23
Closes #24
Closes #28

## PR 7

Title: **Add API regression CI and an executable role-based walkthrough**

Branch: ci/api-regression-and-walkthrough

Description:

Add GitHub Actions checks for pull requests and main pushes. CI builds with Java 21, runs the full H2 suite, and runs five regression suites against isolated PostgreSQL 15 databases. The PostgreSQL setup and runner are also available locally through compose.test.yml and scripts/test-postgres.sh. API contract tests now check every internal OpenAPI reference, request examples, and runtime GraphQL field/argument/input types as well as existing route and snapshot checks.

A separate CI job builds the Docker Compose demo stack and executes scripts/api-walkthrough.py. The walkthrough registers synthetic COMPANY/CANDIDATE accounts, creates profiles and a job, performs administrative approval, verifies search/pagination and the 5 km GraphQL query, submits/reviews an application, schedules an interview, and checks representative errors. Tokens and IDs come from responses. Checked-in GraphQL operation files are executed directly, and shared REST/GraphQL results are compared. The walkthrough respects rate limits and does not print tokens or passwords.

README and ADR now describe implemented test commands, supported filters, role requirements, and equivalent REST/GraphQL calendar examples. Remove the nonexistent integration-test profile, unsupported job_id calendar filter, stale error codes, and unmeasured payload/SQL-count claims. CI uploads reports/logs and removes its disposable demo stack.

Validation: 109 full-suite tests passed with Maven verify under Asia/Kolkata; all 67 selected PostgreSQL tests passed under America/New_York. The Java 21 Docker build and all 36 walkthrough HTTP checks plus response assertions passed. actionlint 1.7.7 reported no workflow errors. Maven returned BUILD SUCCESS despite delayed Surefire fork shutdown warnings. GitHub-hosted execution remains to be verified after pushing. Local commands and expected behavior are in docs/testing.md and docs/api-walkthrough.md.

Closes #32
Closes #33
