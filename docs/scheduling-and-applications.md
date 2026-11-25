# UTC timestamps, scheduling, and application lifecycle

This change covers #23, #24, and #28. Existing role and ownership rules continue to apply through REST and GraphQL.

## Timestamp policy

All API timestamp fields use UTC output with Z: User.createdAt, Job.createdAt/startsAt/endsAt, Application.appliedAt, Resume.uploadedAt, InterviewSlot.createdAt/startsAt/endsAt, and error timestamps. For example, 2027-01-10T15:30:00+05:30 and 2027-01-10T05:00:00-05:00 both represent 2027-01-10T10:00:00Z. Fractional seconds are supported. Date-only strings, malformed offsets, and named timezone suffixes are rejected.

Job/slot writes and REST/GraphQL calendar windows accept ISO 8601 timestamps with Z or an offset. Offset-free input is interpreted as UTC for compatibility, regardless of the server timezone. Clients should send an explicit offset. URL query parameters must encode a positive offset's + as %2B; use curl --data-urlencode. GraphQL and JSON strings need no URL encoding.

Storage remains TIMESTAMP without time zone / LocalDateTime with UTC semantics. Server-generated timestamps use an explicit UTC clock. SQL CURRENT_TIMESTAMP defaults depend on the database session timezone; external writers using those defaults must set their session timezone to UTC. Existing rows are interpreted as UTC without rewriting them. If historical rows were recorded in another timezone, an administrator must identify that timezone and perform a separate, reviewed correction; it cannot be inferred from these columns. External SQL writers must also use UTC. No new schema migration is needed.

## Calendar and slot rules

A slot is a SCHEDULED reservation for one job. Company/admin can create an unassigned reservation by omitting candidateId, or assign an existing candidate. A CANDIDATE always books their own profile. Both assigned and unassigned slots reserve their interval. This is not a separate availability-and-booking workflow: there is no update/assign endpoint. Delete and recreate to change a reservation.

Job and candidate references must exist. COMPANY can schedule only its own jobs, ADMIN any job. An application is not required, and the job's OPEN/CLOSED/DRAFT state does not gate scheduling. Job.startsAt/endsAt are independent metadata: they do not bound slots or synchronize with them.

Intervals are half-open [start, end). A 10:00–11:00 slot overlaps 10:30–11:30 but is adjacent to, and does not conflict with, 11:00–12:00. End must follow start after UTC normalization. Overlap is scoped to the same job, including unassigned reservations; different jobs may overlap even for the same candidate. There is no cross-job candidate calendar constraint.

Creation locks the parent job row in the database through commit, then checks conflicts and inserts. Concurrent service requests cannot create overlapping slots even when no slot exists yet. This applies across API instances sharing the database. Direct SQL bypasses the rule unless it follows the same locking protocol; no exclusion constraint was added.

REST GET /api/v1/slots and GraphQL slots return all overlapping reservations when both bounds are provided, or all reservations when neither is provided. One-sided, equal, reversed, or malformed windows fail with VALIDATION_ERROR. The overlap condition is slot.start < window.end AND slot.end > window.start. Results are unpaginated. Candidate identity is redacted except for the assigned candidate, owning company, and ADMIN.

DELETE /api/v1/slots/{id} physically deletes the slot and frees its interval. The assigned candidate, owning company, or ADMIN may delete it. No cancellation history, status transition endpoint, or email notification exists.

## Application workflow

POST /api/v1/applications accepts {"jobId": 123}. Only CANDIDATE may submit, and the candidate profile comes from the JWT. The optional legacy candidateId must match that profile. A supplied id is rejected with 400; unrecognized status/appliedAt input does not override service-assigned SUBMITTED status and current UTC submission time. Candidate and job must exist, and the job must be OPEN. Job time metadata does not determine eligibility.

The candidate/job pair is unique. Submissions lock the job row before checking duplicates, so simultaneous duplicate requests return one 201 and one 409 DUPLICATE_RESOURCE. The database unique constraint remains a final safeguard. CLOSED/DRAFT jobs return 409 APPLICATION_CONFLICT.

Only the owning COMPANY or ADMIN can update status using PUT /api/v1/applications/{id}/status. The request body remains a raw JSON string, such as "REVIEWED", rather than an object. Unknown enum values or object bodies return 400 INVALID_REQUEST.

| Current status | Allowed next status |
| --- | --- |
| SUBMITTED | REVIEWED, REJECTED |
| REVIEWED | SHORTLISTED, REJECTED |
| SHORTLISTED | HIRED, REJECTED |
| HIRED | None |
| REJECTED | None |

Repeating the current status returns 200 without changing data or sending another notification. Any other transition returns 409 APPLICATION_CONFLICT. Status changes lock the application row so concurrent reviewers cannot overwrite a committed terminal decision. Closing a job does not stop review of existing applications.

Submission emails and status-change emails start only after the database transaction commits. They are asynchronous and best-effort: an email failure does not undo the successful request, and delivery has no durable retry guarantee. Rollbacks and status no-ops send no notification.

The owning candidate or ADMIN can withdraw by DELETE at any status. This physically deletes the application; there is no withdrawal state/history or email. The candidate can reapply if the job is OPEN. GraphQL exposes authorized application reads through Job.applications; no application mutations are added.

## Manual checks

Start the current branch with docker compose up --build. Use existing company/candidate tokens and actual job/application IDs. These requests create test records; use a disposable demo database or delete them afterwards.

1. As the owning company, POST /api/v1/slots with:

~~~json
{"jobId":123,"startsAt":"2027-01-10T15:30:00+05:30","endsAt":"2027-01-10T16:30:00+05:30"}
~~~

Expect 201 with startsAt 2027-01-10T10:00:00Z, endsAt 2027-01-10T11:00:00Z, status SCHEDULED, and candidateId null. Repeat it: expect 409 SLOT_CONFLICT. An 11:00–12:00Z slot should succeed. Delete the original slot and its original interval can be created again.

2. Query the same interval using REST:

~~~sh
curl --get 'http://localhost:8080/api/v1/slots' \
  --data-urlencode 'starts_at=2027-01-10T15:30:00+05:30' \
  --data-urlencode 'ends_at=2027-01-10T16:30:00+05:30'
~~~

The 10:00–11:00Z slot is included; an adjacent 11:00–12:00Z slot is excluded. The equivalent GraphQL query is:

~~~graphql
query {
  slots(startsAt: "2027-01-10T15:30:00+05:30", endsAt: "2027-01-10T16:30:00+05:30") {
    id startsAt endsAt status candidateId
  }
}
~~~

3. As a candidate, submit {"jobId":123} to an OPEN job. Expect 201, your derived candidateId, SUBMITTED, and appliedAt with Z. Repeat it: expect 409 DUPLICATE_RESOURCE. Try a CLOSED job: expect 409 APPLICATION_CONFLICT.
4. As the owning company, update the application with raw JSON "HIRED": expect 409. Then send "REVIEWED", "SHORTLISTED", and "HIRED" sequentially: each returns 200. Repeat "HIRED": 200, no notification. Send "REJECTED" after hiring: 409. Candidate status updates return 403.
5. Run simultaneous identical slot/application requests against fresh fixtures: expect one 201 and one 409, with one persisted record. Automated tests exercise this and competing terminal reviews.
