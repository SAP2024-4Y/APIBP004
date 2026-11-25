package com.jobportal.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CreateApplicationDto {
    @Null(message = "must not be supplied") @Schema(hidden=true)
    private Integer id;
    @Min(1) @Schema(description="Derived from CANDIDATE JWT if omitted. Only CANDIDATE may submit applications.")
    private Integer candidateId;
    @NotNull @Min(1)
    private Integer jobId;
}
