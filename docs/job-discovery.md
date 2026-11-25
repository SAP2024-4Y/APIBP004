# Job discovery, pagination, and salaries

This change covers issues #17–#22 and #29. REST and GraphQL share job field mapping, validation, filters, cursor pagination, and geographic searches. It builds on the existing authorization and Flyway migrations.

## Compatibility changes

- GET /api/v1/jobs **always returns a page envelope**, including title/location/skills searches. Read the data property instead of treating the response as an array.
- GET /api/v1/jobs/search/title and /search/location retain deprecated bare-array responses. Migrate clients to the paginated endpoint; /jobs/company/{companyId} also remains an unpaginated array.
- Old base64-ID cursors are rejected. Restart from page one and use returned cursors. Copy the same filters, sort, and order when continuing.
- Unknown sort/order values, blank search terms, invalid cursors, and page sizes outside 1–100 now return a validation error rather than silently falling back.
- REST PUT now matches GraphQL updateJob: **only non-null supplied fields change**. Omitted and explicit null fields preserve existing values. Null cannot clear fields; an empty object is a no-op.
- GraphQL jobs now honors sort/order, defaults to created_at desc, and accepts title/location/skills filters. GraphQL companies now advances after its cursor, sorted by ID ascending. REST /companies remains an unpaginated array and does not advertise pagination parameters.

## Supported job fields

Creation accepts companyId, title, description, skillsRequired, salaryRange, location, employmentType, category, status, minSalary, maxSalary, latitude, longitude, startsAt, and endsAt through REST and GraphQL. Company ownership/existence is checked first. Status defaults OPEN. ID and createdAt are server-managed.

Updates support the same editable fields except company ownership cannot change. REST permits the unchanged companyId as a compatibility input; GraphQL UpdateJobInput omits it. Final values are validated together, so updating one salary/time/coordinate bound must remain valid with the preserved other bound.

Title requires 3–255 nonblank characters, description at least 20 nonblank characters. Status is OPEN, CLOSED, or DRAFT. Both coordinates must be present together on creation and be finite/in range. Supply both job times or neither; end must follow start. Time fields are ISO local date-times, and an optional trailing Z is accepted without offset conversion. Other timezone offsets are rejected pending the UTC work. Job times persist independently of InterviewSlot records.

Example REST creation, using the owning COMPANY token and actual company ID:

~~~json
{
  "companyId": 1,
  "title": "Backend Engineer",
  "description": "Build and maintain the recruitment platform APIs.",
  "skillsRequired": "Java, PostgreSQL",
  "category": "Technology",
  "status": "OPEN",
  "location": "San Francisco",
  "employmentType": "Full-time",
  "minSalary": 90000,
  "maxSalary": 130000,
  "latitude": 37.7749,
  "longitude": -122.4194,
  "startsAt": "2027-02-01T09:00:00",
  "endsAt": "2027-02-01T10:00:00"
}
~~~

POST /api/v1/jobs returns 201. Read the returned ID through GET /api/v1/jobs/{id}, search by title/category/salary, and query the map to verify persistence. Use the same fields in GraphQL CreateJobInput; IDs come from the response rather than assumptions about sequences.

## Salary policy

The v1 dataset uses a single currency and period: **ISO 4217 USD, annual base salary, whole dollars**. This applies regardless of job location. Values are not cents or monthly/hourly rates. No currency conversion is performed, and there is no per-job currency field. Consumers must convert externally for display if needed.

minSalary and maxSalary are inclusive, nonnegative integer bounds. Zero is valid. Either may be unknown (null); if both are known, minimum cannot exceed maximum.

- min_price / minPrice requires job.minSalary >= the supplied minimum.
- max_price / maxPrice requires job.maxSalary <= the supplied maximum.
- If a compared job bound is unknown, the job does not match that filter. With neither salary filter, unpriced jobs remain eligible.
- Both filters select ranges contained within the requested bounds, **not overlapping ranges**.

