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
git clone https://github.com/<course-org>/APIBP004
cd APIBP004
docker compose up
# Server:        http://localhost:8080
# REST docs:     http://localhost:8080/docs
# GraphQL IDE:   http://localhost:8080/graphql
# Health check:  http://localhost:8080/health
```

The service seeds **25+ job postings** with geospatial coordinates and interview slots automatically on first boot. No manual migration or seed commands are needed.

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

### List jobs with filtering and cursor pagination

```bash
# First page — open Technology jobs, sorted by salary
curl "http://localhost:8080/api/v1/jobs?status=OPEN&category=Technology&sort=minSalary&order=desc&limit=5"

# Next page — use next_cursor from previous response
curl "http://localhost:8080/api/v1/jobs?limit=5&after=<next_cursor>"

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

```bash
# List slots in a time window
curl "http://localhost:8080/api/v1/slots?starts_at=2026-12-01T00:00:00Z&ends_at=2026-12-31T23:59:59Z"

# Book a slot (requires auth — 409 if conflict)
curl -X POST http://localhost:8080/api/v1/slots \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"jobId":1,"startsAt":"2026-12-15T10:00:00Z","endsAt":"2026-12-15T11:00:00Z"}'
```

### Create a job (company role required)

```bash
curl -X POST http://localhost:8080/api/v1/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "companyId": 1,
    "title": "Senior Backend Engineer",
    "description": "Building scalable APIs for our platform.",
    "category": "Technology",
    "status": "OPEN",
    "location": "San Francisco, CA",
    "minSalary": 120000,
    "maxSalary": 160000,
    "employmentType": "Full-time",
    "skillsRequired": "Java, Spring Boot, PostgreSQL",
    "latitude": 37.7749,
    "longitude": -122.4194,
    "startsAt": "2026-12-20T09:00:00Z",
    "endsAt": "2026-12-20T10:00:00Z"
  }'
```

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

### With Maven (requires Java 21 + PostgreSQL or uses H2 test profile)

```bash
cd backend
mvn test
```

### Full integration test with Docker

```bash
docker compose up -d
# Wait for healthy status
curl http://localhost:8080/health
# Run tests against running service
mvn verify -Pintegration-test
docker compose down
```

### CI pipeline

Every Pull Request triggers `.github/workflows/ci.yml` which:
1. Spins up PostgreSQL as a service container
2. Runs `mvn verify` (build + unit + integration tests)
3. Runs a Docker Compose smoke test probing `/health`, `/api/v1/auth/token`, and the list/map/calendar endpoints

---

## Architecture overview

The service uses **Approach A: REST-First + GraphQL Layer**:

```
Client
  │
  ├── REST    → /api/v1/**  (Spring MVC controllers)
  │                │
  └── GraphQL → /graphql   (graphql-java-kickstart resolvers)
                   │
            Service Layer (JobService, CompanyService, etc.)
                   │
            JPA Repositories → PostgreSQL
```

See [`docs/ADR.md`](docs/ADR.md) for the full Architecture Decision Record and
[`docs/openapi.yaml`](docs/openapi.yaml) for the complete OpenAPI 3.1 specification.

---

## Key endpoints summary

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/health` | GET | No | Health check |
| `/docs` | GET | No | Swagger UI |
| `/graphql` | GET/POST | No (IDE) / Token for mutations | GraphQL Playground + API |
| `/api/v1/auth/token` | POST | No | Issue JWT token |
| `/api/v1/auth/login` | POST | No | Login with email |
| `/api/v1/auth/register` | POST | No | Register account |
| `/api/v1/jobs` | GET | No | List jobs (cursor pagination) |
| `/api/v1/jobs/{id}` | GET | No | Get job |
| `/api/v1/jobs` | POST | COMPANY | Create job |
| `/api/v1/jobs/map` | GET | No | Geospatial job search |
| `/api/v1/slots` | GET | No | List interview slots |
| `/api/v1/slots` | POST | Auth | Book a slot |
| `/api/v1/companies` | GET | No | List companies |
| `/api/v1/applications` | POST | CANDIDATE | Submit application |
