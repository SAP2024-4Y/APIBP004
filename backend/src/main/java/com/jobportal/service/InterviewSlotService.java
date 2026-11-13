package com.jobportal.service;

import com.jobportal.entity.InterviewSlot;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.exception.SchedulingConflictException;
import com.jobportal.exception.ValidationException;
import com.jobportal.repository.InterviewSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterviewSlotService {

    @Autowired
    private InterviewSlotRepository slotRepository;

    public List<InterviewSlot> findByTimeWindow(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt != null && endsAt != null) {
            if (endsAt.isBefore(startsAt)) {
                throw new ValidationException("ends_at must be after starts_at");
            }
            return slotRepository.findAllInRange(startsAt, endsAt);
        }
        return slotRepository.findAll();
    }

    @Transactional
    public InterviewSlot createSlot(Integer jobId, Integer candidateId, LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt == null || endsAt == null) {
            throw new ValidationException("starts_at and ends_at are required");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new ValidationException("ends_at must be after starts_at");
        }

        List<InterviewSlot> conflicts = slotRepository.findConflictingSlots(jobId, startsAt, endsAt);
        if (!conflicts.isEmpty()) {
            throw new SchedulingConflictException(
                "Interview slot conflicts with an existing slot for job " + jobId +
                " between " + startsAt + " and " + endsAt
            );
        }

        InterviewSlot slot = new InterviewSlot();
        slot.setJobId(jobId);
        slot.setCandidateId(candidateId);
        slot.setStartsAt(startsAt);
        slot.setEndsAt(endsAt);
        slot.setStatus("SCHEDULED");
        slot.setCreatedAt(LocalDateTime.now());

        return slotRepository.save(slot);
    }

    public void deleteSlot(Long id) {
        if (!slotRepository.existsById(id)) {
            throw new ResourceNotFoundException("Interview slot not found with id: " + id);
        }
        slotRepository.deleteById(id);
    }

    public InterviewSlot getById(Long id) {
        return slotRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Interview slot not found with id: " + id));
    }
}
