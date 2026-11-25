package com.jobportal.security.dto;

import com.jobportal.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Size(max=255)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 72, message = "Password must be between 6 and 72 characters long")
    private String password;

    @NotNull(message = "Role is required")
    @Schema(description = "Public registration role. ADMIN accounts cannot be created through this endpoint.",
            implementation = String.class,
            allowableValues = {"CANDIDATE", "COMPANY"})
    private User.Role role;
}

