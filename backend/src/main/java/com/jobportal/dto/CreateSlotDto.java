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
    @NotBlank @Schema(example="2027-01-10T10:00:00Z", description="ISO 8601 timestamp; offsets normalize to UTC, offset-free input means UTC.")
    private String startsAt;
    @NotBlank @Schema(example="2027-01-10T10:30:00Z", description="Must be later than startsAt.")
    private String endsAt;
}
