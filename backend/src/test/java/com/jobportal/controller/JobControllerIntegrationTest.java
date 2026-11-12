package com.jobportal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.dto.CreateJobDto;
import com.jobportal.entity.Job;
import com.jobportal.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class JobControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobService jobService;

    private Job testJob;
    private CreateJobDto createJobDto;

    @BeforeEach
    void setUp() {
        testJob = new Job();
        testJob.setId(1);
        testJob.setCompanyId(1);
        testJob.setTitle("Software Engineer");
        testJob.setDescription("Exciting opportunity for software engineers to work on cutting-edge projects");
        testJob.setSkillsRequired("Java, Spring Boot, PostgreSQL");
        testJob.setSalaryRange("$80,000 - $100,000");
        testJob.setLocation("San Francisco, CA");
        testJob.setEmploymentType("Full-time");
        testJob.setCreatedAt(LocalDateTime.now());
        testJob.setStatus("OPEN");

        createJobDto = new CreateJobDto();
        createJobDto.setCompanyId(1);
        createJobDto.setTitle("Software Engineer");
        createJobDto.setDescription("Exciting opportunity for software engineers to work on cutting-edge projects");
        createJobDto.setSkillsRequired("Java, Spring Boot, PostgreSQL");
        createJobDto.setSalaryRange("$80,000 - $100,000");
        createJobDto.setLocation("San Francisco, CA");
        createJobDto.setEmploymentType("Full-time");
    }

    @Test
    void getAllJobs_WithTitleParam_ShouldReturnJobList() throws Exception {
        List<Job> jobs = Arrays.asList(testJob);
        when(jobService.searchJobsByTitle("Engineer")).thenReturn(jobs);

        mockMvc.perform(get("/api/v1/jobs")
                .param("title", "Engineer")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Software Engineer"));

        verify(jobService, times(1)).searchJobsByTitle("Engineer");
    }

    @Test
    void getAllJobs_WithCursorPagination_ShouldReturnPagedResponse() throws Exception {
        Map<String, Object> cursorResult = new LinkedHashMap<>();
        cursorResult.put("data", Arrays.asList(testJob));
        cursorResult.put("has_more", false);
        cursorResult.put("next_cursor", null);
        cursorResult.put("limit", 20);

        when(jobService.findWithCursorPagination(eq("OPEN"), isNull(), isNull(), isNull(),
                isNull(), eq(20), any(), any())).thenReturn(cursorResult);

        mockMvc.perform(get("/api/v1/jobs")
                .param("status", "OPEN")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.has_more").value(false));
    }

    @Test
    void getJobById_WithValidId_ShouldReturnJob() throws Exception {
        when(jobService.getJobById(1)).thenReturn(testJob);

        mockMvc.perform(get("/api/v1/jobs/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Software Engineer"))
                .andExpect(jsonPath("$.location").value("San Francisco, CA"));

        verify(jobService, times(1)).getJobById(1);
    }

    @Test
    void getJobsByCompanyId_ShouldReturnCompanyJobs() throws Exception {
        List<Job> jobs = Arrays.asList(testJob);
        when(jobService.getJobsByCompanyId(1)).thenReturn(jobs);

        mockMvc.perform(get("/api/v1/jobs/company/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].companyId").value(1));

        verify(jobService, times(1)).getJobsByCompanyId(1);
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void createJob_WithValidData_ShouldReturnCreatedJob() throws Exception {
        when(jobService.saveJob(any(Job.class))).thenReturn(testJob);

        mockMvc.perform(post("/api/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createJobDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Software Engineer"));

        verify(jobService, times(1)).saveJob(any(Job.class));
    }

    @Test
    void createJob_WithoutAuthentication_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createJobDto)))
                .andExpect(status().isForbidden());

        verify(jobService, never()).saveJob(any());
    }

    @Test
    @WithMockUser(roles = "CANDIDATE")
    void createJob_WithCandidateRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createJobDto)))
                .andExpect(status().isForbidden());

        verify(jobService, never()).saveJob(any());
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void updateJob_WithValidData_ShouldReturnUpdatedJob() throws Exception {
        when(jobService.getJobById(1)).thenReturn(testJob);
        when(jobService.saveJob(any(Job.class))).thenReturn(testJob);

        mockMvc.perform(put("/api/v1/jobs/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testJob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Software Engineer"));

        verify(jobService, times(1)).getJobById(1);
        verify(jobService, times(1)).saveJob(any(Job.class));
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void deleteJob_WithValidId_ShouldReturnNoContent() throws Exception {
        doNothing().when(jobService).deleteJob(1);

        mockMvc.perform(delete("/api/v1/jobs/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(jobService, times(1)).deleteJob(1);
    }

    @Test
    void deleteJob_WithoutAuthentication_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/jobs/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(jobService, never()).deleteJob(anyInt());
    }

    @Test
    @WithMockUser(roles = "CANDIDATE")
    void deleteJob_WithCandidateRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/jobs/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(jobService, never()).deleteJob(anyInt());
    }

    @Test
    void searchJobsByLocation_ShouldReturnFilteredJobs() throws Exception {
        List<Job> jobs = Arrays.asList(testJob);
        when(jobService.searchJobsByLocation("San Francisco")).thenReturn(jobs);

        mockMvc.perform(get("/api/v1/jobs")
                .param("location", "San Francisco")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].location").value("San Francisco, CA"));

        verify(jobService, times(1)).searchJobsByLocation("San Francisco");
    }

    @Test
    void searchJobsByTitle_ShouldReturnFilteredJobs() throws Exception {
        List<Job> jobs = Arrays.asList(testJob);
        when(jobService.searchJobsByTitle("Engineer")).thenReturn(jobs);

        mockMvc.perform(get("/api/v1/jobs")
                .param("title", "Engineer")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].title").value("Software Engineer"));

        verify(jobService, times(1)).searchJobsByTitle("Engineer");
    }
}
