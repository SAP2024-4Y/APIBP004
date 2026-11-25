package com.jobportal.controller;

import org.springframework.security.access.AccessDeniedException;

import com.jobportal.dto.CreateJobDto;
import com.jobportal.dto.response.JobResponseDto;
import com.jobportal.entity.Job;
import com.jobportal.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import com.jobportal.security.ResourceAuthorization;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Jobs", description = "Job management API - Browse, create, update, and delete job postings")
@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/v1/jobs")
@CrossOrigin(origins = "*")
public class JobController {

    @Autowired
    private ResourceAuthorization access;

    @Autowired
    private JobService jobService;

    @Operation(
        summary = "List jobs with cursor-based pagination and filtering",
        description = "Returns jobs with optional filters. Supports cursor-based pagination via 'limit' and 'after'. " +
                      "Exact filter params: status, category, min_price, max_price, sort, order."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved jobs"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> getAllJobs(
            @Parameter(description = "Filter by job status (OPEN, CLOSED, DRAFT)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by category (e.g. Technology, Finance)")
            @RequestParam(required = false) String category,
            @Parameter(description = "Minimum salary filter (min_price)")
            @RequestParam(required = false) Integer min_price,
            @Parameter(description = "Maximum salary filter (max_price)")
            @RequestParam(required = false) Integer max_price,
            @Parameter(description = "Sort field: title, created_at, min_price, max_price")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Sort order: asc or desc (default: desc)")
            @RequestParam(required = false) String order,
            @Parameter(description = "Number of items per page (default 20)")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Opaque cursor for next page (from previous response's next_cursor)")
            @RequestParam(required = false) String after,
            // Legacy params — kept for backward compatibility
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String skills
    ) {
        // If legacy params are used without new params, fall back to simple list
        if ((status == null && category == null && min_price == null && max_price == null && after == null)
                && (title != null || location != null || skills != null)) {
            List<JobResponseDto> jobs;
            if (title != null) {
                jobs = jobService.searchJobsByTitle(title).stream().map(JobResponseDto::from).collect(Collectors.toList());
            } else if (location != null) {
                jobs = jobService.searchJobsByLocation(location).stream().map(JobResponseDto::from).collect(Collectors.toList());
            } else {
                jobs = jobService.getAllJobs().stream().map(JobResponseDto::from).collect(Collectors.toList());
            }
            return ResponseEntity.ok(jobs);
        }

        Map<String, Object> result = jobService.findWithCursorPagination(
            status, category, min_price, max_price, after, limit,
            sort != null ? sort : "created_at",
            order != null ? order : "desc"
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get job by ID")
    public ResponseEntity<JobResponseDto> getJobById(@jakarta.validation.constraints.Positive @PathVariable Integer id) {
        Job job = jobService.getJobById(id);
        return ResponseEntity.ok(JobResponseDto.from(job));
    }

    @GetMapping("/company/{companyId}")
    @Operation(summary = "Get jobs by company")
    public List<JobResponseDto> getJobsByCompanyId(@jakarta.validation.constraints.Positive @PathVariable Integer companyId) {
        return jobService.getJobsByCompanyId(companyId).stream()
            .map(JobResponseDto::from)
            .collect(Collectors.toList());
    }

    @Deprecated
    @GetMapping("/search/title")
    public List<Job> searchJobsByTitle(@RequestParam String title) {
        return jobService.searchJobsByTitle(title);
    }

    @Deprecated
    @GetMapping("/search/location")
    public List<Job> searchJobsByLocation(@RequestParam String location) {
        return jobService.searchJobsByLocation(location);
    }

    @Operation(summary = "Create a new job posting",
               description = "Requires the owning COMPANY or ADMIN. COMPANY callers must own the supplied companyId.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Job created successfully",
                    content = @Content(schema = @Schema(implementation = JobResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid job data", content = @Content),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content)
    })
    @PreAuthorize("hasAnyRole('COMPANY', 'ADMIN')")
    @PostMapping
    public ResponseEntity<JobResponseDto> createJob(@jakarta.validation.Valid @RequestBody CreateJobDto createJobDto) {
        access.requireCompanyOwner(createJobDto.getCompanyId());
        Job job = new Job();
        job.setCompanyId(createJobDto.getCompanyId());
        job.setTitle(createJobDto.getTitle());
        job.setDescription(createJobDto.getDescription());
        job.setSkillsRequired(createJobDto.getSkillsRequired());
        job.setSalaryRange(createJobDto.getSalaryRange());
        job.setLocation(createJobDto.getLocation());
        job.setEmploymentType(createJobDto.getEmploymentType());
        Job savedJob = jobService.saveJob(job);
        return ResponseEntity.status(HttpStatus.CREATED).body(JobResponseDto.from(savedJob));
    }

    @Operation(summary = "Update a job posting",
               description = "Requires the owning COMPANY or ADMIN. Job ownership cannot be reassigned.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    @PreAuthorize("hasAnyRole('COMPANY', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<JobResponseDto> updateJob(@jakarta.validation.constraints.Positive @PathVariable Integer id, @jakarta.validation.Valid @RequestBody com.jobportal.dto.UpdateJobDto jobDetails) {
        access.requireJobOwner(id);
        Job job = new Job();
        org.springframework.beans.BeanUtils.copyProperties(jobService.getJobById(id), job);
        if (jobDetails.getCompanyId() != null && !jobDetails.getCompanyId().equals(job.getCompanyId())) {
            throw new AccessDeniedException("Job ownership cannot be changed");
        }
        job.setTitle(jobDetails.getTitle());
        job.setDescription(jobDetails.getDescription());
        job.setSkillsRequired(jobDetails.getSkillsRequired());
        job.setSalaryRange(jobDetails.getSalaryRange());
        job.setLocation(jobDetails.getLocation());
        job.setEmploymentType(jobDetails.getEmploymentType());
        if (jobDetails.getCategory() != null) job.setCategory(jobDetails.getCategory());
        if (jobDetails.getStatus() != null) job.setStatus(jobDetails.getStatus());
        if (jobDetails.getMinSalary() != null) job.setMinSalary(jobDetails.getMinSalary());
        if (jobDetails.getMaxSalary() != null) job.setMaxSalary(jobDetails.getMaxSalary());
        return ResponseEntity.ok(JobResponseDto.from(jobService.saveJob(job)));
    }

    @Operation(summary = "Delete a job posting",
               description = "Requires the owning COMPANY or ADMIN.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    @PreAuthorize("hasAnyRole('COMPANY', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@jakarta.validation.constraints.Positive @PathVariable Integer id) {
        access.requireJobOwner(id);
        jobService.deleteJob(id);
        return ResponseEntity.noContent().build();
    }
}
