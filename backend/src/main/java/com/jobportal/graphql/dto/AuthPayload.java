package com.jobportal.graphql.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthPayload {
    private String token;
    private String email;
    private String role;
    private Integer userId;
}
