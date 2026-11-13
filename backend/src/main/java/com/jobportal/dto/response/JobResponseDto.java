package com.jobportal.dto.response;

import com.jobportal.entity.Job;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobResponseDto {
    private Integer id;
    private Integer companyId;
    private String title;
    private String description;
    private String skillsRequired;
    private String salaryRange;
    private String location;
    private String employmentType;
    private LocalDateTime createdAt;
    private Double latitude;
    private Double longitude;
    private Integer minSalary;
    private Integer maxSalary;
    private String category;
    private String status;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;

    public static JobResponseDto from(Job job) {
        if (job == null) return null;
        JobResponseDto dto = new JobResponseDto();
        dto.setId(job.getId());
        dto.setCompanyId(job.getCompanyId());
        dto.setTitle(job.getTitle());
        dto.setDescription(job.getDescription());
        dto.setSkillsRequired(job.getSkillsRequired());
        dto.setSalaryRange(job.getSalaryRange());
        dto.setLocation(job.getLocation());
        dto.setEmploymentType(job.getEmploymentType());
        dto.setCreatedAt(job.getCreatedAt());
        dto.setLatitude(job.getLatitude());
        dto.setLongitude(job.getLongitude());
        dto.setMinSalary(job.getMinSalary());
        dto.setMaxSalary(job.getMaxSalary());
        dto.setCategory(job.getCategory());
        dto.setStatus(job.getStatus());
        dto.setStartsAt(job.getStartsAt());
        dto.setEndsAt(job.getEndsAt());
        return dto;
    }
}
