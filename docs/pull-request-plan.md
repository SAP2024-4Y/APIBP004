# Implementation plan

Related issues are grouped into seven pull requests, including the merged registration fix. Issue numbers below are the GitHub issue numbers for SAP2024-4Y/APIBP004. Each implementation PR includes its relevant documentation and regression tests.

| PR | Scope | GitHub issues |
| --- | --- | --- |
| 1 | Restrict public registration roles — merged as PR #34 | #27 |
| 2 | Shared REST and GraphQL authorization, resource ownership, and private fields — merged as PR #35 | #25, #26 |
| 3 | API contracts: Swagger/OpenAPI and GraphQL schema consistency, descriptions, examples, validation, errors, resume handling, and rate-limit documentation | #6, #7, #8, #9, #10, #11, #16, #30, #31 |
| 4 | Database schema, entity documentation, valid seed relationships, and bulk data | #12, #13, #14, #15 |
| 5 | Job fields, salary semantics, filtering, pagination, and geographic search | #17, #18, #19, #20, #21, #22, #29 |
| 6 | UTC timestamps, scheduling rules, and application lifecycle | #23, #24, #28 |
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
