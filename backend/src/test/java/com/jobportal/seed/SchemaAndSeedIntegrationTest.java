package com.jobportal.seed;

import com.jobportal.DataSeeder;
import com.jobportal.service.EmailService;
import com.jobportal.service.JobService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {
        "spring.datasource.url=$" + "{seed.test.jdbc-url:jdbc:h2:mem:seed-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.driver-class-name=$" + "{seed.test.driver:org.h2.Driver}",
        "spring.datasource.username=$" + "{seed.test.username:sa}",
        "spring.datasource.password=$" + "{seed.test.password:}",
        "spring.jpa.properties.hibernate.dialect=$" + "{seed.test.dialect:org.hibernate.dialect.H2Dialect}",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF", "app.email.enabled=false", "app.seed.mode=off"
})
class SchemaAndSeedIntegrationTest {
    @TempDir static Path storage;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry properties) {
        properties.add("app.resume.upload-dir", () -> storage.toString());
    }
    @Autowired SeedDataService seeder;
    @Autowired SeedProperties config;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired JobService jobs;
    @Autowired PasswordEncoder passwords;
    @MockBean DataSeeder runner;
    @MockBean EmailService email;

    @BeforeEach void reset() throws Exception {
        jdbc.update("DELETE FROM demo_seed_records");
        for (String table : List.of("interview_slots", "resumes", "applications", "jobs", "candidates", "companies", "users")) jdbc.update("DELETE FROM " + table);
        jdbc.update("UPDATE demo_seed_state SET random_seed=NULL, base_time=NULL WHERE id=1");
        try (var paths = Files.list(storage)) { for (Path path : paths.toList()) Files.delete(path); }
        config.setMode(SeedProperties.Mode.DEMO); config.setRandomSeed(42);
        config.setCompanies(null); config.setCandidates(null); config.setJobs(null);
        config.setApplications(null); config.setSlots(null); config.setResumes(null);
    }

    @Test void migrationsApplyOnceAndEnforceRelationshipsAndUniqueness() {
        assertEquals(0, flyway.migrate().migrationsExecuted);
        seeder.seed();
        long user = id("candidate-user/0"), candidate = id("candidate/0"), company = id("company/0"), job = id("job/0");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO candidates(user_id,full_name) VALUES (?,?)", user, "Duplicate"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO companies(user_id,company_name) VALUES (?,?)", id("company-user/0"), "Duplicate"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO applications(candidate_id,job_id) VALUES (?,?)", candidate, job));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO resumes(candidate_id,file_path) VALUES (-1,'invalid')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO jobs(company_id,title) VALUES (-1,'Invalid job')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM companies WHERE id=?", company));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM users WHERE id=?", user));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE jobs SET min_salary=200, max_salary=100 WHERE id=?", job));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE interview_slots SET ends_at=starts_at"));
        assertEquals(5, count("companies"));
    }

    @Test void demoUsesRealIdsAndRecoversWhenOnlyTheAdminAlreadyExists() throws Exception {
        for (String table : List.of("users", "candidates", "companies", "jobs", "applications", "interview_slots", "resumes"))
            jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH " + (table.equals("users") ? 1000 : 2000));
        jdbc.update("INSERT INTO users(email,password,role) VALUES ('demo@jobportal.com','existing-hash','ADMIN')");
        jdbc.update("INSERT INTO users(email,password,role) VALUES ('unrelated@example.com','untouched','CANDIDATE')");
        var report = seeder.seed();
        assertEquals(11, report.inserted().get("users"));
        assertEquals(13, count("users")); assertEquals(25, count("jobs")); assertEquals(10, count("applications"));
        assertNotEquals(id("candidate-user/0"), id("candidate/0"));
        assertEquals(id("candidate/0"), jdbc.queryForObject("SELECT candidate_id FROM interview_slots WHERE id=?", Long.class, id("slot/0")));
        assertEquals(id("job/0"), jdbc.queryForObject("SELECT job_id FROM interview_slots WHERE id=?", Long.class, id("slot/0")));
        assertEquals("existing-hash", jdbc.queryForObject("SELECT password FROM users WHERE email='demo@jobportal.com'", String.class));
        assertEquals("untouched", jdbc.queryForObject("SELECT password FROM users WHERE email='unrelated@example.com'", String.class));
        verifyRelations(); verifyNoInteractions(email);
        assertTrue(Files.readString(storage.resolve("seed-v1-candidate-0.pdf")).startsWith("%PDF-1.4"));
        assertTrue(passwords.matches("password123", jdbc.queryForObject("SELECT password FROM users WHERE id=?", String.class, id("candidate-user/0"))));
    }

    @Test void rerunsPreserveEditsAndRepairDeletedRecordsAndFiles() throws Exception {
        seeder.seed();
        long job = id("job/24"), oldResume = id("resume/0");
        jdbc.update("UPDATE jobs SET title='Manually reviewed title' WHERE id=?", job);
        Files.delete(storage.resolve("seed-v1-candidate-0.pdf"));
        jdbc.update("DELETE FROM resumes WHERE id=?", oldResume);
        jdbc.update("DELETE FROM interview_slots WHERE id=?", id("slot/4"));
        var repaired = seeder.seed();
        assertEquals(Map.of("resumes", 1, "interview_slots", 1), repaired.inserted());
        assertEquals("Manually reviewed title", jdbc.queryForObject("SELECT title FROM jobs WHERE id=?", String.class, job));
        assertNotEquals(oldResume, id("resume/0"));
        assertTrue(Files.exists(storage.resolve("seed-v1-candidate-0.pdf")));
        assertTrue(seeder.seed().inserted().isEmpty());
        assertEquals(5, count("resumes")); assertEquals(5, count("interview_slots"));
        verifyRelations();
    }

    @Test void defaultBulkCountsAreReproducibleAndSupportFiveKmSearch() throws Exception {
        config.setMode(SeedProperties.Mode.BULK);
        var first = seeder.seed();
        assertEquals(100, count("companies")); assertEquals(500, count("candidates"));
        assertEquals(1000, count("jobs")); assertEquals(2000, count("applications"));
        assertEquals(500, count("interview_slots")); assertEquals(500, count("resumes")); assertEquals(602, count("users"));
        assertEquals(1000, first.inserted().get("jobs"));
        var before = semanticJobs();
        assertTrue(seeder.seed().inserted().isEmpty()); assertEquals(before, semanticJobs());
        var near = jobs.findJobsWithinRadius(37.7749, -122.4194, 5).stream().map(job -> job.getId().longValue()).toList();
        for (int i = 0; i < 3; i++) assertTrue(near.contains(id("job/" + i)));
        for (int i = 3; i < 5; i++) assertFalse(near.contains(id("job/" + i)));
        verifyRelations(); verifyNoInteractions(email);
        // Regeneration after an explicit test reset has identical semantic values, despite new IDs/hashes.
        reset(); config.setMode(SeedProperties.Mode.BULK); seeder.seed(); assertEquals(before, semanticJobs());
    }

    @Test void demoCanExpandToBulkWithoutDuplicatingApplications() {
        seeder.seed(); long originalJob = id("job/0");
        config.setMode(SeedProperties.Mode.BULK); seeder.seed();
        assertEquals(originalJob, id("job/0"));
        assertEquals(1000, count("jobs")); assertEquals(2000, count("applications"));
        assertEquals(500, count("resumes")); assertTrue(seeder.seed().inserted().isEmpty()); verifyRelations();
    }

    @Test void invalidConfigurationOrExistingRoleFailsWithoutPartialWrites() {
        config.setApplications(126); // Only 5 * 25 distinct pairs exist in demo mode.
        assertThrows(IllegalArgumentException.class, seeder::seed); assertEquals(0, count("users"));
        config.setApplications(10);
        jdbc.update("INSERT INTO users(email,password,role) VALUES ('hr@techcorp.com','do-not-change','CANDIDATE')");
        assertThrows(IllegalStateException.class, seeder::seed);
        assertEquals(1, count("users")); assertEquals(0, count("companies")); assertEquals(0, count("demo_seed_records"));
    }

    @Test void fileFailureRollsBackDatabaseAndCleansNewFiles() throws Exception {
        // A directory occupying a sample filename is a storage failure, never a valid PDF.
        Files.createDirectory(storage.resolve("seed-v1-candidate-1.pdf"));
        assertThrows(IllegalStateException.class, seeder::seed);
        assertEquals(0, count("users")); assertEquals(0, count("demo_seed_records"));
        assertFalse(Files.exists(storage.resolve("seed-v1-candidate-0.pdf")));
    }

    @Test void differentSeedCannotSilentlyMixDatasetsAndOffDoesNothing() {
        config.setMode(SeedProperties.Mode.OFF); assertTrue(seeder.seed().inserted().isEmpty()); assertEquals(0, count("users"));
        config.setMode(SeedProperties.Mode.DEMO); seeder.seed();
        config.setRandomSeed(99); assertThrows(IllegalStateException.class, seeder::seed); assertEquals(25, count("jobs"));
    }

    @Test void concurrentRunsAreSerializedWithoutDuplicates() throws Exception {
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var reports = executor.invokeAll(List.<java.util.concurrent.Callable<SeedDataService.Report>>of(seeder::seed, seeder::seed));
            int inserted = 0;
            for (var result : reports) inserted += result.get().inserted().getOrDefault("jobs", 0);
            assertEquals(25, inserted);
            assertEquals(25, count("jobs")); assertEquals(10, count("applications"));
            verifyRelations();
        }
    }

    @Test void nonemptyUnversionedSchemasAreNotAutomaticallyAdopted() {
        jdbc.execute("CREATE SCHEMA \"legacy_bootstrap_check\"");
        try {
            jdbc.execute("CREATE TABLE \"legacy_bootstrap_check\".existing_data(id INTEGER PRIMARY KEY)");
            var legacy = Flyway.configure().dataSource(jdbc.getDataSource())
                    .schemas("legacy_bootstrap_check").defaultSchema("legacy_bootstrap_check")
                    .baselineOnMigrate(false).load();
            assertThrows(org.flywaydb.core.api.FlywayException.class, legacy::migrate);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM \"legacy_bootstrap_check\".existing_data", Integer.class));
        } finally { jdbc.execute("DROP SCHEMA \"legacy_bootstrap_check\" CASCADE"); }
    }

    private long id(String key) { return jdbc.queryForObject("SELECT resource_id FROM demo_seed_records WHERE seed_key=?", Long.class, "v1/" + key); }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private List<Map<String, Object>> semanticJobs() {
        return jdbc.queryForList("SELECT title,description,category,status,latitude,longitude,min_salary,max_salary,employment_type,created_at FROM jobs ORDER BY title");
    }
    private void verifyRelations() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM interview_slots s LEFT JOIN jobs j ON s.job_id=j.id LEFT JOIN candidates c ON s.candidate_id=c.id WHERE j.id IS NULL OR c.id IS NULL", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM applications a LEFT JOIN jobs j ON a.job_id=j.id LEFT JOIN candidates c ON a.candidate_id=c.id WHERE j.id IS NULL OR c.id IS NULL", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM interview_slots a JOIN interview_slots b ON a.id<b.id AND a.job_id=b.job_id AND a.starts_at<b.ends_at AND b.starts_at<a.ends_at", Integer.class));
    }
}
