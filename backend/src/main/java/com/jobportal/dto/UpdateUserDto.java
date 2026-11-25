package com.jobportal.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class UpdateUserDto {
    @NotBlank @Email @Size(max=255)
    private String email;
    @Size(min=6, max=72) @Schema(format="password", accessMode=Schema.AccessMode.WRITE_ONLY, description="Omit to keep the existing password.")
    private String password;
    @Schema(description="Only ADMIN may change role; omit to preserve it.")
    private com.jobportal.entity.User.Role role;
}
