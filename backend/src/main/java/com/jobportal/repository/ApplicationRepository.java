package com.jobportal.repository;

import com.jobportal.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Integer> {
    List<Application> findByCandidateId(Integer candidateId);
    List<Application> findByJobId(Integer jobId);
    Application findByCandidateIdAndJobId(Integer candidateId, Integer jobId);
}