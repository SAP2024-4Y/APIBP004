package com.jobportal.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
public class CreateJobDto {
    @NotNull @Min(1)
    private Integer companyId;
    @NotBlank @Size(min=3, max=255)
    private String title;
    @NotBlank @Size(min=20)
    private String description;

    private String skillsRequired;
    @Size(max=255) @Schema(deprecated=true, description="Legacy display text. Numeric bounds are authoritative USD per year and regenerate this text when present.")
    private String salaryRange;
    @Size(max=255)
    private String location;
    @Pattern(regexp="FULL_TIME|PART_TIME|CONTRACT|INTERNSHIP|FREELANCE|Full-time|Part-time|Contract|Internship|Freelance")
    private String employmentType;
    @Size(max=255)
    private String category;
    @Pattern(regexp="OPEN|CLOSED|DRAFT") @Schema(allowableValues={"OPEN","CLOSED","DRAFT"}, description="Defaults OPEN on creation; omitted/null preserves current value on update.")
    private String status;
    @Min(0) @Schema(description="Whole USD per year, inclusive lower salary bound; null means unknown.")
    private Integer minSalary;
    @Min(0) @Schema(description="Whole USD per year, inclusive upper salary bound; must be at least minSalary.")
    private Integer maxSalary;
    @DecimalMin("-90") @DecimalMax("90")
    private Double latitude;
    @DecimalMin("-180") @DecimalMax("180")
    private Double longitude;
    @Schema(example="2027-02-01T09:00:00Z", description="Optional job-level UTC timestamp. Supply both time bounds on creation; does not create an interview slot.")
    private String startsAt;
    @Schema(example="2027-02-01T10:00:00Z", description="Optional job-level UTC timestamp strictly after startsAt.")
    private String endsAt;
}
