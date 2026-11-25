#!/usr/bin/env bash
# These tests delete fixture rows. Only use the dedicated jobportal_test_* databases.
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
args=()
for suite in seed discovery lifecycle contract authorization; do
  args+=("-D${suite}.test.jdbc-url=jdbc:postgresql://${TEST_DB_HOST:-localhost}:${TEST_DB_PORT:-55443}/jobportal_test_${suite}")
  args+=("-D${suite}.test.driver=org.postgresql.Driver")
  args+=("-D${suite}.test.username=jobportal_test" "-D${suite}.test.password=local-test-only")
  args+=("-D${suite}.test.dialect=org.hibernate.dialect.PostgreSQLDialect")
done
exec mvn -B -f "$repo_dir/backend/pom.xml" \
  -Dtest=SchemaAndSeedIntegrationTest,JobDiscoveryIntegrationTest,SchedulingLifecycleIntegrationTest,ApiContractIntegrationTest,ResourceAuthorizationIntegrationTest \
  -Duser.timezone=America/New_York "${args[@]}" "$@" test
