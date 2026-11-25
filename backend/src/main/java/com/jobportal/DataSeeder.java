package com.jobportal;

import com.jobportal.seed.SeedDataService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private final SeedDataService seedData;

    @Override
    public void run(ApplicationArguments args) {
        // The separate service proxy commits the transaction before success is logged.
        SeedDataService.Report report = seedData.seed();
        log.info("Synthetic seed complete: inserted={}, reused={}", report.inserted(), report.reused());
    }
}
