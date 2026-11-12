package com.jobportal.graphql;

import com.jobportal.entity.Company;
import com.jobportal.entity.Job;
import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.DataLoader;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class CompanyResolver implements GraphQLResolver<Company> {

    public CompletableFuture<List<Job>> jobs(Company company, DataFetchingEnvironment env) {
        DataLoader<Integer, List<Job>> loader = env.getDataLoader("companyJobsLoader");
        return loader.load(company.getId());
    }
}
