package com.jobportal.graphql.dto;

import com.jobportal.entity.Company;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CompanyEdge {
    private Company node;
    private String cursor;
}
