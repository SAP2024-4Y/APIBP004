package com.jobportal.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Basic company information for nested use in other DTOs
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyBasicDto {
    private Integer id;
    private String companyName;
    private String industry;
    private String location;
}
