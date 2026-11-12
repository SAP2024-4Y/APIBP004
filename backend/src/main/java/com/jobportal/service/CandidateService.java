package com.jobportal.service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import com.jobportal.entity.Candidate;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.exception.ValidationException;
import com.jobportal.repository.CandidateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CandidateService {

    @Autowired
    private CandidateRepository candidateRepository;

    @Cacheable(value = "candidates")
    public List<Candidate> getAllCandidates() {
        return candidateRepository.findAll();
    }

    @Cacheable(value = "candidates", key = "#id")
    public Candidate getCandidateById(Integer id) {
        if (id == null || id <= 0) {
            throw new ValidationException("Candidate ID must be a positive number");
        }
        return candidateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Candidate not found with ID: " + id + ". Please verify the candidate ID."));
    }

    public Candidate getCandidateByUserId(Integer userId) {
        if (userId == null || userId <= 0) {
            throw new ValidationException("User ID must be a positive number");
        }
        
        Candidate candidate = candidateRepository.findByUserId(userId);
        if (candidate == null) {
            throw new ResourceNotFoundException(
                "No candidate profile found for user ID: " + userId + ". Please create a candidate profile first.");
        }
        return candidate;
    }

    @CacheEvict(value = "candidates", allEntries = true)
    public Candidate saveCandidate(Candidate candidate) {
        validateCandidate(candidate);
        
        try {
            return candidateRepository.save(candidate);
        } catch (Exception e) {
            throw new com.jobportal.exception.ServiceException("Failed to save candidate profile. Please try again later.", e);
        }
    }

    @CacheEvict(value = "candidates", allEntries = true)
    public void deleteCandidate(Integer id) {
        if (id == null || id <= 0) {
            throw new ValidationException("Candidate ID must be a positive number");
        }
        
        getCandidateById(id); // This will throw ResourceNotFoundException if not found
        
        try {
            candidateRepository.deleteById(id);
        } catch (Exception e) {
            throw new com.jobportal.exception.ServiceException("Failed to delete candidate with ID: " + id + ". Please try again later.", e);
        }
    }
    
    private void validateCandidate(Candidate candidate) {
        if (candidate == null) {
            throw new ValidationException("Candidate data cannot be null");
        }
        if (candidate.getUserId() == null || candidate.getUserId() <= 0) {
            throw new ValidationException("Valid user ID is required for candidate profile");
        }
        if (candidate.getFullName() == null || candidate.getFullName().trim().isEmpty()) {
            throw new ValidationException("Full name is required and cannot be empty");
        }
        if (candidate.getFullName().length() < 2) {
            throw new ValidationException("Full name must be at least 2 characters long");
        }
        if (candidate.getPhone() != null && !candidate.getPhone().trim().isEmpty()) {
            String phone = candidate.getPhone().replaceAll("[^0-9]", "");
            if (phone.length() < 10) {
                throw new ValidationException("Phone number must be at least 10 digits long");
            }
        }
    }
}