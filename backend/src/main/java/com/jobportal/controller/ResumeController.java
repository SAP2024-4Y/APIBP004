package com.jobportal.controller;

import com.jobportal.entity.Resume;
import com.jobportal.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import com.jobportal.security.ResourceAuthorization;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Resume", description = "Candidates manage their own resumes. Companies may read resumes of applicants to their jobs. Global listing requires ADMIN.")
@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/v1/resumes")
@CrossOrigin(origins = "*")
public class ResumeController {

    @Autowired
    private ResourceAuthorization access;

    @Autowired
    private ResumeService resumeService;

    @GetMapping
    public List<Resume> getAllResumes() {
        access.requireAdmin();
        return resumeService.getAllResumes();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resume> getResumeById(@jakarta.validation.constraints.Positive @PathVariable Integer id) {
        access.requireResumeAccess(id, false);
        Resume resume = resumeService.getResumeById(id);
        return ResponseEntity.ok(resume);
    }

    @GetMapping("/candidate/{candidateId}")
    public List<Resume> getResumesByCandidateId(@jakarta.validation.constraints.Positive @PathVariable Integer candidateId) {
        access.requireCandidateReader(candidateId);
        return resumeService.getResumesByCandidateId(candidateId);
    }

    @GetMapping("/candidate/{candidateId}/latest")
    public ResponseEntity<Resume> getLatestResumeByCandidateId(@jakarta.validation.constraints.Positive @PathVariable Integer candidateId) {
        access.requireCandidateReader(candidateId);
        Resume resume = resumeService.getLatestResumeByCandidateId(candidateId);
        return ResponseEntity.ok(resume);
    }

    @Operation(summary = "Upload a resume file for a candidate")
    @ApiResponse(responseCode = "201", description = "Resume uploaded successfully")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadResume(
            @Parameter(description = "Candidate ID", required = true)
            @jakarta.validation.constraints.Positive @RequestParam("candidateId") Integer candidateId,
            @Parameter(description = "Resume file (PDF, DOC, DOCX - max 5MB)", required = true)
            @RequestParam("file") MultipartFile file) {
        
        access.requireCandidateOwner(candidateId);
        Resume resume = resumeService.uploadResume(candidateId, file);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Resume uploaded successfully");
        response.put("resume", resume);
        response.put("success", true);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadResume(@jakarta.validation.constraints.Positive @PathVariable Integer id) {
        access.requireResumeAccess(id, false);
        Resource resource = resumeService.downloadResume(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment()
                        .filename(resource.getFilename()).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteResume(@jakarta.validation.constraints.Positive @PathVariable Integer id) {
        access.requireResumeAccess(id, true);
        resumeService.deleteResume(id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Resume deleted successfully");
        response.put("success", true);
        
        return ResponseEntity.ok(response);
    }
}