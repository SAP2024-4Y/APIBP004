package com.jobportal.graphql.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class CompanyConnection {
    private List<CompanyEdge> edges;
    private PageInfo pageInfo;
    private Integer totalCount;
}
