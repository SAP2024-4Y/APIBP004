package com.jobportal.controller;

import com.jobportal.entity.Application;
import com.jobportal.service.ApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/applications")
@CrossOrigin(origins = "*")
public class ApplicationController {

    @Autowired
    private ApplicationService applicationService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<Application> getAllApplications() {
        return applicationService.getAllApplications();
    }

    @PreAuthorize("hasAnyRole('CANDIDATE', 'COMPANY', 'ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<Application> getApplicationById(@PathVariable Integer id) {
        // TODO: Add ownership check - candidates can only see their own, companies can see applications to their jobs
        Application application = applicationService.getApplicationById(id);
        return ResponseEntity.ok(application);
    }

    @PreAuthorize("hasAnyRole('CANDIDATE', 'ADMIN')")
    @GetMapping("/candidate/{candidateId}")
    public List<Application> getApplicationsByCandidateId(@PathVariable Integer candidateId) {
        // TODO: Add ownership check - candidates can only see their own applications
        return applicationService.getApplicationsByCandidateId(candidateId);
    }

    @PreAuthorize("hasAnyRole('COMPANY', 'ADMIN')")
    @GetMapping("/job/{jobId}")
    public List<Application> getApplicationsByJobId(@PathVariable Integer jobId) {
        // TODO: Add ownership check - companies can only see applications to their jobs
        return applicationService.getApplicationsByJobId(jobId);
    }

    @PreAuthorize("hasRole('CANDIDATE')")
    @PostMapping
    public ResponseEntity<Application> createApplication(@RequestBody Application application) {
        Application savedApplication = applicationService.saveApplication(application);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedApplication); // Return 201
    }

    @PreAuthorize("hasAnyRole('COMPANY', 'ADMIN')")
    @PutMapping("/{id}/status")
    public ResponseEntity<Application> updateApplicationStatus(@PathVariable Integer id, @RequestBody Application.ApplicationStatus status) {
        // TODO: Add ownership check - only company that owns the job can update status
        Application updatedApplication = applicationService.updateApplicationStatus(id, status);
        return ResponseEntity.ok(updatedApplication);
    }

    @PreAuthorize("hasAnyRole('CANDIDATE', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable Integer id) {
        // TODO: Add ownership check - only the candidate who created it can delete
        applicationService.deleteApplication(id);
        return ResponseEntity.noContent().build(); // Return 204
    }
}