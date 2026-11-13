package com.jobportal.dto;

import lombok.Data;

@Data
public class CreateJobDto {
    private Integer companyId;
    private String title;
    private String description;
    private String skillsRequired;
    private String salaryRange;
    private String location;
    private String employmentType;
}