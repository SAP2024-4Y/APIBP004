package com.jobportal.controller;

import com.jobportal.entity.Job;
import com.jobportal.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "Map", description = "Geospatial job search - radius and bounding-box")
@RestController
@RequestMapping("/api/v1/jobs/map")
@CrossOrigin(origins = "*")
public class MapController {

    @Autowired
    private JobService jobService;

    @GetMapping
    @Operation(summary = "Search jobs by location",
               description = "Radius search: latitude, longitude, radius_km. " +
                             "Bounding-box: min_lat, min_lon, max_lat, max_lon. " +
                             "Returns GeoJSON FeatureCollection with coordinates=[longitude, latitude].")
    public ResponseEntity<Map<String, Object>> searchByLocation(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radius_km,
            @RequestParam(required = false) Double min_lat,
            @RequestParam(required = false) Double min_lon,
            @RequestParam(required = false) Double max_lat,
            @RequestParam(required = false) Double max_lon) {

        List<Job> jobs;

        if (latitude != null && longitude != null && radius_km != null) {
            jobs = jobService.findJobsWithinRadius(latitude, longitude, radius_km);
        } else if (min_lat != null && min_lon != null && max_lat != null && max_lon != null) {
            jobs = jobService.findJobsInBoundingBox(min_lat, min_lon, max_lat, max_lon);
        } else {
            jobs = jobService.getAllJobsWithCoordinates();
        }

        return ResponseEntity.ok(toGeoJsonFeatureCollection(jobs));
    }

    private Map<String, Object> toGeoJsonFeatureCollection(List<Job> jobs) {
        List<Map<String, Object>> features = new ArrayList<>();
        for (Job job : jobs) {
            if (job.getLatitude() == null || job.getLongitude() == null) continue;

            Map<String, Object> geometry = new LinkedHashMap<>();
            geometry.put("type", "Point");
            // GeoJSON convention: [longitude, latitude]
            geometry.put("coordinates", new double[]{job.getLongitude(), job.getLatitude()});

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("id", job.getId());
            properties.put("title", job.getTitle());
            properties.put("location", job.getLocation());
            properties.put("category", job.getCategory());
            properties.put("status", job.getStatus());
            properties.put("employmentType", job.getEmploymentType());
            properties.put("minSalary", job.getMinSalary());
            properties.put("maxSalary", job.getMaxSalary());
            properties.put("companyId", job.getCompanyId());

            Map<String, Object> feature = new LinkedHashMap<>();
            feature.put("type", "Feature");
            feature.put("geometry", geometry);
            feature.put("properties", properties);
            features.add(feature);
        }

        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("type", "FeatureCollection");
        collection.put("features", features);
        return collection;
    }
}
