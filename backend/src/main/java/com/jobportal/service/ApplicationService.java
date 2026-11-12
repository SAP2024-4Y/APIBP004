package com.jobportal.service;

import com.jobportal.entity.Application;
import com.jobportal.entity.Candidate;
import com.jobportal.entity.Company;
import com.jobportal.entity.Job;
import com.jobportal.entity.User;
import com.jobportal.exception.DuplicateResourceException;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.exception.ServiceException;
import com.jobportal.exception.ValidationException;
import com.jobportal.repository.ApplicationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Transactional
public class ApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationService.class);

    @Autowired
    private ApplicationRepository applicationRepository;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private CandidateService candidateService;
    
    @Autowired
    private JobService jobService;
    
    @Autowired
    private CompanyService companyService;
    
    @Autowired
    private UserService userService;

    public List<Application> getAllApplications() {
        return applicationRepository.findAll();
    }

    public Application getApplicationById(Integer id) {
        if (id == null || id <= 0) {
            throw new ValidationException("Application ID must be a positive number");
        }
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Application not found with ID: " + id + ". The application may have been withdrawn or the ID is incorrect."));
    }

    public List<Application> getApplicationsByCandidateId(Integer candidateId) {
        if (candidateId == null || candidateId <= 0) {
            throw new ValidationException("Candidate ID must be a positive number");
        }
        
        return applicationRepository.findByCandidateId(candidateId);
    }

    public List<Application> getApplicationsByJobId(Integer jobId) {
        if (jobId == null || jobId <= 0) {
            throw new ValidationException("Job ID must be a positive number");
        }
        
        return applicationRepository.findByJobId(jobId);
    }

    public Application getApplicationByCandidateAndJob(Integer candidateId, Integer jobId) {
        if (candidateId == null || candidateId <= 0) {
            throw new ValidationException("Candidate ID must be a positive number");
        }
        if (jobId == null || jobId <= 0) {
            throw new ValidationException("Job ID must be a positive number");
        }
        
        Application application = applicationRepository.findByCandidateIdAndJobId(candidateId, jobId);
        if (application == null) {
            throw new ResourceNotFoundException(
                "No application found for candidate ID: " + candidateId + " and job ID: " + jobId);
        }
        return application;
    }

    public Application saveApplication(Application application) {
        if (application.getId() == null) {
            // Check for duplicate applications
            Application existingApplication = applicationRepository.findByCandidateIdAndJobId(
                    application.getCandidateId(), application.getJobId());
            if (existingApplication != null) {
                throw new DuplicateResourceException(
                        "Application already exists for candidate ID: " + application.getCandidateId() +
                        " and job ID: " + application.getJobId() + ". Duplicate applications are not allowed.");
            }
            
            // Save new application
            Application savedApplication = applicationRepository.save(application);
            
            // Send email notifications for new applications
            sendApplicationEmails(savedApplication);
            
            logger.info("New application created with ID: {} for candidate: {} and job: {}", 
                       savedApplication.getId(), application.getCandidateId(), application.getJobId());
            return savedApplication;
        }
        
        // Update existing application
        return applicationRepository.save(application);
    }

    public void deleteApplication(Integer id) {
        if (id == null || id <= 0) {
            throw new ValidationException("Application ID must be a positive number");
        }
        
        getApplicationById(id); // This will throw ResourceNotFoundException if not found
        
        try {
            applicationRepository.deleteById(id);
        } catch (Exception e) {
            throw new com.jobportal.exception.ServiceException("Failed to delete application with ID: " + id + ". Please try again later.", e);
        }
    }

    private void validateApplication(Application application) {
        if (application == null) {
            throw new ValidationException("Application data cannot be null");
        }
        if (application.getCandidateId() == null || application.getCandidateId() <= 0) {
            throw new ValidationException("Valid candidate ID is required for job application");
        }
        if (application.getJobId() == null || application.getJobId() <= 0) {
            throw new ValidationException("Valid job ID is required for job application");
        }
    }

    public Application updateApplicationStatus(Integer id, Application.ApplicationStatus status) {
        Application application = getApplicationById(id);
        Application.ApplicationStatus oldStatus = application.getStatus();
        application.setStatus(status);
        Application savedApplication = applicationRepository.save(application);
        
        // Send status change notification asynchronously if status changed
        if (!oldStatus.equals(status)) {
            // Send email asynchronously to avoid blocking the response
            CompletableFuture.runAsync(() -> sendStatusChangeEmail(savedApplication, oldStatus, status));
        }
        
        return savedApplication;
    }

    private void sendApplicationEmails(Application application) {
        // Send emails asynchronously to avoid blocking the main transaction
        CompletableFuture.runAsync(() -> {
            try {
                // Get candidate and job details
                Candidate candidate = candidateService.getCandidateById(application.getCandidateId());
                Job job = jobService.getJobById(application.getJobId());
                Company company = companyService.getCompanyById(job.getCompanyId());
                User candidateUser = userService.getUserById(candidate.getUserId());
                User companyUser = userService.getUserById(company.getUserId());

                if (candidateUser != null && companyUser != null && candidateUser.getEmail() != null && companyUser.getEmail() != null) {
                    try {
                        // Send email to candidate
                        emailService.sendApplicationSubmittedEmail(
                            candidateUser.getEmail(),
                            candidate.getFullName(),
                            job.getTitle(),
                            company.getCompanyName()
                        );
                        logger.info("Application submitted email sent to: {}", candidateUser.getEmail());
                    } catch (Exception e) {
                        logger.warn("Failed to send application submitted email to {}: {}", 
                            candidateUser.getEmail(), e.getMessage());
                    }

                    try {
                        // Send email to company
                        emailService.sendApplicationReceivedEmail(
                            companyUser.getEmail(),
                            company.getCompanyName(),
                            candidate.getFullName(),
                            job.getTitle()
                        );
                        logger.info("Application received email sent to: {}", companyUser.getEmail());
                    } catch (Exception e) {
                        logger.warn("Failed to send application received email to {}: {}", 
                            companyUser.getEmail(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                // Log error but don't fail the application creation
                logger.error("Failed to send application emails: " + e.getMessage(), e);
            }
        });
    }

    private void sendStatusChangeEmail(Application application, Application.ApplicationStatus oldStatus, Application.ApplicationStatus newStatus) {
        try {
            // Get candidate and job details
            Candidate candidate = candidateService.getCandidateById(application.getCandidateId());
            Job job = jobService.getJobById(application.getJobId());
            Company company = companyService.getCompanyById(job.getCompanyId());
            User candidateUser = userService.getUserById(candidate.getUserId());

            if (candidateUser != null) {
                emailService.sendApplicationStatusChangeEmail(
                    candidateUser.getEmail(),
                    candidate.getFullName(),
                    job.getTitle(),
                    company.getCompanyName(),
                    oldStatus.toString(),
                    newStatus.toString()
                );
            }
        } catch (Exception e) {
            // Log error but don't fail the status update
            logger.error("Failed to send status change email: " + e.getMessage(), e);
        }
    }
}