Numeric bounds are authoritative. On writes, salaryRange becomes "90000 - 130000" when both exist, "90000+" for a known minimum only, or "Up to 130000" for a known maximum only. Supplied legacy text is overridden whenever numeric bounds exist. With neither numeric bound, legacy-only text is retained for display and is not parsed or searched numerically. V2 backfills these display strings for existing priced jobs, preserving legacy-only text. There is no implicit conversion of historical salary values into another currency/period.

## Combined filters and ordering

All supplied filters combine with **AND**. Status/category are exact matches after trimming. Title/location/skills are case-insensitive literal substrings; percent and underscore are ordinary search characters, not SQL wildcards. Blank or over-255-character filter text is rejected. Skills is one substring over the stored skillsRequired text, not a tokenized skill expression.

~~~sh
curl 'http://localhost:8080/api/v1/jobs?status=OPEN&category=Technology&title=Engineer&location=Francisco&skills=Java&min_price=80000&max_price=150000&sort=min_price&order=asc&limit=5'
~~~

Supported sort fields are id, title, created_at, min_price, and max_price. Aliases createdAt, minSalary/min_salary, and maxSalary/max_salary remain accepted; matching is case-insensitive. Defaults are created_at desc. Title ordering follows database collation. **Nulls are last in both directions**, and equal values use job ID in the same direction as the primary sort.

The response has data, has_more, next_cursor, and limit. next_cursor is null when no further results exist, including empty results. No total-count query is implied.

~~~json
{"data":[],"has_more":false,"next_cursor":null,"limit":20}
~~~

Cursors encode a version, resource, sort/direction, filter fingerprint, sort value, and unique ID. They are opaque positions, not credentials or authorization tokens. A cursor remains usable if its boundary record was deleted. Different resources, filters, or ordering reject the cursor; page size may change. Keep every original filter in subsequent requests, including salary bounds and text filters.

This is keyset traversal over live data, not a snapshot. With unchanged data, all supported sorts traverse without duplicates or gaps, including ties/nulls. Concurrent inserts, updates that move sort positions, and deletions can change the result set; use a database snapshot/export process for immutable reporting.

GraphQL edge cursors use the same job pagination logic. PageInfo startCursor/endCursor identify the current nonempty page, including its final page; both are null on an empty page. hasPreviousPage checks whether matching rows exist at/before the supplied cursor. An empty page beyond the end may therefore have hasPreviousPage=true. hasNextPage is based on an extra fetched row.

## Geographic semantics

Clients supply their home coordinates in decimal degrees, obtained from their own location picker/geocoder or permissioned device location. Candidate.location is text; the API does not geocode addresses or infer a home coordinate.

Distance is straight-line spherical Haversine distance in kilometres (Earth radius 6371 km), **not road distance or travel time**. Radius is positive and finite. Boundaries are inclusive, with a 0.1 mm floating-point tolerance. Radius queries support poles/dateline crossing and clamp numerical rounding in the distance calculation.

For REST map requests, supply all latitude/longitude/radius_km fields OR all min_lat/min_lon/max_lat/max_lon fields. Mixed/incomplete modes return 400. Omit all geographic filters to list every geocoded job. GraphQL provides separate required-argument jobsNear and jobsInBox operations.

Bounding-box latitude bounds must be ordered; min_lon > max_lon means crossing the dateline. +/-180 represents the same meridian. Equal bounds are allowed, and boundaries are inclusive. Latitude is -90..90, longitude -180..180.

Results are unpaginated with no result-count cap, ordered by job ID. Jobs with null coordinates are excluded; OPEN/CLOSED/DRAFT and remote-labelled jobs are all included when they have coordinates. No matches returns an empty array in GraphQL or an empty GeoJSON FeatureCollection in REST. GeoJSON coordinate order is **[longitude, latitude]**.

## Reproducible 5 km example

The persisted fixture in JobDiscoveryIntegrationTest creates one company and these four jobs, then executes the full query below. It also inserts a job without coordinates to verify its exclusion. Existing demo/bulk data may contain additional matches, so an exact four-job fixture must use an isolated test database.

