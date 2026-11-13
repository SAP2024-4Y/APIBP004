package com.jobportal.repository;

import com.jobportal.entity.InterviewSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InterviewSlotRepository extends JpaRepository<InterviewSlot, Long> {

    List<InterviewSlot> findByJobId(Integer jobId);

    @Query("SELECT s FROM InterviewSlot s WHERE s.startsAt >= :startsAt AND s.endsAt <= :endsAt")
    List<InterviewSlot> findByTimeWindow(
        @Param("startsAt") LocalDateTime startsAt,
        @Param("endsAt") LocalDateTime endsAt
    );

    @Query("SELECT s FROM InterviewSlot s WHERE s.startsAt < :endsAt AND s.endsAt > :startsAt")
    List<InterviewSlot> findAllInRange(
        @Param("startsAt") LocalDateTime startsAt,
        @Param("endsAt") LocalDateTime endsAt
    );

    @Query("SELECT s FROM InterviewSlot s WHERE s.jobId = :jobId AND s.startsAt < :endsAt AND s.endsAt > :startsAt")
    List<InterviewSlot> findConflictingSlots(
        @Param("jobId") Integer jobId,
        @Param("startsAt") LocalDateTime startsAt,
        @Param("endsAt") LocalDateTime endsAt
    );
}
