# API authorization

REST and GraphQL use the same resource-ownership policy, implemented by `ResourceAuthorization`. Send an access token as `Authorization: Bearer <token>`. A role grants access only within the ownership rules below. User IDs, candidate IDs, and company IDs identify separate records and are not interchangeable.

## Access matrix

| Resource or operation | Anonymous | CANDIDATE | COMPANY | ADMIN |
| --- | --- | --- | --- | --- |
| Job discovery, job details, map search | Read | Read | Read | Read |
| Company list/details | Read | Read | Read | Read |
| Company lookup by user ID | Denied | Own user ID | Own user ID | Any |
| Create company/candidate profile | Denied | Own candidate profile | Own company profile | For a user with the matching role |
| Update/delete company profile | Denied | Denied | Own company | Any company |
| Candidate profile details | Denied | Own profile | Applicants to own jobs | Any profile |
| Candidate lookup by user ID | Denied | Own user ID | Own user ID | Any |
| Update/delete candidate profile | Denied | Own profile | Denied | Any profile |
| List all candidates | Denied | Denied | Denied | Allowed |
| Create/update/delete job | Denied | Denied | Own company/jobs | Any company/job |
| List all applications | Denied | Denied | Denied | Allowed |
| Read an application | Denied | Own application | Applications to own jobs | Any application |
| Applications by candidate | Denied | Own candidate profile | Denied | Any candidate |
| Applications by job / GraphQL Job.applications | Denied | Denied | Own jobs | Any job |
| Submit application | Denied | Own candidate profile | Denied | Denied |
| Update application status | Denied | Denied | Own jobs | Any application |
| Delete application | Denied | Own application | Denied | Any application |
| Read/download resume | Denied | Own resumes | Applicants to own jobs | Any resume |
| Upload/delete resume | Denied | Own resumes | Denied | Any resume |
| List all resumes | Denied | Denied | Denied | Allowed |
| Read interview slots | Public fields | Candidate identity for own bookings | Candidate identity for own jobs | All fields |
| Create interview slot | Denied | Booking for self | Own jobs | Any job |
| Delete interview slot | Denied | Own assigned booking | Own jobs | Any slot |
| Read/update user account | Denied | Own account; role unchanged | Own account; role unchanged | Any account; may change role |
| List/create/delete users | Denied | Denied | Denied | Allowed |

The user-ID lookup routes still require that the relevant profile exists; authorization alone does not create a profile. A company can read candidate profiles and resumes only when an application connects the candidate to a job belonging to that company. It cannot modify the candidate's profile or files.

## Ownership and creation rules

- Profile creation derives `userId` from the authenticated caller when omitted. A non-admin cannot supply another user's ID. ADMIN must target a user with the corresponding CANDIDATE or COMPANY role.
- Profile creation rejects an existing resource `id`. Company creation forces `approved: false` for COMPANY callers; only ADMIN may set approval.
- Company, candidate, and job updates cannot reassign their owner. Ownership is checked against persisted records before modifying an entity.
- Application submission derives `candidateId` from the caller's candidate profile and rejects a different supplied candidate ID. It rejects a supplied application ID, assigns `SUBMITTED`, and sets the submission time on the server.
- Candidates booking a slot may omit `candidateId` or provide their own profile ID. Companies can schedule only for their own jobs. Referenced jobs and candidates must exist.
- User self-service cannot change roles. Existing ADMIN-only user-management operations remain available.

## GraphQL behavior

Execute requests at `POST /graphql/query`; the Playground is at `/graphql`. Login and public discovery remain public. Job mutations require the owning COMPANY or ADMIN. Slot mutations follow the same ownership rules as REST.

The HTTP request's authenticated identity is captured in an immutable GraphQL context. Nested fields retain that identity when DataLoader resolves company/job relationships asynchronously. Protected fields do not depend on a worker thread's security context.

`Job.applications` requires the job's owning COMPANY or ADMIN. An unauthorized caller receives an error for that field; other permitted fields can still appear in the response. `InterviewSlot.candidateId` is returned as null to callers who are not the assigned candidate, the owning company, or ADMIN. REST slot responses apply the same redaction without modifying stored slot records.

Protected GraphQL operations require the bearer-authenticated HTTP execution path. Contexts without an authenticated request identity are treated as anonymous.

## Error behavior

REST returns HTTP 401 when authentication is required and HTTP 403 when an authenticated caller lacks the required role or ownership. Resource lookup can return 404 when a referenced record does not exist. For company/job ownership checks, a non-owner may receive 403 for an inaccessible or nonexistent ID.

GraphQL resolver authorization failures use `errors[].extensions.code`:

| Code | Meaning |
| --- | --- |
| `UNAUTHORIZED` | The operation or field requires an authenticated request. |
| `FORBIDDEN` | The caller lacks the required role or resource ownership. |

Resolver errors return HTTP 200 with the GraphQL error envelope. GraphQL nullability determines whether the failed field, a parent field, or the entire `data` value becomes null. Transport errors such as rate limiting can use a non-200 HTTP response.

## Manual verification

1. Build and start the current branch with `docker compose up --build`. Compose uses Flyway and preserves data across restarts. For an existing unversioned database, follow the [database review setup](database-and-demo-data.md).
2. Open `/docs` and obtain two COMPANY tokens through `/api/v1/auth/login`: `hr@techcorp.com` and `careers@innovatelab.com`, both with password `password123`. Record each login response's `userId`.
3. For each account, find its company through `/api/v1/companies/user/{userId}`, then its jobs through `/api/v1/jobs/company/{companyId}`. Use IDs returned by the API.
4. With the first company's token, update one of its jobs using a valid title and description: expect 200. Submit the same update against the second company's job: expect 403 and unchanged stored data.
5. In GraphQL Playground, set the Authorization header to the first company's token and run the query below with each job ID. Its own job's applications should be returned; the other job's applications should be null with `FORBIDDEN`.
6. Remove the Authorization header. Job title remains public, while applications produce `UNAUTHORIZED`.
7. Log in as candidate `john.doe@email.com` with password `password123`. Find the candidate profile using the login's `userId`; read that profile, then attempt to access another candidate's profile: expect 200 and 403 respectively.
8. Attempt to change that candidate user's role to ADMIN through `PUT /api/v1/users/{userId}`: expect 403 and an unchanged role.
9. Inspect `/api/v1/slots` anonymously: `candidateId` must be null. Create a new booking as a candidate with `candidateId` omitted, then read it as that candidate: the caller's actual candidate-profile ID should be visible.

```graphql
query JobApplications($id: ID!) {
  job(id: $id) {
    id
    title
    applications {
      id
      status
    }
  }
}
```

For mutation checks, use a known existing job ID and a valid update:

```graphql
mutation UpdateJob($id: ID!, $input: UpdateJobInput!) {
  updateJob(id: $id, input: $input) {
    id
    title
  }
}
```

An anonymous mutation must return `UNAUTHORIZED`; a different company or candidate must receive `FORBIDDEN`; the owning company and ADMIN may update the job.

## Automated verification

Run `mvn -f backend/pom.xml test`. Ownership integration tests start an HTTP server with an isolated in-memory database, real JWT authentication, repositories, controllers, and GraphQL execution. Seed startup, email delivery, and rate limiting are isolated from this suite so unrelated demo data or quotas do not affect the permission checks.
