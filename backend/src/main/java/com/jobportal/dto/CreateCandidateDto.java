package com.jobportal.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CreateCandidateDto {
    @Null(message = "must not be supplied") @Schema(hidden=true)
    private Integer id;
    @Min(1) @Schema(description = "Optional for the matching role: derived from JWT. ADMIN must supply a user ID. Ownership cannot be changed.")
    private Integer userId;
    @NotBlank @Size(min=2, max=255)
    private String fullName;
    @Size(max=255) @Pattern(regexp="^$|(?:[^0-9]*[0-9]){10,}[^0-9]*$", message="must contain at least 10 digits or be empty")
    private String phone;
    @Size(max=255)
    private String location;
    @Size(max=255)
    private String experience;
}
