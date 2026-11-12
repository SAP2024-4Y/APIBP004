package com.jobportal.dto.response;

import com.jobportal.entity.Application;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for Application entity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationResponseDto {
    private Integer id;
    private Integer candidateId;
    private Integer jobId;
    private String status;
    private LocalDateTime appliedAt;

    public static ApplicationResponseDto from(Application application) {
        if (application == null) return null;
        return new ApplicationResponseDto(
            application.getId(),
            application.getCandidateId(),
            application.getJobId(),
            application.getStatus().name(),
            application.getAppliedAt()
        );
    }
}
