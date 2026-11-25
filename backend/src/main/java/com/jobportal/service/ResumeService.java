package com.jobportal.service;

import com.jobportal.entity.Resume;
import com.jobportal.exception.*;
import com.jobportal.repository.ResumeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ResumeService {
    private final ResumeRepository resumeRepository;
    private final CandidateService candidateService;
    private final Path uploadDir;

    public ResumeService(ResumeRepository resumeRepository, CandidateService candidateService,
            @Value("$" + "{app.resume.upload-dir:backend/src/main/resources/static/resumes}") String uploadDir) {
        this.resumeRepository = resumeRepository;
        this.candidateService = candidateService;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public List<Resume> getAllResumes() { return resumeRepository.findAll(); }

    public Resume getResumeById(Integer id) {
        if (id == null || id <= 0) throw new ValidationException("Resume ID must be positive");
        return resumeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found with id: " + id));
    }

    public List<Resume> getResumesByCandidateId(Integer candidateId) {
        candidateService.getCandidateById(candidateId);
        return resumeRepository.findByCandidateId(candidateId);
    }

    public Resume getLatestResumeByCandidateId(Integer candidateId) {
        candidateService.getCandidateById(candidateId);
        Resume resume = resumeRepository.findTopByCandidateIdOrderByUploadedAtDesc(candidateId);
        if (resume == null) throw new ResourceNotFoundException("No resume found for candidate with id: " + candidateId);
        return resume;
    }

    public Resume uploadResume(Integer candidateId, MultipartFile file) {
        candidateService.getCandidateById(candidateId);
        if (file == null || file.isEmpty()) throw new ValidationException("Resume file must not be empty");
        if (file.getSize() > 5 * 1024 * 1024) throw new PayloadTooLargeException("Resume exceeds 5 MiB");
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (extension == null || !Set.of("pdf", "doc", "docx").contains(extension.toLowerCase(Locale.ROOT))) {
            throw new ValidationException("Only PDF, DOC, and DOCX filename extensions are accepted");
        }
        String filename = "resume_" + candidateId + "_" + UUID.randomUUID() + "." + extension.toLowerCase(Locale.ROOT);
        Path filePath = uploadDir.resolve(filename);
        try {
            Files.createDirectories(uploadDir);
            try (InputStream input = file.getInputStream()) { Files.copy(input, filePath); }
            Resume resume = new Resume();
            resume.setCandidateId(candidateId);
            resume.setFilePath("/static/resumes/" + filename);
            // Flush before returning so a rejected record does not leave an orphaned file.
            return resumeRepository.saveAndFlush(resume);
        } catch (IOException | RuntimeException ex) {
            try { Files.deleteIfExists(filePath); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new ServiceException("Failed to store resume", ex);
        }
    }

    private Path storedPath(Resume resume) {
        String filename = Paths.get(resume.getFilePath()).getFileName().toString();
        Path path = uploadDir.resolve(filename).normalize();
        if (!path.startsWith(uploadDir)) throw new ServiceException("Invalid resume storage path");
        return path;
    }

    public Resource downloadResume(Integer id) {
        Path path = storedPath(getResumeById(id));
        if (!Files.isRegularFile(path)) throw new ResourceNotFoundException("Resume file was not found");
        if (!Files.isReadable(path)) throw new ServiceException("Resume file could not be read");
        return new FileSystemResource(path);
    }

    public void deleteResume(Integer id) {
        Resume resume = getResumeById(id);
        try { Files.deleteIfExists(storedPath(resume)); }
        catch (IOException ex) { throw new ServiceException("Failed to delete resume file", ex); }
        resumeRepository.deleteById(id);
    }
}
