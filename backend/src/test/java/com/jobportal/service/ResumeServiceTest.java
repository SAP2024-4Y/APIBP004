package com.jobportal.service;

import com.jobportal.entity.Candidate;
import com.jobportal.entity.Resume;
import com.jobportal.exception.*;
import com.jobportal.repository.ResumeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResumeServiceTest {
    @TempDir Path directory;
    ResumeRepository repository=mock(ResumeRepository.class);
    CandidateService candidates=mock(CandidateService.class);

    @Test void databaseFailureRemovesTheNewFile() throws Exception {
        when(candidates.getCandidateById(1)).thenReturn(new Candidate());
        when(repository.saveAndFlush(any(Resume.class))).thenThrow(new DataIntegrityViolationException("test constraint"));
        ResumeService service=new ResumeService(repository,candidates,directory.toString());
        assertThrows(DataIntegrityViolationException.class,()->service.uploadResume(1,new MockMultipartFile("file","resume.pdf","application/pdf",new byte[]{1})));
        try(var files=Files.list(directory)) {assertEquals(0,files.count());}
    }

    @Test void nonexistentCandidateDoesNotCreateAFile() throws Exception {
        when(candidates.getCandidateById(99)).thenThrow(new ResourceNotFoundException("Candidate not found"));
        ResumeService service=new ResumeService(repository,candidates,directory.toString());
        assertThrows(ResourceNotFoundException.class,()->service.uploadResume(99,new MockMultipartFile("file","resume.docx","application/octet-stream",new byte[]{1})));
        verifyNoInteractions(repository);
        try(var files=Files.list(directory)) {assertEquals(0,files.count());}
    }

    @Test void storageFailureIsReportedWithoutSavingMetadata() throws Exception {
        Path blocked=directory.resolve("file-not-directory"); Files.writeString(blocked,"blocked");
        ResumeService service=new ResumeService(repository,candidates,blocked.toString());
        assertThrows(ServiceException.class,()->service.uploadResume(1,new MockMultipartFile("file","resume.pdf","application/pdf",new byte[]{1})));
        verifyNoInteractions(repository);
    }
}