| Job | Latitude | Longitude | Distance north of home | Expected at 5 km |
| --- | --- | --- | ---: | --- |
| At home | 37.7749 | -122.4194 | 0 km | Included |
| Inside radius | 37.7749 + degrees(4.9 / 6371) | -122.4194 | 4.9 km | Included |
| On radius | 37.7749 + degrees(5 / 6371) | -122.4194 | 5 km | Included |
| Outside radius | 37.7749 + degrees(5.1 / 6371) | -122.4194 | 5.1 km | Excluded |

~~~graphql
query NearbyJobs($latitude: Float!, $longitude: Float!) {
  jobsNear(latitude: $latitude, longitude: $longitude, radiusKm: 5) {
    id
    title
    company { id companyName }
  }
}
~~~

Variables:

~~~json
{"latitude":37.7749,"longitude":-122.4194}
~~~

Full expected response shape and values, illustrated with company ID 201 and job IDs 101–104 (actual IDs are returned by fixture creation):

~~~json
{
  "data": {
    "jobsNear": [
      {"id":"101","title":"At home","company":{"id":"201","companyName":"Discovery Company"}},
      {"id":"102","title":"Inside radius","company":{"id":"201","companyName":"Discovery Company"}},
      {"id":"103","title":"On radius","company":{"id":"201","companyName":"Discovery Company"}}
    ]
  }
}
~~~

The test compares the **complete response data**, using actual persisted IDs. The same fixture queried with latitude=0 and longitude=0 returns:

~~~json
{"data":{"jobsNear":[]}}
~~~

Equivalent REST request:

~~~sh
curl 'http://localhost:8080/api/v1/jobs/map?latitude=37.7749&longitude=-122.4194&radius_km=5'
~~~

Expect three features for that isolated fixture, with coordinates [longitude, latitude]. A no-match map response is:

~~~json
{"type":"FeatureCollection","features":[]}
~~~

Run the focused persisted-fixture check without Docker:

~~~sh
mvn -o -f backend/pom.xml -Dtest=JobDiscoveryIntegrationTest#fiveKmBoundaryFixturesProduceFullGraphqlAndGeoJsonResults test
~~~

## Manual verification before merging

1. Start the branch with docker compose up --build. Flyway applies V2 without resetting data. On an existing bulk/demo database, numeric salary display strings should be normalized and legacy-only strings preserved.
2. Create a job with the full example body under an owned company. Read it, search it with combined filters, and find it using the 5 km map/GraphQL query.
3. Update only its title or longitude. Check that omitted description, salary, and other coordinate/time values are preserved. Invalid final salary/time pairs return 400 without changing persisted/cached state.
4. Page through jobs at limit=2 for each supported sort in asc and desc. Reuse the entire filter set and returned cursor; IDs should not repeat on unchanged data. Repeat in GraphQL.
5. Change one filter or sort while reusing the cursor; expect REST 400 VALIDATION_ERROR / GraphQL errors[].extensions.code=VALIDATION_ERROR. Old MQ== style cursors also fail.
6. Page through GraphQL companies(first:2,after:...) until hasNextPage=false; verify start/end cursors and empty-page metadata.
7. Confirm deprecated REST search routes still return arrays, while /jobs?title=... always returns an envelope.
8. Check dateline search around latitude=0, longitude=180 and polar search with suitable geocoded fixtures; see automated tests for exact cases.

## Automated verification

Run mvn -o -f backend/pom.xml test. JobDiscoveryIntegrationTest exercises both transports with persisted fixtures, all five sort fields/directions, tied/null values, filters, cursor errors, company traversal, field round trips, salary rules, and radius/box edge cases.

The same tests accept discovery.test.jdbc-url, discovery.test.driver, discovery.test.username, discovery.test.password, and discovery.test.dialect system properties for a dedicated disposable PostgreSQL database. Tests delete fixture rows; never point them at a database you want to keep. The default suite uses isolated migrated H2 databases.
