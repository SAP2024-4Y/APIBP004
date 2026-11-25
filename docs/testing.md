# Tests and continuous integration

Run commands from the repository root. Java 21, Maven 3.9+, and Python 3.10+ are required; Docker with Compose v2 is needed for PostgreSQL and the demo walkthrough. The first Maven build downloads dependencies. Subsequent builds can add -o if everything is cached.

## Full regression suite

~~~sh
mvn -B -f backend/pom.xml -Duser.timezone=Asia/Kolkata verify
~~~

Tests create isolated H2 databases, apply Flyway migrations, and start embedded HTTP servers on random ports. They do not call an already-running Compose API. The command includes unit, authorization, schema/seed, discovery, lifecycle, rate-limit, registration, resume, and contract tests. No integration-test Maven profile exists or is needed.

Contract checks parse the served OpenAPI JSON and checked-in YAML, resolve internal references, compare routes, compare snapshots, validate request/response examples against the supported schema shapes, and compare representative live responses. GraphQL checks compare the published SDL with the source and inspect runtime field/argument/input types, including nullability. HTTP tests exercise actual queries/mutations; the walkthrough executes the checked-in GraphQL examples.

These checks do not claim exhaustive semantic validation of every possible response. Existing examples, representative live responses, and workflow assertions form complementary coverage. Snapshot generation is an explicit maintenance operation described in api-contracts.md; CI never regenerates snapshots to hide drift.

## PostgreSQL regression suites

~~~sh
docker compose -p jobportal-tests -f compose.test.yml up -d --wait
bash scripts/test-postgres.sh
docker compose -p jobportal-tests -f compose.test.yml down
~~~

compose.test.yml is standalone. Its PostgreSQL 15 container has temporary storage and initializes five databases from database/test/create-databases.sql. It does not attach the application's data volumes. Test fixture cleanup deletes rows; use only these disposable databases. Stop/remove this test project after use.

| Suite | Database suffix | Coverage |
| --- | --- | --- |
| SchemaAndSeedIntegrationTest | seed | Flyway, constraints, seed references/counts, rollback, repair, concurrency |
| JobDiscoveryIntegrationTest | discovery | All supported sorts, cursor boundaries, combined filters, geography, salary migration |
| SchedulingLifecycleIntegrationTest | lifecycle | UTC offsets, concurrent reservations/applications/reviews, transitions, rollback notifications |
| ApiContractIntegrationTest | contract | Published contracts, live errors/responses, resume round trips, GraphQL schema |
| ResourceAuthorizationIntegrationTest | authorization | Roles, ownership, private fields, cross-owner attempts |

Each database is named jobportal_test_<suffix>, using local synthetic credentials jobportal_test / local-test-only. They are separate so suite fixture cleanup cannot collide with another suite's rows. The script runs with America/New_York to complement the full suite's Asia/Kolkata setting.

If port 55443 is occupied, set TEST_DB_PORT for both commands, for example:

~~~sh
TEST_DB_PORT=55445 docker compose -p jobportal-tests -f compose.test.yml up -d --wait
TEST_DB_PORT=55445 bash scripts/test-postgres.sh
~~~

TEST_DB_HOST defaults to localhost. Do not use an existing application database. CI creates the same five databases in its PostgreSQL service and sets TEST_DB_PORT=5432.

## Demo walkthrough

The separate [executable walkthrough](api-walkthrough.md) uses the actual packaged app, demo seed data, PostgreSQL, and live HTTP. It checks candidate/company/admin journeys and representative errors, then compares equivalent REST/GraphQL data.

## GitHub Actions

.github/workflows/ci.yml runs on pull_request and main pushes with read-only repository permissions. Java 21 and PostgreSQL 15 run the regression suites. A separate job builds the Docker Compose stack and runs the same Python walkthrough. Reports and stack logs are uploaded even when a job fails; the disposable stack is removed in an always step. No user credentials or repository secrets are needed.

Check the failed test name in the uploaded Surefire reports, or the failing HTTP step and Compose logs for walkthrough failures. The default per-IP quota is 60 requests/minute; integration test contexts raise/mock it where needed, while the walkthrough honors Retry-After without disabling the real filter. Shared-IP activity may require waiting before another run. JVM/Mockito warnings and delayed Surefire shutdown messages should be distinguished from the Maven exit status and reported test failures.
