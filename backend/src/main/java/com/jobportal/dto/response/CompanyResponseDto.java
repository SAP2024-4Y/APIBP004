package com.jobportal.dto.response;

import com.jobportal.entity.Company;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for Company entity
 * Excludes internal userId reference
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyResponseDto {
    private Integer id;
    private String companyName;
    private String industry;
    private String address;
    private Boolean approved;

    public static CompanyResponseDto from(Company company) {
        if (company == null) return null;
        return new CompanyResponseDto(
            company.getId(),
            company.getCompanyName(),
            company.getIndustry(),
            company.getAddress(),
            company.getApproved()
        );
    }
}
