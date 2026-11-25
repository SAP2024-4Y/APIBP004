# APIBP004 — Job Portal API

Course: API Based Products

## Team Details

| # | Name              | Roll No        | GitHub ID         | Email                              |
|---|-------------------|----------------|-------------------|------------------------------------|
| 1 | Atig Sinha        | 2024SL70017    | atig-sinha        | 2024sl70017@wilp.bits-pilani.ac.in |
| 2 | Ranvijay Singh    | 2024SL70026    | 2024sl70026-sudo  | 2024sl70026@wilp.bits-pilani.ac.in |
| 3 | Sourav Kumar      | 2024SL70036    | ImSourav03        | 2024sl70036@wilp.bits-pilani.ac.in |
| 4 | Simran Sahay      | 2024SL70038    | simransahay19     | 2024sl70038@wilp.bits-pilani.ac.in |

- Team ID: `APIBP004` (must equal the repository name and `team.json.team_id`)
- Chosen Approach: `A` — REST-First + GraphQL Layer

## Quick start

```bash
git clone https://github.com/SAP2024-4Y/APIBP004
cd APIBP004
docker compose up --build
# Server:        http://localhost:8080
# REST docs:     http://localhost:8080/docs
# GraphQL IDE:   http://localhost:8080/graphql
# Health check:  http://localhost:8080/health
```

Compose enables a **25-job demo dataset** with five companies, five candidates, ten applications, five interview slots, and five sample PDF resumes. Flyway applies the maintained schema; restarting preserves data. Bulk mode adds **1,000 jobs, 100 companies, 500 candidates, 2,000 applications, 500 slots, and 500 resumes**:

~~~sh
docker compose -f docker-compose.yml -f compose.bulk.yml up --build
~~~

See [database setup, existing-database handling, counts, and accounts](docs/database-and-demo-data.md) and the [entity diagram/data dictionary](docs/entities.md). Seeding defaults to off outside Compose; set APP_SEED_MODE=demo or bulk explicitly for local runs.

---

## Demo credentials

The evaluator account is pre-seeded automatically:

| Field    | Value           |
|----------|-----------------|
| username | `demo`          |
| password | `demo-password` |
| email    | `demo@jobportal.com` |
| role     | `ADMIN`         |

### Obtain a token

```bash
curl -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo-password"}'
# → {"token":"eyJ...","message":"Token issued successfully"}
```

Use the token in subsequent requests:
```bash
TOKEN="<paste token here>"
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/slots
```

---

## REST API examples

See [job discovery and compatibility notes](docs/job-discovery.md) for the consistent page envelope, supported fields, salary units, cursor rules, and a verified 5 km example. Numeric salaries are whole USD per year.

### List jobs with filtering and cursor pagination

```bash
# First page — open Technology jobs, sorted by salary
curl "http://localhost:8080/api/v1/jobs?status=OPEN&category=Technology&sort=minSalary&order=desc&limit=5"

# Next page — preserve the SAME filters/sort/order and use next_cursor
curl "http://localhost:8080/api/v1/jobs?status=OPEN&category=Technology&sort=minSalary&order=desc&limit=5&after=<next_cursor>"

# Filter by salary range
curl "http://localhost:8080/api/v1/jobs?min_price=80000&max_price=150000"
```

### Geospatial (Map API)

```bash
# Jobs within 50 km of San Francisco
curl "http://localhost:8080/api/v1/jobs/map?latitude=37.7749&longitude=-122.4194&radius_km=50"

# Jobs in a bounding box (New York metro)
curl "http://localhost:8080/api/v1/jobs/map?min_lat=40.4&min_lon=-74.3&max_lat=41.1&max_lon=-73.5"
```

### Calendar API (interview slots)

~~~sh
curl "http://localhost:8080/api/v1/slots?starts_at=2030-01-01T00:00:00Z&ends_at=2030-02-01T00:00:00Z"
~~~

Creating jobs requires the owning COMPANY or ADMIN; candidates book for themselves. Use the [executable walkthrough](docs/api-walkthrough.md) to register synthetic accounts, create profiles, capture IDs/tokens, post a job, apply, review, and schedule. It also verifies the 5 km GraphQL query and expected errors:

~~~sh
python3 scripts/api-walkthrough.py
~~~

Run it against a disposable demo stack. It creates records, removes its application/slot at the end, and leaves the company, candidate, job, and accounts for inspection. It never prints tokens or passwords.

