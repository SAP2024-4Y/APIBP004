# Executable API walkthrough

The walkthrough creates synthetic COMPANY and CANDIDATE accounts and profiles, posts a job, searches and applies, reviews the application, books an interview, and checks administrative access. It captures every token and resource ID from responses. It also checks documented failures and compares equivalent REST/GraphQL views.

## Run

Use a disposable demo stack with Docker Compose v2 and Python 3.10+ (standard library only). From the repository root:

~~~sh
DB_PORT=55444 API_PORT=18080 docker compose -p jobportal-walkthrough up --build -d
python3 scripts/api-walkthrough.py --base-url http://localhost:18080 --wait-seconds 180
~~~

The script waits for /health and then prints PASS for each verified request. Any unexpected status, application error code, missing record, GraphQL error, or mismatched comparison exits nonzero. JWTs and passwords are kept in process memory and never printed. No pip install is required.

The demo ADMIN uses username demo and password demo-password. If that seeded account's password was changed, supply WALKTHROUGH_ADMIN_USERNAME and WALKTHROUGH_ADMIN_PASSWORD through environment variables. Public registration is used only for COMPANY and CANDIDATE; it cannot create an ADMIN. Demo seeding must be enabled and email delivery disabled, as in the default Compose configuration.

Each run creates unique synthetic emails and a random password. It deletes its application and slot after checking them, but leaves its job, profiles, and accounts for inspection. It does not delete pre-existing data. Re-running creates another set of demo records. To remove the entire disposable stack and its data after review:

~~~sh
docker compose -p jobportal-walkthrough down -v
~~~

This cleanup applies only to that named demo project. Do not use it to reset a database you want to keep.

## Requests and expected results

| Step | Request | Expected result |
| --- | --- | --- |
| Admin login | POST /api/v1/auth/token | 200; capture ADMIN token |
| Register and log in | POST /api/v1/auth/register, /login | 201 then 200 for each COMPANY/CANDIDATE; capture userId/token |
| Company profile | POST /api/v1/companies | 201; derived owner and approved=false |
| Candidate profile | POST /api/v1/candidates | 200; derived owner |
| Administrative approval | PUT /api/v1/companies/{id} | 200 with approved=true |
| Admin listing and denials | GET /api/v1/users | ADMIN 200, anonymous 401, candidate 403 |
| Publish job | POST /api/v1/jobs | Owning COMPANY 201; candidate 403 |
| Discover | GET /api/v1/jobs with combined filters and cursor pages | Created job is found; pages do not repeat |
| Within 5 km | GraphQL jobsNear and REST /jobs/map | Same job IDs; created job is included |
| Apply | POST /api/v1/applications with only jobId | 201, JWT-derived candidateId, SUBMITTED, UTC appliedAt |
| Duplicate | Repeat submission | 409 DUPLICATE_RESOURCE |
| Review | PUT /applications/{id}/status with raw JSON strings | REVIEWED then SHORTLISTED: 200; direct HIRED: 409; candidate review: 403 |
| Reserve | GraphQL createSlot | Candidate identity derived; offset input becomes UTC |
| Conflict | REST POST /slots for same interval | 409 SLOT_CONFLICT |
| Composite read | REST job/company/slots and GraphQL job + slots | Same shared fields and slot sets |
| Withdrawal/deletion | DELETE own slot and application | 204 |
| Invalid geography / missing job | GET map / job detail | 400 VALIDATION_ERROR / 404 RESOURCE_NOT_FOUND |

The script is the complete executable request sequence, including payloads, expected statuses, and response assertions. Test accounts use example.test addresses. The run ends with returned job/company/candidate IDs for inspection.

## Checked-in GraphQL operations

- examples/jobs-near.graphql — latitude 37.7749, longitude -122.4194, radiusKm 5.
- examples/create-slot.graphql — use the created jobId and 2030-01-10T15:30:00+05:30 through 16:30:00+05:30; expect 10:00–11:00Z.
- examples/job-and-calendar.graphql — use the returned job ID and that same UTC window.

The script reads these files directly, so CI executes the documented operations. REST and GraphQL both retrieve all slots in the window; filter by jobId on the client when needed. No unsupported job_id calendar parameter or private application fields are used for the comparison. No unmeasured byte savings are claimed.

The script respects the default rate limit. On 429 it waits according to Retry-After (up to 60 seconds) and retries the rejected request at most twice. It does not automatically retry network failures or other failed writes. Shared-IP activity may require waiting before another run.
