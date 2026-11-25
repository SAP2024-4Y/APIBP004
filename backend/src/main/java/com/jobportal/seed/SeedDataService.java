package com.jobportal.seed;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/** Deterministic, opt-in synthetic data. Never calls notification-producing application services. */
@Service
@RequiredArgsConstructor
public class SeedDataService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final SeedProperties properties;
    @Value("$" + "{app.resume.upload-dir:backend/src/main/resources/static/resumes}")
    private String uploadDirectory;
    @Value("$" + "{app.email.enabled:false}")
    private boolean emailEnabled;

    private static final String[] COMPANY_EMAILS = {"hr@techcorp.com", "careers@innovatelab.com", "jobs@globaltech.com", "recruit@financeplus.com", "hiring@healthnet.com"};
    private static final String[] COMPANY_NAMES = {"TechCorp Solutions", "InnovateLab Inc", "GlobalTech Systems", "FinancePlus Ltd", "HealthNet Corp"};
    private static final String[] CANDIDATE_EMAILS = {"john.doe@email.com", "jane.smith@email.com", "mike.johnson@email.com", "sarah.wilson@email.com", "alex.brown@email.com"};
    private static final String[] CANDIDATE_NAMES = {"John Doe", "Jane Smith", "Mike Johnson", "Sarah Wilson", "Alex Brown"};
    private static final String[] CATEGORIES = {"Technology", "Finance", "Healthcare", "Education", "Retail"};
    private static final double[][] CENTERS = {{37.7749, -122.4194}, {40.7128, -74.0060}, {30.2672, -97.7431}, {42.3601, -71.0589}, {47.6062, -122.3321}};
    private static final String[] LOCATIONS = {"San Francisco, CA", "New York, NY", "Austin, TX", "Boston, MA", "Seattle, WA"};
    private static final String[] JOB_TITLES = {"Senior Full Stack Developer", "Frontend React Developer", "DevOps Engineer", "Machine Learning Engineer", "Data Scientist", "Quantitative Analyst", "Financial Software Engineer", "Risk Management Analyst", "Backend Java Developer", "Cloud Architect", "Product Manager - SaaS", "Healthcare Data Engineer", "Clinical Software Developer", "UI/UX Designer - Healthcare", "Mobile App Developer", "Supply Chain Software Engineer", "Computer Vision Engineer", "Fintech Engineer - London", "Software Engineer - Berlin", "Senior Backend Engineer (Remote)", "ML Research Engineer (Remote)", "EdTech Platform Engineer", "E-Commerce Backend Engineer", "Part-time React Developer", "Contract: Trading System QA"};

    public record Report(Map<String, Integer> inserted, Map<String, Integer> reused) {}

    @Transactional
    public Report seed() {
        if (properties.getMode() == SeedProperties.Mode.OFF) return new Report(Map.of(), Map.of());
        SeedProperties.Counts counts = properties.counts();
        if (emailEnabled) throw new IllegalStateException("Synthetic seeding requires app.email.enabled=false");
        // A database lock makes concurrent starts safe, not just consecutive reruns.
        Map<String, Object> state = jdbc.queryForMap("SELECT random_seed, base_time FROM demo_seed_state WHERE id=1 FOR UPDATE");
        if (state.get("random_seed") != null && (((Number) state.get("random_seed")).longValue() != properties.getRandomSeed()
                || !((Timestamp) state.get("base_time")).toLocalDateTime().equals(properties.getBaseTime())))
            throw new IllegalStateException("Seed random-seed/base-time differs from the existing dataset. Use a separate empty demo database for a different configuration.");
        jdbc.update("UPDATE demo_seed_state SET random_seed=?, base_time=? WHERE id=1", properties.getRandomSeed(), properties.getBaseTime());
        Context context = new Context();
        registerFileRollback(context);
        // Hash once per common synthetic password rather than thousands of times. Never reset existing passwords.
        String commonHash = passwords.encode("password123");
        user(context, "admin/demo", "demo@jobportal.com", passwords.encode("demo-password"), "ADMIN");
        user(context, "admin/secondary", "admin@jobportal.com", passwords.encode("admin-pass"), "ADMIN");
        List<Long> companies = new ArrayList<>(), candidates = new ArrayList<>(), jobs = new ArrayList<>();
        for (int i = 0; i < counts.companies(); i++) {
            String email = i < 5 ? COMPANY_EMAILS[i] : String.format(Locale.ROOT, "company%04d@seed.jobportal.example", i + 1);
            long userId = user(context, "company-user/" + i, email, commonHash, "COMPANY");
            companies.add(ensure(context, "company/" + i, "companies", "user_id=?", new Object[]{userId},
                    row("user_id", userId, "company_name", i < 5 ? COMPANY_NAMES[i] : "Synthetic Company " + (i + 1),
                            "industry", CATEGORIES[i % 5], "address", "Synthetic office, " + LOCATIONS[i % 5], "approved", true)));
        }
        for (int i = 0; i < counts.candidates(); i++) {
            String email = i < 5 ? CANDIDATE_EMAILS[i] : String.format(Locale.ROOT, "candidate%04d@seed.jobportal.example", i + 1);
            long userId = user(context, "candidate-user/" + i, email, commonHash, "CANDIDATE");
            candidates.add(ensure(context, "candidate/" + i, "candidates", "user_id=?", new Object[]{userId},
                    row("user_id", userId, "full_name", i < 5 ? CANDIDATE_NAMES[i] : "Synthetic Candidate " + (i + 1),
                            "phone", String.format(Locale.ROOT, "202555%04d", i % 10000), "location", LOCATIONS[i % 5], "experience", (i % 12 + 1) + " years of synthetic software experience")));
        }
        for (int i = 0; i < counts.jobs(); i++) {
            Random random = new Random(properties.getRandomSeed() + i * 104729L);
            int cluster = i < 5 ? 0 : i % 5;
            double latitude = CENTERS[cluster][0] + (random.nextDouble() - .5) * .2;
            double longitude = CENTERS[cluster][1] + (random.nextDouble() - .5) * .2;
            if (i < 5) { // Known northward distances from the San Francisco demo house.
                double km = new double[]{0, 4.9, 4.99, 5.01, 5.1}[i];
                latitude = CENTERS[0][0] + Math.toDegrees(km / 6371.0);
                longitude = CENTERS[0][1];
            }
            String title = i < JOB_TITLES.length ? JOB_TITLES[i] : "Synthetic " + CATEGORIES[i % 5] + " Engineer " + (i + 1);
            long companyId = companies.get(i % companies.size());
            int min = 40000 + random.nextInt(100) * 1000;
            String status = i < 5 ? "OPEN" : new String[]{"OPEN", "OPEN", "CLOSED", "DRAFT"}[i % 4];
            jobs.add(ensure(context, "job/" + i, "jobs", "company_id=? AND title=?", new Object[]{companyId, title},
                    row("company_id", companyId, "title", title, "description", "Synthetic recruitment posting for API demonstrations, filtering, and related-resource workflows.",
                            "skills_required", "Java, Spring Boot, PostgreSQL", "salary_range", min + " - " + (min + 30000),
                            "location", LOCATIONS[cluster], "employment_type", new String[]{"Full-time", "Part-time", "Contract", "Internship", "Freelance"}[i % 5],
                            "latitude", latitude, "longitude", longitude, "min_salary", min, "max_salary", min + 30000,
                            "category", CATEGORIES[i % 5], "status", status, "created_at", properties.getBaseTime().minusMinutes(i), "starts_at", null, "ends_at", null)));
        }
        for (int i = 0; i < counts.applications(); i++) {
            int candidateIndex = i % candidates.size();
            int jobIndex = (i / candidates.size() + candidateIndex) % jobs.size();
            long candidateId = candidates.get(candidateIndex), jobId = jobs.get(jobIndex);
            ensure(context, "application/c" + candidateIndex + "/j" + jobIndex, "applications", "candidate_id=? AND job_id=?", new Object[]{candidateId, jobId},
                    row("candidate_id", candidateId, "job_id", jobId, "status", new String[]{"SUBMITTED", "REVIEWED", "SHORTLISTED", "REJECTED", "HIRED"}[i % 5], "applied_at", properties.getBaseTime().plusMinutes(i)));
        }
        for (int i = 0; i < counts.slots(); i++) {
            long jobId = jobs.get(i % jobs.size()), candidateId = candidates.get(i % candidates.size());
            LocalDateTime start = properties.getBaseTime().plusDays(30).plusHours(i * 2L);
            ensure(context, "slot/" + i, "interview_slots", "job_id=? AND candidate_id=? AND starts_at=? AND ends_at=?", new Object[]{jobId, candidateId, start, start.plusMinutes(30)},
                    row("job_id", jobId, "candidate_id", candidateId, "starts_at", start, "ends_at", start.plusMinutes(30), "status", "SCHEDULED", "created_at", properties.getBaseTime()));
        }
        for (int i = 0; i < counts.resumes(); i++) {
            String filename = "seed-v1-candidate-" + i + ".pdf";
            String stored = "/static/resumes/" + filename;
            long candidateId = candidates.get(i);
            long resumeId = ensure(context, "resume/" + i, "resumes", "candidate_id=? AND file_path=?", new Object[]{candidateId, stored},
                    row("candidate_id", candidateId, "file_path", stored, "uploaded_at", properties.getBaseTime()));
            // Preserve edited metadata: repair only this seeder's own deterministic filename.
            String actualPath = jdbc.queryForObject("SELECT file_path FROM resumes WHERE id=?", String.class, resumeId);
            if (stored.equals(actualPath)) writeSample(context, filename, i);
        }
        return new Report(Map.copyOf(context.inserted), Map.copyOf(context.reused));
    }

    private long user(Context context, String key, String email, String hash, String role) {
        long id = ensure(context, key, "users", "email=?", new Object[]{email}, row("email", email, "password", hash, "role", role, "created_at", properties.getBaseTime()));
        String actual = jdbc.queryForObject("SELECT role FROM users WHERE id=?", String.class, id);
        if (!role.equals(actual)) throw new IllegalStateException("Existing seed account has a different role: " + email);
        return id;
    }

    private long ensure(Context context, String key, String table, String predicate, Object[] args, LinkedHashMap<String, Object> fields) {
        String seedKey = "v1/" + key;
        List<Long> tracked = jdbc.queryForList("SELECT resource_id FROM demo_seed_records WHERE seed_key=? AND resource_type=?", Long.class, seedKey, table);
        if (!tracked.isEmpty() && Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*) > 0 FROM " + table + " WHERE id=?", Boolean.class, tracked.get(0)))) {
            context.reused.merge(table, 1, Integer::sum);
            return tracked.get(0);
        }
        jdbc.update("DELETE FROM demo_seed_records WHERE seed_key=?", seedKey);
        List<Long> existing = jdbc.queryForList("SELECT id FROM " + table + " WHERE " + predicate + " ORDER BY id", Long.class, args);
        long id;
        if (!existing.isEmpty()) {
            if (existing.size() > 1) throw new IllegalStateException("Ambiguous existing synthetic record: " + seedKey);
            id = existing.get(0);
            context.reused.merge(table, 1, Integer::sum);
        } else {
            String sql = "INSERT INTO " + table + " (" + String.join(",", fields.keySet()) + ") VALUES (" + String.join(",", Collections.nCopies(fields.size(), "?")) + ")";
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement(sql, new String[]{"id"});
                int index = 1;
                for (Object value : fields.values()) statement.setObject(index++, value);
                return statement;
            }, keys);
            id = Objects.requireNonNull(keys.getKey()).longValue();
            context.inserted.merge(table, 1, Integer::sum);
        }
        jdbc.update("INSERT INTO demo_seed_records(seed_key,resource_type,resource_id) VALUES (?,?,?)", seedKey, table, id);
        return id;
    }

    private void writeSample(Context context, String filename, int index) {
        Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path path = directory.resolve(filename);
        try {
            Files.createDirectories(directory);
            if (Files.exists(path) && (!Files.isRegularFile(path) || !Files.isReadable(path)))
                throw new IOException("Synthetic resume path is not a readable file");
            if (!Files.exists(path)) {
                Files.write(path, SampleResume.pdf(index), StandardOpenOption.CREATE_NEW);
                context.createdFiles.add(path);
            }
        } catch (IOException ex) { throw new IllegalStateException("Unable to create synthetic resume file", ex); }
    }

    private void registerFileRollback(Context context) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) for (Path path : context.createdFiles) {
                    try { Files.deleteIfExists(path); }
                    catch (IOException ex) { org.slf4j.LoggerFactory.getLogger(SeedDataService.class).warn("Unable to clean up synthetic file {}", path, ex); }
                }
            }
        });
    }
    private static LinkedHashMap<String, Object> row(Object... entries) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) row.put((String) entries[i], entries[i + 1]);
        return row;
    }
    private static class Context {
        final Map<String, Integer> inserted = new TreeMap<>(), reused = new TreeMap<>();
        final List<Path> createdFiles = new ArrayList<>();
    }
}
