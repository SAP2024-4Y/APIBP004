package com.jobportal.repository;

import com.jobportal.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Integer> {
    List<Resume> findByCandidateId(Integer candidateId);
    Resume findTopByCandidateIdOrderByUploadedAtDesc(Integer candidateId);
}