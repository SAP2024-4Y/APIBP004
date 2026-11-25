# GraphQL examples

Open `http://localhost:8080/graphql` for the IDE. Send JSON requests containing query and optional variables to `http://localhost:8080/graphql/query`. Supply `Authorization: Bearer <token>` in HTTP headers for private fields and mutations. Discovery queries and login are public. All transport requests share the HTTP rate limit described in [API contracts](api-contracts.md).

## Find jobs within 5 km of a house

Replace the coordinates with the house location. The seeded demo includes jobs near these San Francisco coordinates. Results depend on available geocoded jobs.

~~~graphql
query NearbyJobs($latitude: Float!, $longitude: Float!) {
  jobsNear(latitude: $latitude, longitude: $longitude, radiusKm: 5) {
    id
    title
    location
    latitude
    longitude
    company { id companyName }
  }
}
~~~

Variables:

~~~json
{"latitude":37.7749,"longitude":-122.4194}
~~~

The response is `{"data":{"jobsNear":[...]}}`; no matches returns an empty list. Latitude must be -90–90, longitude -180–180, and radius positive. This local search does not yet guarantee correct polar or dateline behavior.

## Browse and inspect a job

~~~graphql
query Jobs {
  jobs(first: 10, status: "OPEN") {
    edges { cursor node { id title minSalary maxSalary company { companyName } } }
    pageInfo { hasNextPage endCursor }
  }
}
~~~

Pass the preceding endCursor as after to continue job discovery. first must be 1–100. Job results currently use ascending ID order; sort and order arguments are ignored. The companies query currently ignores after, so it cannot page beyond its initial results.

~~~graphql
query JobDetails($id: ID!) {
  job(id: $id) { id title description company { companyName } }
}
~~~

Use a real ID as `{"id":"1"}`. An unknown job returns an execution error with RESOURCE_NOT_FOUND. Unknown company IDs currently return null.

## Authenticate and create a job

~~~graphql
mutation Login($username: String!, $password: String!) {
  login(username: $username, password: $password) { token email role }
}
~~~

username accepts an email, or demo for the seeded demo account. Use the returned token in the Authorization header. A COMPANY user needs an existing company profile created through REST before publishing a job.

~~~graphql
mutation CreateJob($companyId: Int!) {
  createJob(input: {
    companyId: $companyId
    title: "Backend Engineer"
    description: "Build and maintain the recruitment platform APIs."
    status: "OPEN"
    employmentType: "Full-time"
    minSalary: 90000
    maxSalary: 130000
    latitude: 37.7749
    longitude: -122.4194
  }) { id title company { companyName } }
}
~~~

Only the owning COMPANY or ADMIN may create/update/delete jobs. Title requires 3–255 characters, description at least 20; salaries must be nonnegative and ordered. Status is OPEN, CLOSED, or DRAFT. Coordinates must be supplied together. startsAt/endsAt job inputs are currently ignored; create an interview slot separately.

## Schedule and read private fields

~~~graphql
mutation Book($jobId: Int!) {
  createSlot(input: {
    jobId: $jobId
    startsAt: "2027-01-10T10:00:00"
    endsAt: "2027-01-10T10:30:00"
  }) { id jobId candidateId startsAt endsAt status }
}
~~~

A CANDIDATE books for their own profile. A COMPANY schedules only its jobs; ADMIN may schedule any job. Company/admin callers may supply an existing candidateId or omit it for an unassigned slot. Dates are local date-times; timezone conversion is not implemented. Same-job overlap returns SLOT_CONFLICT. No application prerequisite is currently enforced.

Only the job-owning COMPANY or ADMIN can select `job { applications { id status appliedAt } }`. Unauthorized nested-field access can return partial public job data and a FORBIDDEN error. Slot candidateId is null for callers other than the assigned candidate, owning company, and ADMIN.

## Errors and supported operations

GraphQL execution normally returns HTTP 200 even if the response contains errors. Inspect errors and data; non-null field failures may propagate null to the operation result.

~~~json
{
  "data": {"job": {"id":"1","applications":null}},
  "errors": [{
    "message":"You don't have permission to access this resource",
    "path":["job","applications"],
    "extensions":{"code":"FORBIDDEN"}
  }]
}
~~~

The example omits source locations, which depend on query formatting. Application codes include VALIDATION_ERROR, UNAUTHORIZED, FORBIDDEN, RESOURCE_NOT_FOUND, DUPLICATE_RESOURCE, SLOT_CONFLICT, and INTERNAL_ERROR. Syntax/schema validation errors retain GraphQL's native format and do not necessarily have an application code. HTTP 429 is a transport error with the shared REST error body and Retry-After header.

Queries: jobs, job, companies, company, jobsNear, jobsInBox, slots. Mutations: login, createJob, updateJob, deleteJob, createSlot. There are no implemented myApplications, applyForJob, or cancelSlot operations; use the corresponding REST application and slot deletion endpoints. The complete supported types and inputs are in [schema.graphql](schema.graphql).
