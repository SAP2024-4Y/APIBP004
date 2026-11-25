package com.jobportal.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "jobs")
@Data
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "company_id", nullable = false)
    private Integer companyId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "skills_required", columnDefinition = "TEXT")
    private String skillsRequired;

    @Column(name = "salary_range")
    private String salaryRange;

    private String location;

    @Column(name = "employment_type")
    private String employmentType;

    @Column(name = "created_at")
    private LocalDateTime createdAt = com.jobportal.time.UtcTime.now();

    // Geospatial fields
    private Double latitude;
    private Double longitude;

    // Salary range as numeric for filtering
    @Column(name = "min_salary")
    private Integer minSalary;

    @Column(name = "max_salary")
    private Integer maxSalary;

    // Category and status for List API filtering
    private String category;

    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'OPEN'")
    private String status = "OPEN";

    // Interview slot scheduling fields on the job
    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;
}
