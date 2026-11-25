package com.jobportal.controller;

import com.jobportal.entity.User.Role;
import com.jobportal.exception.ValidationException;
import org.springframework.security.access.AccessDeniedException;

import com.jobportal.entity.Candidate;
import com.jobportal.service.CandidateService;
import org.springframework.beans.factory.annotation.Autowired;
import com.jobportal.security.ResourceAuthorization;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Candidate", description = "Candidates manage their own profile. Companies may read candidates who applied to their jobs. ADMIN may manage all profiles and list candidates.")
@RestController
@RequestMapping("/api/v1/candidates")
@CrossOrigin(origins = "*")
public class CandidateController {

    @Autowired
    private ResourceAuthorization access;

    @Autowired
    private CandidateService candidateService;

    @GetMapping
    public List<Candidate> getAllCandidates() {
        access.requireAdmin();
        return candidateService.getAllCandidates();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Candidate> getCandidateById(@PathVariable Integer id) {
        access.requireCandidateReader(id);
        Candidate candidate = candidateService.getCandidateById(id);
        if (candidate != null) {
            return ResponseEntity.ok(candidate);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Candidate> getCandidateByUserId(@PathVariable Integer userId) {
        access.requireCurrentUser(userId);
        Candidate candidate = candidateService.getCandidateByUserId(userId);
        if (candidate != null) {
            return ResponseEntity.ok(candidate);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public Candidate createCandidate(@RequestBody Candidate candidate) {
        if (candidate.getId() != null) throw new ValidationException("New candidate must not specify an ID");
        candidate.setUserId(access.profileOwner(candidate.getUserId(), Role.CANDIDATE));
        return candidateService.saveCandidate(candidate);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Candidate> updateCandidate(@PathVariable Integer id, @RequestBody Candidate candidateDetails) {
        access.requireCandidateOwner(id);
        Candidate candidate = candidateService.getCandidateById(id);
        if (candidate != null) {
            if (candidateDetails.getUserId() != null && !candidateDetails.getUserId().equals(candidate.getUserId())) {
                throw new AccessDeniedException("Candidate ownership cannot be changed");
            }
            candidate.setFullName(candidateDetails.getFullName());
            candidate.setPhone(candidateDetails.getPhone());
            candidate.setLocation(candidateDetails.getLocation());
            candidate.setExperience(candidateDetails.getExperience());
            return ResponseEntity.ok(candidateService.saveCandidate(candidate));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCandidate(@PathVariable Integer id) {
        access.requireCandidateOwner(id);
        Candidate candidate = candidateService.getCandidateById(id);
        if (candidate != null) {
            candidateService.deleteCandidate(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
