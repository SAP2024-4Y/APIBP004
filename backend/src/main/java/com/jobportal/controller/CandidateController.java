package com.jobportal.controller;

import com.jobportal.entity.Candidate;
import com.jobportal.service.CandidateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/candidates")
@CrossOrigin(origins = "*")
public class CandidateController {

    @Autowired
    private CandidateService candidateService;

    @GetMapping
    public List<Candidate> getAllCandidates() {
        return candidateService.getAllCandidates();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Candidate> getCandidateById(@PathVariable Integer id) {
        Candidate candidate = candidateService.getCandidateById(id);
        if (candidate != null) {
            return ResponseEntity.ok(candidate);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Candidate> getCandidateByUserId(@PathVariable Integer userId) {
        Candidate candidate = candidateService.getCandidateByUserId(userId);
        if (candidate != null) {
            return ResponseEntity.ok(candidate);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public Candidate createCandidate(@RequestBody Candidate candidate) {
        return candidateService.saveCandidate(candidate);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Candidate> updateCandidate(@PathVariable Integer id, @RequestBody Candidate candidateDetails) {
        Candidate candidate = candidateService.getCandidateById(id);
        if (candidate != null) {
            candidate.setUserId(candidateDetails.getUserId());
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
        Candidate candidate = candidateService.getCandidateById(id);
        if (candidate != null) {
            candidateService.deleteCandidate(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}