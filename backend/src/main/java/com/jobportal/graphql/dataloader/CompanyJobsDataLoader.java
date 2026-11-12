package com.jobportal.graphql.dataloader;

import com.jobportal.entity.Job;
import com.jobportal.repository.JobRepository;
import org.dataloader.MappedBatchLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class CompanyJobsDataLoader implements MappedBatchLoader<Integer, List<Job>> {

    @Autowired
    private JobRepository jobRepository;

    @Override
    public CompletableFuture<Map<Integer, List<Job>>> load(Set<Integer> companyIds) {
        return CompletableFuture.supplyAsync(() -> {
            List<Job> jobs = jobRepository.findByCompanyIdIn(new ArrayList<>(companyIds));
            Map<Integer, List<Job>> result = new HashMap<>();
            for (Integer id : companyIds) {
                result.put(id, new ArrayList<>());
            }
            for (Job job : jobs) {
                result.computeIfAbsent(job.getCompanyId(), k -> new ArrayList<>()).add(job);
            }
            return result;
        });
    }
}
