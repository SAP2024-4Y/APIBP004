package com.jobportal.repository;

import com.jobportal.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT e FROM Application e WHERE e.id = :id")
    java.util.Optional<Application> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Integer id);

    List<Application> findByCandidateId(Integer candidateId);
    List<Application> findByJobId(Integer jobId);
    Application findByCandidateIdAndJobId(Integer candidateId, Integer jobId);
}