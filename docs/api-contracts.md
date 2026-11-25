# API contracts and verification

Swagger UI is available at `http://localhost:8080/docs`. Its source is the generated REST group at `/v3/api-docs/v1`, covering all 47 application operations. [openapi.yaml](openapi.yaml) is an exported snapshot of that document. Controller signatures and validated DTOs define the transport contract; `ApiContractCustomizer` and `backend/src/main/resources/openapi/operations.json` supply business descriptions, examples, and schemas for map/envelope responses.

The executable GraphQL schema is `backend/src/main/resources/graphql/schema.graphqls`. [schema.graphql](schema.graphql) is its exact published copy. The IDE is `/graphql`; POST GraphQL requests to **`/graphql/query`**. See [GraphQL examples](graphql-examples.md) and the [authorization matrix](authorization.md).

## Start and inspect

From the repository root, run:

~~~sh
docker compose up --build
~~~

Compose uses Flyway migrations and Hibernate validation; restarts preserve data. See [database setup](database-and-demo-data.md) before switching an existing unversioned database to this branch. The automated tests below use isolated H2 databases and temporary upload directories; they do not connect to the compose database.

Open `/docs`, expand an operation, and inspect its use case, consumers, roles/ownership, workflow, request schema, response examples, and applicable errors. Authentication is optional for public discovery. Use **Authorize** and enter a JWT for protected operations.

## Quick-start workflow

Use the returned IDs from your environment; a user ID, company ID, candidate ID, application ID, and resume ID identify different records.

1. **Authenticate or register.** `POST /api/v1/auth/register` with `{"email":"candidate@example.com","password":"password123","role":"CANDIDATE"}` returns 201 and a token. Repeat with a separate email and COMPANY role for a recruiter. Existing users use `POST /api/v1/auth/login`. Only CANDIDATE and COMPANY may register publicly.
2. **Create profiles.** With the candidate token, `POST /api/v1/candidates` with `{"fullName":"Alex Morgan","phone":"9876543210","location":"San Francisco"}` returns 200. With the company token, `POST /api/v1/companies` with `{"companyName":"Example Technologies","industry":"Technology","address":"San Francisco"}` returns 201. Both infer userId from the token; only ADMIN can operate for another matching-role user.
3. **Publish and discover a job.** With the company token, `POST /api/v1/jobs` with `{"companyId":1,"title":"Backend Engineer","description":"Build and maintain the recruitment platform APIs.","employmentType":"Full-time","location":"San Francisco"}`, replacing companyId. Expect 201. `GET /api/v1/jobs` and `GET /api/v1/jobs/{id}` are public.
4. **Apply.** With the candidate token, `POST /api/v1/applications` with `{"jobId":1}`, replacing jobId. Expect 201, the caller’s candidateId, status SUBMITTED, and a server timestamp. A duplicate candidate/job pair returns 409.
5. **Review.** With the owning company token, `GET /api/v1/applications/job/{jobId}` lists applicants. `PUT /api/v1/applications/{id}/status` accepts a JSON string such as `"SHORTLISTED"`, not an object.
6. **Schedule.** With the candidate token, `POST /api/v1/slots` with `{"jobId":1,"startsAt":"2027-01-10T10:00:00","endsAt":"2027-01-10T10:30:00"}`, replacing jobId. Expect 201. Repeating the same interval returns 409 SLOT_CONFLICT. Public slot listing hides candidateId; the assigned candidate, job owner, and ADMIN may see it.

## Manual checks for this PR

| Check | Request/action | Expected result |
| --- | --- | --- |
| Complete Swagger | Open `/docs`; browse each resource group and health | Descriptions, role rules, inputs, success examples, and error examples |
| Deprecated routes | Inspect `/api/v1/jobs/search/` routes | Marked deprecated with replacement routes |
| Job page | `GET /api/v1/jobs?limit=2` | 200; data, has_more, nullable next_cursor, limit; no invented total_count |
| Empty results | `GET /api/v1/jobs?category=no-such-category` | 200; empty data, false has_more, null next_cursor |
| Bad input | `GET /api/v1/jobs?limit=0`, `?status=INVALID`, or `?min_price=200&max_price=100` | 400 VALIDATION_ERROR |
| Bad JSON/type | Authenticated slot creation with `jobId:"invalid"` | 400 INVALID_REQUEST |
| Bad dates | Authenticated slot creation with valid jobId and malformed date strings | 400 VALIDATION_ERROR |
| Missing resource | `GET /api/v1/jobs/999999` | 404 RESOURCE_NOT_FOUND if that ID does not exist |
| Missing authentication | `GET /api/v1/users` without a token | 401 UNAUTHORIZED |
| Wrong role | `GET /api/v1/users` with a candidate token | 403 FORBIDDEN |
| Registration policy | Register with role ADMIN or a password shorter than six characters | 400 VALIDATION_ERROR; no account created |
| Job validation | Owning-company job update with minSalary greater than maxSalary | 400; persisted job and cached GET response remain unchanged |
| Account password | Update own user with email and a new password, then log in | New password works; response never contains password |
| Geographic search | `GET /api/v1/jobs/map?latitude=37.7749&longitude=-122.4194&radius_km=5` | GeoJSON with [longitude, latitude] and the documented property subset |
| Incomplete map input | `GET /api/v1/jobs/map?latitude=37.7749` | 400; no silent fallback to every job |

