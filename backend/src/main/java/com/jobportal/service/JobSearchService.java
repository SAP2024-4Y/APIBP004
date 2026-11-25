package com.jobportal.service;

import com.jobportal.entity.Job;
import com.jobportal.entity.Company;
import com.jobportal.exception.ValidationException;
import com.jobportal.service.paging.KeysetPager;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class JobSearchService {
    private final KeysetPager pager;
    public record Filters(String status, String category, Integer minPrice, Integer maxPrice, String title, String location, String skills) {
        public Filters {
            status = clean(status, false); category = clean(category, false);
            title = clean(title, true); location = clean(location, true); skills = clean(skills, true);
            if (status != null && !Set.of("OPEN", "CLOSED", "DRAFT").contains(status)) throw new ValidationException("status must be OPEN, CLOSED, or DRAFT");
            if ((minPrice != null && minPrice < 0) || (maxPrice != null && maxPrice < 0)) throw new ValidationException("Salary bounds must be nonnegative");
            if (minPrice != null && maxPrice != null && minPrice > maxPrice) throw new ValidationException("Minimum salary must not exceed maximum salary");
        }
        private static String clean(String value, boolean lower) {
            if (value == null) return null;
            String result = value.trim();
            if (result.isEmpty() || result.length() > 255) throw new ValidationException("Search text must contain 1–255 characters");
            return lower ? result.toLowerCase(Locale.ROOT) : result;
        }
    }
    public KeysetPager.Page<Job> jobs(Filters filters, String after, int limit, String sortInput, String orderInput) {
        String sort = KeysetPager.sort(sortInput), order = KeysetPager.order(orderInput);
        return pager.fetch(Job.class, "jobs", sort, order, filters, after, limit,
                (cb, root) -> predicate(cb, root, filters), Job::getId, job -> switch (sort) {
                    case "id" -> job.getId(); case "title" -> job.getTitle(); case "minSalary" -> job.getMinSalary();
                    case "maxSalary" -> job.getMaxSalary(); default -> job.getCreatedAt();
                });
    }
    public KeysetPager.Page<Company> companies(String after, int limit) {
        return pager.fetch(Company.class, "companies", "id", "asc", Map.of(), after, limit, (cb, root) -> cb.conjunction(), Company::getId, Company::getId);
    }
    private Predicate predicate(CriteriaBuilder cb, Root<Job> root, Filters f) {
        List<Predicate> predicates = new ArrayList<>();
        if (f.status() != null) predicates.add(cb.equal(root.get("status"), f.status()));
        if (f.category() != null) predicates.add(cb.equal(root.get("category"), f.category()));
        if (f.minPrice() != null) predicates.add(cb.ge(root.get("minSalary"), f.minPrice()));
        if (f.maxPrice() != null) predicates.add(cb.le(root.get("maxSalary"), f.maxPrice()));
        contains(cb, root, predicates, "title", f.title());
        contains(cb, root, predicates, "location", f.location());
        contains(cb, root, predicates, "skillsRequired", f.skills());
        return cb.and(predicates.toArray(Predicate[]::new));
    }
    private void contains(CriteriaBuilder cb, Root<Job> root, List<Predicate> predicates, String field, String value) {
        if (value != null) {
            String escaped = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            predicates.add(cb.like(cb.lower(root.get(field)), "%" + escaped + "%", '\\'));
        }
    }
}
