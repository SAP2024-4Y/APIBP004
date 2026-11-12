package com.jobportal.dto.response;

import com.jobportal.entity.Candidate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for Candidate entity
 * Excludes internal userId reference
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandidateResponseDto {
    private Integer id;
    private String fullName;
    private String phone;
    private String location;
    private String experience;

    public static CandidateResponseDto from(Candidate candidate) {
        if (candidate == null) return null;
        return new CandidateResponseDto(
            candidate.getId(),
            candidate.getFullName(),
            candidate.getPhone(),
            candidate.getLocation(),
            candidate.getExperience()
        );
    }
}
