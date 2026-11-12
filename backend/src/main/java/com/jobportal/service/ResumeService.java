package com.jobportal.service;

import com.jobportal.entity.Resume;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.exception.ServiceException;
import com.jobportal.repository.ResumeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class ResumeService {

    @Autowired
    private ResumeRepository resumeRepository;

    private final String uploadDir = "backend/src/main/resources/static/resumes/";

    public List<Resume> getAllResumes() {
        return resumeRepository.findAll();
    }

    public Resume getResumeById(Integer id) {
        return resumeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found with id: " + id));
    }

    public List<Resume> getResumesByCandidateId(Integer candidateId) {
        return resumeRepository.findByCandidateId(candidateId);
    }

    public Resume getLatestResumeByCandidateId(Integer candidateId) {
        Resume resume = resumeRepository.findTopByCandidateIdOrderByUploadedAtDesc(candidateId);
        if (resume == null) {
            throw new ResourceNotFoundException("No resume found for candidate with id: " + candidateId);
        }
        return resume;
    }

    public Resume uploadResume(Integer candidateId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new ServiceException("File is empty. Please select a valid file.");
        }

        // Validate file type
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !isValidFileType(originalFilename)) {
            throw new ServiceException("Invalid file type. Only PDF, DOC, and DOCX files are allowed.");
        }

        // Validate file size (5MB max)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new ServiceException("File size exceeds the maximum limit of 5MB.");
        }

        try {
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String fileExtension = getFileExtension(originalFilename);
            String uniqueFilename = "resume_" + candidateId + "_" + UUID.randomUUID().toString() + "." + fileExtension;
            Path filePath = uploadPath.resolve(uniqueFilename);

            // Copy file to upload directory
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Save resume record to database
            Resume resume = new Resume();
            resume.setCandidateId(candidateId);
            resume.setFilePath("/static/resumes/" + uniqueFilename);

            return resumeRepository.save(resume);

        } catch (IOException e) {
            throw new ServiceException("Failed to upload file: " + e.getMessage());
        }
    }

    public void deleteResume(Integer id) {
        Resume resume = getResumeById(id);
        
        // Delete file from filesystem
        try {
            String fileName = Paths.get(resume.getFilePath()).getFileName().toString();
            Path filePath = Paths.get(uploadDir + fileName);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
        } catch (IOException e) {
            // Log warning but don't fail the deletion
            System.err.println("Warning: Could not delete file: " + e.getMessage());
        }

        // Delete database record
        resumeRepository.deleteById(id);
    }

    private boolean isValidFileType(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        return extension.equals("pdf") || extension.equals("doc") || extension.equals("docx");
    }

    private String getFileExtension(String filename) {
        return StringUtils.getFilenameExtension(filename);
    }
}