package com.jobportal.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import com.jobportal.entity.Job;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.exception.ValidationException;
import com.jobportal.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
public class JobService {

    @Autowired
    private JobRepository jobRepository;

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
        if (title != null && !title.trim().isEmpty()) {
            return jobRepository.findByTitleContainingIgnoreCase(title, pageable);
        } else if (location != null && !location.trim().isEmpty()) {
            return jobRepository.findByLocationContainingIgnoreCase(location, pageable);
        } else {
            return jobRepository.findAll(pageable);
        }
    }

    /**
     * Cursor-based (keyset) pagination for the List API.
     * Returns a map with: data, has_more, next_cursor, total_returned.
     */
    public Map<String, Object> findWithCursorPagination(
            String status, String category, Integer minPrice, Integer maxPrice,
            String after, int limit, String sortField, String order) {

        validatePageSize(limit);
        validateFilters(status, minPrice, maxPrice);
        if (order != null && !java.util.Set.of("asc", "desc").contains(order.toLowerCase(java.util.Locale.ROOT))) throw new ValidationException("order must be asc or desc");
        Integer afterId = null;
        if (after != null && !after.isBlank()) {
            try {
                String decoded = new String(Base64.getDecoder().decode(after));
                afterId = Integer.parseInt(decoded);
                if (afterId <= 0) throw new ValidationException("Cursor ID must be positive");
            } catch (Exception e) {
                throw new ValidationException("Invalid cursor value in 'after' parameter");
            }
        }

        // Request one extra to determine has_more
        Sort sort = "asc".equalsIgnoreCase(order)
            ? Sort.by(Sort.Direction.ASC, mapSortField(sortField))
            : Sort.by(Sort.Direction.DESC, mapSortField(sortField));

        Pageable pageable = PageRequest.of(0, limit + 1, sort);
        List<Job> results = jobRepository.findWithFiltersAndCursor(
            status, category, minPrice, maxPrice, afterId, pageable
        );

        boolean hasMore = results.size() > limit;
        List<Job> page = hasMore ? results.subList(0, limit) : results;

        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            Job last = page.get(page.size() - 1);
            nextCursor = Base64.getEncoder().encodeToString(String.valueOf(last.getId()).getBytes());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", page);
        response.put("has_more", hasMore);
        response.put("next_cursor", nextCursor);
        response.put("limit", limit);
        return response;
    }

    private String mapSortField(String field) {
        if (field == null) return "createdAt";
        return switch (field.toLowerCase()) {
            case "title" -> "title";
            case "created_at", "createdat" -> "createdAt";
            case "min_price", "min_salary", "minsalary" -> "minSalary";
            case "max_price", "max_salary", "maxsalary" -> "maxSalary";
            default -> "createdAt";
        };
    }

    // --- Geospatial ---

    public List<Job> getAllJobsWithCoordinates() {
        return jobRepository.findAllWithCoordinates();
    }

    public List<Job> findJobsWithinRadius(double lat, double lon, double radiusKm) {
        validateCoordinates(lat, lon);
        if (!Double.isFinite(radiusKm) || radiusKm <= 0) throw new ValidationException("radius_km must be positive and finite");
        // Use bounding box first then filter with Haversine
        double latDelta = radiusKm / EARTH_RADIUS_KM * (180.0 / Math.PI);
        double lonDelta = latDelta / Math.cos(Math.toRadians(lat));

        List<Job> candidates = jobRepository.findByBoundingBox(
            lat - latDelta, lon - lonDelta, lat + latDelta, lon + lonDelta
        );

        return candidates.stream()
            .filter(j -> haversineKm(lat, lon, j.getLatitude(), j.getLongitude()) <= radiusKm)
            .collect(Collectors.toList());
    }

    public List<Job> findJobsInBoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
        validateCoordinates(minLat, minLon);
        validateCoordinates(maxLat, maxLon);
        if (minLat > maxLat || minLon > maxLon) throw new ValidationException("Bounding-box minimums must not exceed maximums");
        return jobRepository.findByBoundingBox(minLat, minLon, maxLat, maxLon);
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // --- Mutations ---

    @CacheEvict(value = "jobs", allEntries = true)
    public Job saveJob(Job job) {
        validateJob(job);
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

    public List<Job> findForGraphQL(String status, String category, Integer minPrice, Integer maxPrice, int first, Integer afterId) {
        validateFilters(status, minPrice, maxPrice);
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        Pageable pageable = PageRequest.of(0, first, sort);
        return jobRepository.findWithFiltersAndCursor(status, category, minPrice, maxPrice, afterId, pageable);
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
    }
}
