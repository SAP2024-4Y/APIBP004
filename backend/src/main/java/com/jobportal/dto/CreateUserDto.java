package com.jobportal.dto;

import lombok.Data;

@Data
public class CreateUserDto {
    private String email;
    private String password;
    private String role; // "CANDIDATE", "COMPANY", or "ADMIN"
}