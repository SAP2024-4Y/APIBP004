package com.jobportal.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview_slots")
@Data
public class InterviewSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Integer jobId;

    @Column(name = "candidate_id")
    private Integer candidateId;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(nullable = false, columnDefinition = "VARCHAR(30) DEFAULT 'SCHEDULED'")
    private String status = "SCHEDULED";

    @Column(name = "created_at")
    private LocalDateTime createdAt = com.jobportal.time.UtcTime.now();
}