For a resume upload, use an existing candidate profile and its owner’s token:

~~~sh
curl -i -X POST http://localhost:8080/api/v1/resumes/upload \
  -H "Authorization: Bearer $CANDIDATE_TOKEN" \
  -F "candidateId=$CANDIDATE_ID" \
  -F "file=@./sample.pdf"
~~~

Expect 201 with message, success, and resume. Use resume.id in `GET /api/v1/resumes/download/{id}` with the same token. The response contains the original bytes, `Content-Type: application/octet-stream`, and `Content-Disposition: attachment`. Deletion returns 200 with message and success; subsequent metadata lookup returns 404.

An empty file, extensionless filename, or unsupported extension returns 400. A file exceeding **5 MiB**, or a multipart request exceeding **6 MiB**, returns 413. Accepted filename extensions are PDF, DOC, and DOCX, case-insensitively; this validates extensions, not document contents. Configure storage through `app.resume.upload-dir` / `APP_RESUME_UPLOAD_DIR`. The default preserves the existing directory. filePath in metadata is an opaque reference, not a public download URL.

## Error and rate-limit contract

REST errors share this structure, including authentication and rate-limit errors raised before controller execution:

~~~json
{
  "timestamp": "2026-11-25T10:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "error": "Bad Request",
  "message": "Input validation failed for one or more fields",
  "path": "/api/v1/users",
  "details": ["email: must be a well-formed email address"]
}
~~~

Branch on code and HTTP status; message describes the specific failure and may vary. details is an array, empty when field-level details are unavailable. error is the HTTP reason phrase. Validation uses 400, not 422. Unexpected server failures return a sanitized 500 INTERNAL_ERROR. GraphQL execution errors use `errors[].extensions.code`, generally with HTTP 200; transport rate limiting returns REST-style HTTP 429.

The default limit is **60 HTTP requests per 60-second window per directly connected client IP**, including docs, health, REST, GraphQL, and failed authentication. CORS preflight requests are excluded. Configure `app.ratelimit.requests-per-minute` / `APP_RATELIMIT_REQUESTS_PER_MINUTE` to change it. Responses include RateLimit-Limit, RateLimit-Remaining, and RateLimit-Reset (UTC epoch seconds). HTTP 429 also includes Retry-After in seconds. Windows begin with the first request for that client and reset after 60 seconds. Quotas are in memory per API instance; clients behind the same proxy share its address. Untrusted X-Forwarded-For headers do not change the quota.

For a manual quota check, call `/health` repeatedly with `curl -i`, stop at 429, and retry after the reported delay. Swagger requests also count toward the quota.

## Validation and current limits

Dedicated REST request DTOs define required fields, length bounds, and enums. Password creation/update uses 6–72 characters; omitting an update password preserves it. IDs must be positive. Salaries must be nonnegative with minimum no greater than maximum. Job status is OPEN, CLOSED, or DRAFT. Employment type accepts FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP, FREELANCE and the existing Full-time, Part-time, Contract, Internship, Freelance spellings. Shared job service checks also apply to GraphQL mutations.

REST ignores unknown JSON fields. New profile/application IDs are explicitly rejected; server status/timestamps are assigned independently. GraphQL rejects unknown input fields. Relationship checks require existing users with the matching role for profiles, existing companies for job creation, existing jobs/candidates for applications, slots, and resumes. Ownership checks can return 403 before revealing whether a record exists. Database foreign keys enforce these relationships; deleting a parent with dependents returns 400 DATA_INTEGRITY_ERROR.

Job discovery now uses a consistent page envelope, combined filters, and sort-aware cursors shared with GraphQL. Job create/update persists numeric salaries, coordinates, category/status, and local job time bounds. See [job discovery and compatibility notes](job-discovery.md) for salary units, null-update behavior, cursors, and exact 5 km examples. Domain timestamps remain local date-time strings without offsets; scheduling/application lifecycle rules and UTC normalization remain separate planned changes.

## Automated checks and updating the published contracts

Run from the repository root:

~~~sh
mvn -o -f backend/pom.xml test
~~~

Contract tests compare controller routes with documented methods/paths, compare the served document with the YAML, validate response/example structure, and check Swagger’s configured URL. HTTP tests exercise validation, permissions, duplicate applications, slot conflicts, multipart limits, file round trips, password updates, and a 5 km GraphQL query. Separate tests check concurrent rate-limit enforcement and file cleanup after database failure. Existing ownership/registration tests remain included.

After an intentional Java/metadata contract change, regenerate the snapshot:

~~~sh
mvn -o -f backend/pom.xml -Dtest=ApiContractIntegrationTest \
  -Dcontracts.export=true test
~~~

Review `git diff -- docs/openapi.yaml`, then run tests **without** contracts.export to verify the snapshot is current. No running Docker stack is required. If Maven dependencies are not cached, omit `-o` on the first run.

After changing the executable GraphQL SDL, copy it to docs/schema.graphql and run the tests. Tests compare the exact files and verify query/mutation names against live introspection. Do not add a field to the published SDL unless the runtime implements it.
