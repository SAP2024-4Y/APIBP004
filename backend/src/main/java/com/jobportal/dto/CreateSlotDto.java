package com.jobportal.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CreateSlotDto {
    @NotNull @Min(1)
    private Integer jobId;
    @Min(1) @Schema(description="Candidate profile ID, not user ID. Derived for CANDIDATE callers.")
    private Integer candidateId;
    @NotBlank @Schema(example="2027-01-10T10:00:00", description="ISO local date-time; trailing Z is accepted. Offset conversion is not implemented.")
    private String startsAt;
    @NotBlank @Schema(example="2027-01-10T10:30:00", description="Must be later than startsAt.")
    private String endsAt;
}
