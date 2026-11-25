package com.jobportal.graphql;

import com.jobportal.entity.Application;
import com.jobportal.entity.Company;
import com.jobportal.entity.Job;
import com.jobportal.repository.ApplicationRepository;
import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.DataLoader;
import org.springframework.beans.factory.annotation.Autowired;
import com.jobportal.security.ResourceAuthorization;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class JobResolver implements GraphQLResolver<Job> {

    @Autowired
    private ResourceAuthorization access;

    @Autowired
    private ApplicationRepository applicationRepository;

    public CompletableFuture<Company> company(Job job, DataFetchingEnvironment env) {
        if (job.getCompanyId() == null) return CompletableFuture.completedFuture(null);
        DataLoader<Integer, Company> loader = env.getDataLoader("companyLoader");
        return loader.load(job.getCompanyId());
    }

    public List<Application> applications(Job job, DataFetchingEnvironment env) {
        access.requireJobOwner(access.actor(env), job.getId());
        return applicationRepository.findByJobId(job.getId());
    }
    public String startsAt(Job job) { return time(job.getStartsAt()); }
    public String endsAt(Job job) { return time(job.getEndsAt()); }
    public String createdAt(Job job) { return time(job.getCreatedAt()); }
    private String time(java.time.LocalDateTime value) {
        return com.jobportal.time.UtcTime.format(value);
    }
}
