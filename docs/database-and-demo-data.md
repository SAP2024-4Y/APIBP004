# Database setup and synthetic demo data

## One maintained schema

Flyway runs [V1__job_portal_schema.sql](../backend/src/main/resources/db/migration/V1__job_portal_schema.sql) before JPA starts. Hibernate uses validate and does not create or drop tables. Local and Docker runs share this path. Restarting/stopping the updated API preserves data. See the [entity diagram and dictionary](entities.md).

Flyway clean and automatic baselining are disabled. A nonempty database without migration history fails startup instead of being assumed to match V1. **The old Hibernate-generated database is not an approved V1 baseline:** it may lack FKs and contain incorrect slot references. Back it up if needed, then test this branch against a separate empty database. In-place legacy import requires reviewed data repair; this PR does not guess how to repair records.

For an isolated review stack alongside your existing stack:

~~~sh
DB_PORT=55440 API_PORT=8081 docker compose -p jobportal-pr4 up --build
~~~

It uses project-scoped database/resume volumes. Standard Compose still uses ports 5432/8080. Fixed container names have been removed to support separate projects. An old API still configured with create-drop can drop its own tables on shutdown; do not use that old application against data you intend to preserve.

## Seed modes and counts

Outside Compose, app.seed.mode defaults to off. Standard Compose explicitly enables demo. Bulk is opt-in:

~~~sh
docker compose -f docker-compose.yml -f compose.bulk.yml up --build
~~~

For bulk in the isolated review project:

~~~sh
DB_PORT=55440 API_PORT=8081 docker compose -p jobportal-pr4 \
  -f docker-compose.yml -f compose.bulk.yml up --build
~~~

| Resource | Demo | Bulk |
| --- | ---: | ---: |
| Companies | 5 | 100 |
| Candidates | 5 | 500 |
| Jobs | 25 | 1,000 |
| Applications | 10 | 2,000 |
| Interview slots | 5 | 500 |
| Resumes with sample PDFs | 5 | 500 |
| Users, including two admins | 12 | 602 |

Override bulk counts:

~~~sh
SEED_COMPANIES=20 SEED_CANDIDATES=100 SEED_JOBS=300 \
SEED_APPLICATIONS=600 SEED_SLOTS=100 SEED_RESUMES=100 \
docker compose -f docker-compose.yml -f compose.bulk.yml up --build
~~~

For direct Java/Maven startup use APP_SEED_MODE=demo or bulk, plus APP_SEED_COMPANIES, APP_SEED_CANDIDATES, APP_SEED_JOBS, APP_SEED_APPLICATIONS, APP_SEED_SLOTS, APP_SEED_RESUMES as needed. Counts are 0–100000; companies/candidates/jobs must be positive. Applications cannot exceed candidates × jobs; sample resumes cannot exceed candidates. Invalid settings fail before writes. Set applications/slots/resumes to zero when not needed.

Default random seed: **42**. Default base time: **2027-01-01T09:00:00**. Override SEED_RANDOM_SEED / SEED_BASE_TIME for Compose bulk, or APP_SEED_RANDOM_SEED / APP_SEED_BASE_TIME for direct startup. Fresh databases with the same settings reproduce field values, coordinates, and timestamps; sequence IDs and salted password hashes may differ. Changing seed/time for an existing dataset is rejected; use a separate empty database/volume.

## Reruns, recovery, and storage

Seeding runs in one transaction with a database row lock. Helpers use actual returned/looked-up IDs, never user IDs in place of profile IDs. The ledger gives each record a stable identity independent of sequences. Known accounts/profiles can be adopted after an older partial seed. Existing passwords/edits are preserved; a known email with the wrong role fails and rolls back.

Identical reruns do not duplicate records. Deleted seed records are recreated if parent relationships allow it, and missing sample files are regenerated. Failure rolls back database writes and removes files created by the failed run. Logs show inserted/reused counts by table. Seeder code bypasses notification-producing services and refuses to run when app.email.enabled is true.

Demo can expand to default bulk without duplicating applications. Counts describe the requested generated dataset, not a destructive target for the whole database. Reducing counts does not delete records; unrelated data and existing relationships remain. After arbitrary count changes, old application pairs outside the newly requested set may remain. Use an empty database for exact fresh counts.

Sample PDFs contain only synthetic text, with no real personal data, scripts, or external references. They use deterministic filenames in APP_RESUME_UPLOAD_DIR. Docker uses /app/uploads/resumes with a persistent resume_data volume. Existing files are not overwritten. Download through the authenticated endpoint; file_path is not a public URL.

To deliberately delete **only the disposable review project's volumes**:

~~~sh
docker compose -p jobportal-pr4 down -v
~~~

Do not use down -v on a project whose data you want to keep. Omit -v to stop while preserving data. Restart the same project to verify stable counts and IDs.

## Demo accounts

