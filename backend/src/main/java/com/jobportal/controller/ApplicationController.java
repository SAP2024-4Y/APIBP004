package com.jobportal.controller;

import java.time.LocalDateTime;

import com.jobportal.entity.Application;
import com.jobportal.dto.CreateApplicationDto;
import jakarta.validation.Valid;
import com.jobportal.service.ApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import com.jobportal.security.ResourceAuthorization;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Application", description = "Applications are private. Candidates submit and withdraw their own applications; the owning company or ADMIN reviews them. Global listing requires ADMIN.")
@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/v1/applications")
@CrossOrigin(origins = "*")
public class ApplicationController {

    @Autowired
    private ResourceAuthorization access;

    @Autowired
    private ApplicationService applicationService;

    @GetMapping
    public List<Application> getAllApplications() {
        access.requireAdmin();
        return applicationService.getAllApplications();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Application> getApplicationById(@jakarta.validation.constraints.Positive @PathVariable Integer id) {
        access.requireApplicationReader(id);
        Application application = applicationService.getApplicationById(id);
        return ResponseEntity.ok(application);
    }

    @GetMapping("/candidate/{candidateId}")
    public List<Application> getApplicationsByCandidateId(@jakarta.validation.constraints.Positive @PathVariable Integer candidateId) {
        access.requireCandidateOwner(candidateId);
        return applicationService.getApplicationsByCandidateId(candidateId);
    }

    @GetMapping("/job/{jobId}")
    public List<Application> getApplicationsByJobId(@jakarta.validation.constraints.Positive @PathVariable Integer jobId) {
        access.requireJobOwner(jobId);
        return applicationService.getApplicationsByJobId(jobId);
    }

    @PostMapping
    public ResponseEntity<Application> createApplication(@Valid @RequestBody CreateApplicationDto request) {
        Application application = new Application();
        application.setCandidateId(request.getCandidateId());
        application.setJobId(request.getJobId());

        application.setCandidateId(access.applicationCandidate(application.getCandidateId()));
        application.setStatus(Application.ApplicationStatus.SUBMITTED);
        application.setAppliedAt(LocalDateTime.now());
        Application savedApplication = applicationService.saveApplication(application);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedApplication); // Return 201
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Application> updateApplicationStatus(@jakarta.validation.constraints.Positive @PathVariable Integer id, @RequestBody Application.ApplicationStatus status) {
        access.requireApplicationReviewer(id);
        Application updatedApplication = applicationService.updateApplicationStatus(id, status);
        return ResponseEntity.ok(updatedApplication);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@jakarta.validation.constraints.Positive @PathVariable Integer id) {
        access.requireApplicationOwner(id);
        applicationService.deleteApplication(id);
        return ResponseEntity.noContent().build(); // Return 204
    }
}