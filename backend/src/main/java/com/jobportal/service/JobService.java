package com.jobportal.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import com.jobportal.entity.Job;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.exception.ValidationException;
import com.jobportal.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
public class JobService {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobSearchService discovery;

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Cacheable(value = "jobs")
    public List<Job> getAllJobs() {
        return jobRepository.findAll();
    }

    @Cacheable(value = "jobs", key = "#id")
    public Job getJobById(Integer id) {
        if (id == null || id <= 0) {
            throw new ValidationException("Job ID must be a positive number");
        }
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Job not found with ID: " + id + ". The job may have been removed or the ID is incorrect."));
    }

    public List<Job> getJobsByCompanyId(Integer companyId) {
        if (companyId == null || companyId <= 0) {
            throw new ValidationException("Company ID must be a positive number");
        }
        return jobRepository.findByCompanyId(companyId);
    }

    public List<Job> searchJobsByLocation(String location) {
        if (location == null || location.trim().isEmpty()) {
            throw new ValidationException("Location search term cannot be empty");
        }
        return jobRepository.findByLocationContainingIgnoreCase(location.trim());
    }

    public List<Job> searchJobsByTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ValidationException("Title search term cannot be empty");
        }
        return jobRepository.findByTitleContainingIgnoreCase(title.trim());
    }

    public Page<Job> searchJobsWithPagination(String title, String location, String skills, Pageable pageable) {
        return jobRepository.searchJobs(title, location, skills, pageable);
    }

    public Map<String, Object> findWithCursorPagination(
            String status, String category, Integer minPrice, Integer maxPrice,
            String after, int limit, String sortField, String order, String title, String location, String skills) {
        var page = discovery.jobs(new JobSearchService.Filters(status, category, minPrice, maxPrice, title, location, skills), after, limit, sortField, order);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", page.items()); response.put("has_more", page.hasMore());
        response.put("next_cursor", page.hasMore() ? page.endCursor() : null); response.put("limit", limit);
        return response;
    }

    // --- Geospatial ---

    public List<Job> getAllJobsWithCoordinates() {
        return jobRepository.findAllWithCoordinates();
    }

    public List<Job> findJobsWithinRadius(double lat, double lon, double radiusKm) {
        validateCoordinates(lat, lon);
        if (!Double.isFinite(radiusKm) || radiusKm <= 0) throw new ValidationException("radius_km must be positive and finite");
        // Exact spherical-cap bounds, padded by 0.1 mm for floating-point boundary comparisons.
        double angular = Math.min(Math.PI, (radiusKm + 1e-7) / EARTH_RADIUS_KM);
        double latitude = Math.toRadians(lat);
        double minLat = Math.max(-90, Math.toDegrees(latitude - angular));
        double maxLat = Math.min(90, Math.toDegrees(latitude + angular));
        List<Job> candidates;
        if (minLat <= -90 || maxLat >= 90) {
            candidates = jobRepository.findByBoundingBox(minLat, -180.0, maxLat, 180.0);
        } else {
            double longitudeDelta = Math.toDegrees(Math.asin(Math.min(1.0, Math.sin(angular) / Math.cos(latitude))));
            candidates = longitudeBox(minLat, normalizeLongitude(lon - longitudeDelta), maxLat, normalizeLongitude(lon + longitudeDelta));
        }
        return candidates.stream()
                .filter(j -> haversineKm(lat, lon, j.getLatitude(), j.getLongitude()) <= radiusKm + 1e-7)
                .sorted(java.util.Comparator.comparing(Job::getId)).collect(Collectors.toList());
    }

    public List<Job> findJobsInBoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
        validateCoordinates(minLat, minLon);
        validateCoordinates(maxLat, maxLon);
        if (minLat > maxLat) throw new ValidationException("Minimum latitude must not exceed maximum latitude");
        return longitudeBox(minLat, minLon, maxLat, maxLon);
    }

    private List<Job> longitudeBox(double minLat, double minLon, double maxLat, double maxLon) {
        if (minLon <= maxLon && minLon != -180 && maxLon != 180)
            return jobRepository.findByBoundingBox(minLat, minLon, maxLat, maxLon);
        Map<Integer, Job> combined = new java.util.TreeMap<>();
        if (minLon <= maxLon) {
            jobRepository.findByBoundingBox(minLat, minLon, maxLat, maxLon).forEach(job -> combined.put(job.getId(), job));
            if (minLon == -180) jobRepository.findByBoundingBox(minLat, 180.0, maxLat, 180.0).forEach(job -> combined.put(job.getId(), job));
            if (maxLon == 180) jobRepository.findByBoundingBox(minLat, -180.0, maxLat, -180.0).forEach(job -> combined.put(job.getId(), job));
            return List.copyOf(combined.values());
        }
        jobRepository.findByBoundingBox(minLat, minLon, maxLat, 180.0).forEach(job -> combined.put(job.getId(), job));
        jobRepository.findByBoundingBox(minLat, -180.0, maxLat, maxLon).forEach(job -> combined.put(job.getId(), job));
        return List.copyOf(combined.values());
    }

    private double normalizeLongitude(double value) {
        return ((value + 180) % 360 + 360) % 360 - 180;
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        a = Math.max(0.0, Math.min(1.0, a));
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // --- Mutations ---

    @CacheEvict(value = "jobs", allEntries = true)
    public Job saveJob(Job job) {
        validateJob(job);
        if (job.getMinSalary() != null || job.getMaxSalary() != null) {
            job.setSalaryRange(job.getMinSalary() == null ? "Up to " + job.getMaxSalary()
                    : job.getMaxSalary() == null ? job.getMinSalary() + "+"
                    : job.getMinSalary() + " - " + job.getMaxSalary());
        }
        try {
            return jobRepository.save(job);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new com.jobportal.exception.ServiceException("Failed to save job posting. Please try again later.", e);
        }
    }

    @CacheEvict(value = "jobs", allEntries = true)
    public void deleteJob(Integer id) {
        if (id == null || id <= 0) {
            throw new ValidationException("Job ID must be a positive number");
        }
        getJobById(id);
        try {
            jobRepository.deleteById(id);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new com.jobportal.exception.ServiceException("Failed to delete job with ID: " + id + ". Please try again later.", e);
        }
    }

    // For GraphQL resolvers
    public List<Job> findJobsByCompanyIds(List<Integer> companyIds) {
        return jobRepository.findByCompanyIdIn(companyIds);
    }

    public static void validatePageSize(int size) {
        if (size < 1 || size > 100) throw new ValidationException("Page size must be between 1 and 100");
    }

    private void validateFilters(String status, Integer minSalary, Integer maxSalary) {
        if (status != null && !java.util.Set.of("OPEN", "CLOSED", "DRAFT").contains(status)) throw new ValidationException("status must be OPEN, CLOSED, or DRAFT");
        if ((minSalary != null && minSalary < 0) || (maxSalary != null && maxSalary < 0)) throw new ValidationException("Salary values must be nonnegative");
        if (minSalary != null && maxSalary != null && minSalary > maxSalary) throw new ValidationException("Minimum salary must not exceed maximum salary");
    }

    private void validateCoordinates(double lat, double lon) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180)
            throw new ValidationException("Latitude must be between -90 and 90; longitude between -180 and 180");
    }

    private void validateJob(Job job) {
        if (job == null) {
            throw new ValidationException("Job data cannot be null");
        }
        if (job.getCompanyId() == null || job.getCompanyId() <= 0) {
            throw new ValidationException("Valid company ID is required for job posting");
        }
        if (job.getTitle() == null || job.getTitle().trim().isEmpty()) {
            throw new ValidationException("Job title is required and cannot be empty");
        }
        if (job.getTitle().length() < 3) {
            throw new ValidationException("Job title must be at least 3 characters long");
        }
        if (job.getDescription() == null || job.getDescription().trim().isEmpty()) {
            throw new ValidationException("Job description is required and cannot be empty");
        }
        if (job.getDescription().length() < 20) {
            throw new ValidationException("Job description must be at least 20 characters long");
        }
        if (job.getTitle().length() > 255) throw new ValidationException("Job title must not exceed 255 characters");
        for (String value : new String[]{job.getLocation(), job.getCategory(), job.getSalaryRange()}) {
            if (value != null && value.length() > 255) throw new ValidationException("Job text field exceeds 255 characters");
        }
        validateFilters(job.getStatus(), job.getMinSalary(), job.getMaxSalary());
        if (job.getEmploymentType() != null && !java.util.Set.of("FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP", "FREELANCE", "Full-time", "Part-time", "Contract", "Internship", "Freelance").contains(job.getEmploymentType()))
            throw new ValidationException("Unsupported employmentType");
        if ((job.getLatitude() == null) != (job.getLongitude() == null)) throw new ValidationException("Supply both latitude and longitude");
        if (job.getLatitude() != null) validateCoordinates(job.getLatitude(), job.getLongitude());
        if ((job.getStartsAt() == null) != (job.getEndsAt() == null)) throw new ValidationException("Supply both job time bounds");
        if (job.getStartsAt() != null && !job.getEndsAt().isAfter(job.getStartsAt())) throw new ValidationException("Job endsAt must be after startsAt");
    }
}
