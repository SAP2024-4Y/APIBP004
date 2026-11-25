package com.jobportal.service;

import com.jobportal.entity.Job;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.exception.ValidationException;
import com.jobportal.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobService jobService;

    private Job testJob;

    @BeforeEach
    void setUp() {
        testJob = new Job();
        testJob.setId(1);
        testJob.setCompanyId(1);
        testJob.setTitle("Software Engineer");
        testJob.setDescription("Exciting opportunity for software engineers");
        testJob.setSkillsRequired("Java, Spring Boot, PostgreSQL");
        testJob.setSalaryRange("$80,000 - $100,000");
        testJob.setLocation("San Francisco, CA");
        testJob.setEmploymentType("Full-time");
        testJob.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void getAllJobs_ShouldReturnAllJobs() {
        // Arrange
        List<Job> jobs = Arrays.asList(testJob);
        when(jobRepository.findAll()).thenReturn(jobs);

        // Act
        List<Job> result = jobService.getAllJobs();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testJob.getTitle(), result.get(0).getTitle());
        verify(jobRepository, times(1)).findAll();
    }

    @Test
    void getJobById_WithValidId_ShouldReturnJob() {
        // Arrange
        when(jobRepository.findById(1)).thenReturn(Optional.of(testJob));

        // Act
        Job result = jobService.getJobById(1);

        // Assert
        assertNotNull(result);
        assertEquals(testJob.getTitle(), result.getTitle());
        verify(jobRepository, times(1)).findById(1);
    }

    @Test
    void getJobById_WithInvalidId_ShouldThrowException() {
        // Arrange
        when(jobRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            jobService.getJobById(999);
        });
        verify(jobRepository, times(1)).findById(999);
    }

    @Test
    void getJobById_WithNullId_ShouldThrowValidationException() {
        // Act & Assert
        assertThrows(ValidationException.class, () -> {
            jobService.getJobById(null);
        });
        verify(jobRepository, never()).findById(any());
    }

    @Test
    void getJobById_WithNegativeId_ShouldThrowValidationException() {
        // Act & Assert
        assertThrows(ValidationException.class, () -> {
            jobService.getJobById(-1);
        });
        verify(jobRepository, never()).findById(any());
    }

    @Test
    void getJobsByCompanyId_ShouldReturnCompanyJobs() {
        // Arrange
        List<Job> jobs = Arrays.asList(testJob);
        when(jobRepository.findByCompanyId(1)).thenReturn(jobs);

        // Act
        List<Job> result = jobService.getJobsByCompanyId(1);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getCompanyId());
        verify(jobRepository, times(1)).findByCompanyId(1);
    }

    @Test
    void searchJobsByLocation_WithValidLocation_ShouldReturnJobs() {
        // Arrange
        List<Job> jobs = Arrays.asList(testJob);
        when(jobRepository.findByLocationContainingIgnoreCase("San Francisco")).thenReturn(jobs);

        // Act
        List<Job> result = jobService.searchJobsByLocation("San Francisco");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(jobRepository, times(1)).findByLocationContainingIgnoreCase("San Francisco");
    }

    @Test
    void searchJobsByLocation_WithEmptyString_ShouldThrowValidationException() {
        // Act & Assert
        assertThrows(ValidationException.class, () -> {
            jobService.searchJobsByLocation("");
        });
        verify(jobRepository, never()).findByLocationContainingIgnoreCase(any());
    }

    @Test
    void searchJobsByTitle_WithValidTitle_ShouldReturnJobs() {
        // Arrange
        List<Job> jobs = Arrays.asList(testJob);
        when(jobRepository.findByTitleContainingIgnoreCase("Engineer")).thenReturn(jobs);

        // Act
        List<Job> result = jobService.searchJobsByTitle("Engineer");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(jobRepository, times(1)).findByTitleContainingIgnoreCase("Engineer");
    }

    @Test
    void searchJobsWithPagination_ShouldReturnPagedResults() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Job> jobPage = new PageImpl<>(Arrays.asList(testJob));
        when(jobRepository.searchJobs(null, null, null, pageable)).thenReturn(jobPage);

        // Act
        Page<Job> result = jobService.searchJobsWithPagination(null, null, null, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(testJob.getTitle(), result.getContent().get(0).getTitle());
        verify(jobRepository, times(1)).searchJobs(null, null, null, pageable);
    }

    @Test
    void saveJob_WithValidJob_ShouldReturnSavedJob() {
        // Arrange
        when(jobRepository.save(any(Job.class))).thenReturn(testJob);

        // Act
        Job result = jobService.saveJob(testJob);

        // Assert
        assertNotNull(result);
        assertEquals(testJob.getTitle(), result.getTitle());
        verify(jobRepository, times(1)).save(testJob);
    }

    @Test
    void saveJob_WithNullJob_ShouldThrowValidationException() {
        // Act & Assert
        assertThrows(ValidationException.class, () -> {
            jobService.saveJob(null);
        });
        verify(jobRepository, never()).save(any());
    }

    @Test
    void saveJob_WithEmptyTitle_ShouldThrowValidationException() {
        // Arrange
        testJob.setTitle("");

        // Act & Assert
        assertThrows(ValidationException.class, () -> {
            jobService.saveJob(testJob);
        });
        verify(jobRepository, never()).save(any());
    }

    @Test
    void saveJob_WithShortTitle_ShouldThrowValidationException() {
        // Arrange
        testJob.setTitle("AB");

        // Act & Assert
        assertThrows(ValidationException.class, () -> {
            jobService.saveJob(testJob);
        });
        verify(jobRepository, never()).save(any());
    }

    @Test
    void saveJob_WithShortDescription_ShouldThrowValidationException() {
        // Arrange
        testJob.setDescription("Too short");

        // Act & Assert
        assertThrows(ValidationException.class, () -> {
            jobService.saveJob(testJob);
        });
        verify(jobRepository, never()).save(any());
    }

    @Test
    void deleteJob_WithValidId_ShouldDeleteJob() {
        // Arrange
        when(jobRepository.findById(1)).thenReturn(Optional.of(testJob));
        doNothing().when(jobRepository).deleteById(1);

        // Act
        jobService.deleteJob(1);

        // Assert
        verify(jobRepository, times(1)).findById(1);
        verify(jobRepository, times(1)).deleteById(1);
    }

    @Test
    void deleteJob_WithInvalidId_ShouldThrowResourceNotFoundException() {
        // Arrange
        when(jobRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            jobService.deleteJob(999);
        });
        verify(jobRepository, times(1)).findById(999);
        verify(jobRepository, never()).deleteById(any());
    }
}
