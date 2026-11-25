package com.jobportal.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CreateCompanyDto {
    @Null(message = "must not be supplied") @Schema(hidden=true)
    private Integer id;
    @Min(1) @Schema(description = "Optional for the matching role: derived from JWT. ADMIN must supply a user ID. Ownership cannot be changed.")
    private Integer userId;
    @NotBlank @Size(min=2, max=255)
    private String companyName;
    @Size(max=255)
    private String industry;
    @Size(max=255)
    private String address;
    @Schema(description="Only ADMIN may set approval. COMPANY creation always starts unapproved.")
    private Boolean approved;
}
