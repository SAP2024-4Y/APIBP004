package com.jobportal.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "companies")
@Data
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", unique = true, nullable = false)
    private Integer userId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    private String industry;
    private String address;
    private Boolean approved = false;
}