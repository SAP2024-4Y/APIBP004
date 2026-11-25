package com.jobportal.dto;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Data
public class CreateJobDto {
    @NotNull @Min(1)
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
}