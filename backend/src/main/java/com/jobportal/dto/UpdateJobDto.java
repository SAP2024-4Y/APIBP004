package com.jobportal.dto;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Data
public class UpdateJobDto {
    @Min(1)
    private Integer companyId;
    @NotBlank @Size(min=3, max=255)
    private String title;
    @NotBlank @Size(min=20)
    private String description;
    private String skillsRequired;
    @Size(max=255)
    private String salaryRange;
    @Size(max=255)
    private String location;
    @Size(max=255)
    @Pattern(regexp="FULL_TIME|PART_TIME|CONTRACT|INTERNSHIP|FREELANCE|Full-time|Part-time|Contract|Internship|Freelance")
    @Schema(allowableValues={"FULL_TIME","PART_TIME","CONTRACT","INTERNSHIP","FREELANCE","Full-time","Part-time","Contract","Internship","Freelance"})
    private String employmentType;
    @Size(max=255)
    private String category;
    @io.swagger.v3.oas.annotations.media.Schema(allowableValues={"OPEN","CLOSED","DRAFT"})
    @Pattern(regexp="OPEN|CLOSED|DRAFT")
    private String status;
    @Min(0)
    private Integer minSalary;
    @Min(0)
    private Integer maxSalary;
}
