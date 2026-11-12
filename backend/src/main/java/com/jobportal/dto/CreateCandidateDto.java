package com.jobportal.dto;

import lombok.Data;

@Data
public class CreateCandidateDto {
    private Integer userId;
    private String fullName;
    private String phone;
    private String location;
    private String experience;
}