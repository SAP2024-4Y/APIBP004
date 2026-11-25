package com.jobportal.seed;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Data
@Component
@ConfigurationProperties(prefix = "app.seed")
public class SeedProperties {
    public enum Mode { OFF, DEMO, BULK }
    private Mode mode = Mode.OFF;
    private long randomSeed = 42;
    private LocalDateTime baseTime = LocalDateTime.of(2027, 1, 1, 9, 0);
    private Integer companies, candidates, jobs, applications, slots, resumes;

    public record Counts(int companies, int candidates, int jobs, int applications, int slots, int resumes) {}

    public Counts counts() {
        boolean bulk = mode == Mode.BULK;
        Counts counts = new Counts(value(companies, bulk ? 100 : 5), value(candidates, bulk ? 500 : 5),
                value(jobs, bulk ? 1000 : 25), value(applications, bulk ? 2000 : 10),
                value(slots, bulk ? 500 : 5), value(resumes, bulk ? 500 : 5));
        if (counts.companies < 1 || counts.candidates < 1 || counts.jobs < 1)
            throw new IllegalArgumentException("Seed companies, candidates, and jobs must be positive");
        for (int count : new int[]{counts.companies, counts.candidates, counts.jobs, counts.applications, counts.slots, counts.resumes})
            if (count < 0 || count > 100_000) throw new IllegalArgumentException("Seed counts must be between 0 and 100000");
        if (counts.resumes > counts.candidates) throw new IllegalArgumentException("Seed resumes cannot exceed candidates (one sample per candidate)");
        if ((long) counts.applications > (long) counts.candidates * counts.jobs)
            throw new IllegalArgumentException("Seed applications exceed unique candidate/job pairs");
        if (baseTime == null) throw new IllegalArgumentException("Seed base-time is required");
        return counts;
    }
    private int value(Integer configured, int fallback) { return configured == null ? fallback : configured; }
}
