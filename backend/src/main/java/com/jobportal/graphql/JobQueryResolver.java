package com.jobportal.graphql;

import com.jobportal.entity.Company;
import com.jobportal.entity.InterviewSlot;
import com.jobportal.entity.Job;
import com.jobportal.graphql.dto.*;
import com.jobportal.repository.CompanyRepository;
import com.jobportal.service.InterviewSlotService;
import com.jobportal.service.JobService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class JobQueryResolver implements GraphQLQueryResolver {

    @Autowired private JobService jobService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private InterviewSlotService slotService;

    public JobConnection jobs(Integer first, String after, String status, String category,
                              Integer minPrice, Integer maxPrice, String sort, String order) {
        int pageSize = first != null ? first : 20;
        JobService.validatePageSize(pageSize);
        Integer afterId = decodeCursor(after);

        List<Job> results = jobService.findForGraphQL(status, category, minPrice, maxPrice, pageSize + 1, afterId);
        boolean hasNextPage = results.size() > pageSize;
        List<Job> page = hasNextPage ? results.subList(0, pageSize) : results;

        List<JobEdge> edges = new ArrayList<>();
        for (Job job : page) {
            edges.add(new JobEdge(job, encodeCursor(job.getId())));
        }

        String endCursor = page.isEmpty() ? null : encodeCursor(page.get(page.size() - 1).getId());
        PageInfo pageInfo = new PageInfo(hasNextPage, false, null, endCursor);
        return new JobConnection(edges, pageInfo, page.size());
    }

    public Job job(String id) {
        return jobService.getJobById(Integer.parseInt(id));
    }

    public CompanyConnection companies(Integer first, String after) {
        int pageSize = first != null ? first : 20;
        JobService.validatePageSize(pageSize);
        List<Company> all = companyRepository.findAll(
            PageRequest.of(0, pageSize + 1, Sort.by(Sort.Direction.ASC, "id"))
        ).getContent();

        boolean hasNextPage = all.size() > pageSize;
        List<Company> page = hasNextPage ? all.subList(0, pageSize) : all;

        List<CompanyEdge> edges = new ArrayList<>();
        for (Company c : page) {
            edges.add(new CompanyEdge(c, encodeCursor(c.getId())));
        }

        String endCursor = page.isEmpty() ? null : encodeCursor(page.get(page.size() - 1).getId());
        PageInfo pageInfo = new PageInfo(hasNextPage, false, null, endCursor);
        return new CompanyConnection(edges, pageInfo, page.size());
    }

    public Company company(String id) {
        return companyRepository.findById(Integer.parseInt(id)).orElse(null);
    }

    public List<Job> jobsNear(Double latitude, Double longitude, Double radiusKm) {
        return jobService.findJobsWithinRadius(latitude, longitude, radiusKm);
    }

    public List<Job> jobsInBox(Double minLat, Double minLon, Double maxLat, Double maxLon) {
        return jobService.findJobsInBoundingBox(minLat, minLon, maxLat, maxLon);
    }

    public List<InterviewSlot> slots(String startsAt, String endsAt) {
        LocalDateTime start = startsAt != null ? LocalDateTime.parse(startsAt.replace("Z", "")) : null;
        LocalDateTime end = endsAt != null ? LocalDateTime.parse(endsAt.replace("Z", "")) : null;
        return slotService.findByTimeWindow(start, end);
    }

    private String encodeCursor(Integer id) {
        return Base64.getEncoder().encodeToString(String.valueOf(id).getBytes());
    }

    private Integer decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            int id = Integer.parseInt(new String(Base64.getDecoder().decode(cursor)));
            if (id <= 0) throw new IllegalArgumentException();
            return id;
        } catch (Exception e) {
            throw new com.jobportal.exception.ValidationException("Invalid cursor value in after");
        }
    }
}