These are isolated-demo credentials. The first five company/candidate logins remain familiar; additional bulk addresses use reserved .example domains.

| Role | Email / username | Password |
| --- | --- | --- |
| ADMIN evaluator | demo@jobportal.com / username demo | demo-password |
| ADMIN secondary | admin@jobportal.com | admin-pass |
| COMPANY 1 | hr@techcorp.com | password123 |
| COMPANY 2 | careers@innovatelab.com | password123 |
| COMPANY 3 | jobs@globaltech.com | password123 |
| COMPANY 4 | recruit@financeplus.com | password123 |
| COMPANY 5 | hiring@healthnet.com | password123 |
| CANDIDATE 1 | john.doe@email.com | password123 |
| CANDIDATE 2 | jane.smith@email.com | password123 |
| CANDIDATE 3 | mike.johnson@email.com | password123 |
| CANDIDATE 4 | sarah.wilson@email.com | password123 |
| CANDIDATE 5 | alex.brown@email.com | password123 |
| Additional companies | company0006@seed.jobportal.example through configured count | password123 |
| Additional candidates | candidate0006@seed.jobportal.example through configured count | password123 |

Adopted accounts retain their previous passwords. Use IDs returned by login/profile lookup; never assume userId equals candidateId.

## Manual review

1. Start a fresh isolated demo stack. Verify the demo counts above and inserted-count log.
2. Restart the backend. No records should be inserted; IDs and downloadable files should remain.
3. Enable bulk in the same project. Verify bulk counts and no duplicates on another restart.
4. Log in as a candidate, call GET /api/v1/candidates/user/{userId}, then use the returned candidate ID to list applications/resumes. Download and inspect a sample PDF.
5. Exercise GET /api/v1/jobs?status=OPEN&category=Technology&limit=5 and salary bounds. Data covers three job states, five categories, and multiple salary ranges. Existing pagination limitations remain as documented in [API contracts](api-contracts.md).
6. Search around the house at **37.7749, -122.4194**. The first five generated jobs are **0, 4.9, 4.99, 5.01, 5.1 km north** of it. A 5 km search includes the first three and excludes the last two; additional random jobs may match. Identify jobs by returned title/ID, not assumed IDs 1–5.

~~~sh
curl 'http://localhost:8081/api/v1/jobs/map?latitude=37.7749&longitude=-122.4194&radius_km=5'
~~~

The [GraphQL 5 km query](graphql-examples.md) uses the same house. Boundary job titles are Senior Full Stack Developer, Frontend React Developer, DevOps Engineer, Machine Learning Engineer, and Data Scientist.

7. Read GET /api/v1/slots as ADMIN. With default base time, the first slot is **2027-01-31T09:00:00–09:30:00**, with subsequent slots two hours apart. POST a slot with that record's actual jobId and identical interval: expect **409 SLOT_CONFLICT**. Generated slots do not overlap for a job or candidate.
8. Delete a company with existing jobs: expect **400 DATA_INTEGRITY_ERROR**, with company/jobs intact. Delete dependents explicitly before the parent.

## Local and manual setup

Recommended local setup: create an empty PostgreSQL database named jobportal, configure datasource credentials, then start the backend with APP_SEED_MODE=demo and APP_EMAIL_ENABLED=false. Flyway applies the same schema as Docker; no SQL seeding is needed.

For psql provisioning, database/jobportal.sql includes authoritative V1 in a transaction:

~~~sh
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f database/jobportal.sql
~~~

Use only an **empty** database. On the first application startup after this exact manual bootstrap, set SPRING_FLYWAY_BASELINE_ON_MIGRATE=true and SPRING_FLYWAY_BASELINE_VERSION=1; remove those overrides afterwards. Future migrations run normally. Never baseline an older Hibernate schema this way. database/seed-data.sql now points to the Java seeder rather than maintaining incompatible data.

## Automated verification

~~~sh
mvn -o -f backend/pom.xml test
~~~

The suite migrates isolated H2 databases and validates JPA. Seed tests cover bulk counts, real references with non-default sequences, deterministic regeneration, repeated/concurrent runs, recovery/rollback, sample files, FK/unique/check constraints, and refusal to silently baseline nonempty schemas.

SchemaAndSeedIntegrationTest also supports a **dedicated disposable PostgreSQL database**. It deletes rows and changes sequences; never point it at your normal database:

~~~sh
mvn -o -f backend/pom.xml -Dtest=SchemaAndSeedIntegrationTest \
  -Dseed.test.jdbc-url=jdbc:postgresql://localhost:55439/jobportal_check \
  -Dseed.test.driver=org.postgresql.Driver \
  -Dseed.test.username=jobportal_check -Dseed.test.password=local-check-only \
  -Dseed.test.dialect=org.hibernate.dialect.PostgreSQLDialect test
~~~

The first build needs network access to download Flyway; omit -o if dependencies are not cached.
