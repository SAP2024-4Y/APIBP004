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
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class JobQueryResolver implements GraphQLQueryResolver {

    @Autowired private JobService jobService;
    @Autowired private com.jobportal.service.JobSearchService discovery;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private InterviewSlotService slotService;

    public JobConnection jobs(Integer first, String after, String status, String category,
                              Integer minPrice, Integer maxPrice, String sort, String order,
                              String title, String location, String skills) {
        var page = discovery.jobs(new com.jobportal.service.JobSearchService.Filters(status, category, minPrice, maxPrice, title, location, skills),
                after, first != null ? first : 20, sort, order);
        List<JobEdge> edges = new ArrayList<>();
        for (int i = 0; i < page.items().size(); i++) edges.add(new JobEdge(page.items().get(i), page.cursors().get(i)));
        return new JobConnection(edges, new PageInfo(page.hasMore(), page.hasPrevious(), page.startCursor(), page.endCursor()), edges.size());
    }

    public Job job(String id) {
        return jobService.getJobById(Integer.parseInt(id));
    }

    public CompanyConnection companies(Integer first, String after) {
        var page = discovery.companies(after, first != null ? first : 20);
        List<CompanyEdge> edges = new ArrayList<>();
        for (int i = 0; i < page.items().size(); i++) edges.add(new CompanyEdge(page.items().get(i), page.cursors().get(i)));
        return new CompanyConnection(edges, new PageInfo(page.hasMore(), page.hasPrevious(), page.startCursor(), page.endCursor()), edges.size());
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
        LocalDateTime start = com.jobportal.time.UtcTime.parse(startsAt);
        LocalDateTime end = com.jobportal.time.UtcTime.parse(endsAt);
        return slotService.findByTimeWindow(start, end);
    }

}
