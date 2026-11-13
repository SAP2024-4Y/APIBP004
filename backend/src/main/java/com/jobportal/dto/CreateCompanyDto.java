package com.jobportal.dto;

import lombok.Data;

@Data
public class CreateCompanyDto {
    private Integer userId;
    private String companyName;
    private String industry;
    private String address;
}