package com.jobportal.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "applications", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"candidate_id", "job_id"}))
@Data
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "candidate_id", nullable = false)
    private Integer candidateId;

    @Column(name = "job_id", nullable = false)
    private Integer jobId;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt = LocalDateTime.now();

    public enum ApplicationStatus {
        SUBMITTED, REVIEWED, SHORTLISTED, REJECTED, HIRED
    }
}