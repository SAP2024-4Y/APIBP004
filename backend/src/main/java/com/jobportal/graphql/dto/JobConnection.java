package com.jobportal.graphql.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class JobConnection {
    private List<JobEdge> edges;
    private PageInfo pageInfo;
    private Integer totalCount;
}