---

## GraphQL examples

Open the GraphQL Playground at **http://localhost:8080/graphql** and run:

```graphql
# Paginated job list with company info
query {
  jobs(first: 5, status: "OPEN", category: "Technology") {
    edges {
      node {
        id title location minSalary maxSalary
        company { companyName industry }
      }
      cursor
    }
    pageInfo { hasNextPage endCursor }
  }
}

# Geospatial search
query {
  jobsNear(latitude: 37.7749, longitude: -122.4194, radiusKm: 50) {
    id title location latitude longitude
  }
}

# Calendar — upcoming slots
query {
  slots(startsAt: "2026-12-01T00:00:00Z", endsAt: "2026-12-31T23:59:59Z") {
    id jobId startsAt endsAt status
  }
}

# Auth via GraphQL
mutation {
  login(username: "demo", password: "demo-password") {
    token email role
  }
}
```

---

## Running tests locally

From the repository root, with Java 21 and Maven 3.9+:

~~~sh
mvn -B -f backend/pom.xml -Duser.timezone=Asia/Kolkata verify
~~~

The full suite uses isolated, migrated H2 databases and random HTTP ports. No running API, external PostgreSQL, or Maven integration-test profile is required. Contract snapshots are compared without regeneration.

For the five PostgreSQL regression suites, Docker Compose provides separate disposable databases:

~~~sh
docker compose -p jobportal-tests -f compose.test.yml up -d --wait
bash scripts/test-postgres.sh
docker compose -p jobportal-tests -f compose.test.yml down
~~~

The test databases use port 55443 and temporary storage. Tests delete their fixtures; do not point them at application data. See [test setup and CI](docs/testing.md) for ports, suite coverage, and troubleshooting.

### CI pipeline

[.github/workflows/ci.yml](.github/workflows/ci.yml) runs on pull requests and pushes to main:

1. Java 21 build and full H2 regression suite, including OpenAPI/GraphQL contract checks.
2. PostgreSQL 15 suites covering schema/seeds, discovery/geography, scheduling/application lifecycle, API contracts, and authorization.
3. A separate Docker Compose demo stack running the same executable candidate/company/admin walkthrough documented above.

CI uploads test reports and demo-stack logs, then removes its disposable stack. It requires no repository secrets and does not regenerate contract snapshots.

---

## Architecture overview

The service uses **Approach A: REST-First + GraphQL Layer**:

```
Client
  │
  ├── REST    → /api/v1/**  (Spring MVC controllers)
  │                │
  └── GraphQL → /graphql/query (graphql-java-kickstart resolvers)
                   │
            Service Layer (JobService, CompanyService, etc.)
                   │
            JPA Repositories → PostgreSQL
```

See [`docs/ADR.md`](docs/ADR.md) for the full Architecture Decision Record and
[`docs/openapi.yaml`](docs/openapi.yaml) for the generated OpenAPI 3.0 contract. See [API contracts and manual verification](docs/api-contracts.md) for the documentation source, workflow, validation rules, and test commands.

---

## Authorization

REST and GraphQL share role and resource-ownership checks. See [the access matrix and manual verification guide](docs/authorization.md) for permitted operations, private fields, and authentication errors.

## Key endpoints summary

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/health` | GET | No | Health check |
| `/docs` | GET | No | Swagger UI |
| `/graphql` | GET | No | GraphQL Playground |
| `/graphql/query` | POST | Public discovery; role/ownership for protected fields and writes | GraphQL API |
| `/api/v1/auth/token` | POST | No | Issue JWT token |
| `/api/v1/auth/login` | POST | No | Login with email |
| `/api/v1/auth/register` | POST | No | Register account |
| `/api/v1/jobs` | GET | No | List jobs (cursor pagination) |
| `/api/v1/jobs/{id}` | GET | No | Get job |
| `/api/v1/jobs` | POST | Owning COMPANY or ADMIN | Create job |
| `/api/v1/jobs/map` | GET | No | Geospatial job search |
| `/api/v1/slots` | GET | No | List interview slots |
| `/api/v1/slots` | POST | Auth | Book a slot |
| `/api/v1/companies` | GET | No | List companies |
| `/api/v1/applications` | POST | CANDIDATE | Submit application |

Timestamp, scheduling, and application workflow rules and manual checks: [scheduling-and-applications.md](docs/scheduling-and-applications.md).
