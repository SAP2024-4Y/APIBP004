package com.jobportal.dto;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
@Data
public class TokenRequest {
    private String username;
    @Email private String email;
    @NotBlank private String password;
    @AssertTrue(message="username or email is required")
    @Schema(hidden=true)
    public boolean isIdentityPresent() {
        return (username != null && !username.isBlank()) || (email != null && !email.isBlank());
    }
}
