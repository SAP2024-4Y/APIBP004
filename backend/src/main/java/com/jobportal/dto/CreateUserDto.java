package com.jobportal.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CreateUserDto {
    @NotBlank @Email @Size(max=255)
    private String email;
    @NotBlank @Size(min=6, max=72) @Schema(format="password", accessMode=Schema.AccessMode.WRITE_ONLY)
    private String password;
    @NotBlank @Pattern(regexp="CANDIDATE|COMPANY|ADMIN") @Schema(allowableValues={"CANDIDATE","COMPANY","ADMIN"})
    private String role;
}
