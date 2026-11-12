package com.jobportal.graphql.dto;

import com.jobportal.entity.Job;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JobEdge {
    private Job node;
    private String cursor;
}
