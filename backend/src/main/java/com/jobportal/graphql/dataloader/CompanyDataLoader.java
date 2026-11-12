package com.jobportal.graphql.dataloader;

import com.jobportal.entity.Company;
import com.jobportal.repository.CompanyRepository;
import org.dataloader.MappedBatchLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
public class CompanyDataLoader implements MappedBatchLoader<Integer, Company> {

    @Autowired
    private CompanyRepository companyRepository;

    @Override
    public CompletableFuture<Map<Integer, Company>> load(Set<Integer> companyIds) {
        return CompletableFuture.supplyAsync(() -> {
            List<Company> companies = companyRepository.findAllById(companyIds);
            Map<Integer, Company> result = new HashMap<>();
            for (Company c : companies) {
                result.put(c.getId(), c);
            }
            return result;
        });
    }
}
