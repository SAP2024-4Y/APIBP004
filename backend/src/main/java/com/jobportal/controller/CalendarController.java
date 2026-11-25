package com.jobportal.controller;

import com.jobportal.entity.InterviewSlot;
import com.jobportal.service.InterviewSlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import com.jobportal.security.ResourceAuthorization;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "Calendar", description = "Interview slot scheduling API - time-window filtering and conflict prevention")
@RestController
@RequestMapping("/api/v1/slots")
@CrossOrigin(origins = "*")
public class CalendarController {

    @Autowired
    private ResourceAuthorization access;

    @Autowired
    private InterviewSlotService slotService;

    @GetMapping
    @Operation(summary = "Get interview slots by time window",
               description = "Returns slots overlapping the given ISO 8601 UTC window. Parameters: starts_at, ends_at. Candidate identities are visible only to the candidate, job owner, or ADMIN.")
    public ResponseEntity<List<InterviewSlot>> getSlots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime starts_at,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ends_at) {
        return ResponseEntity.ok(slotService.findByTimeWindow(starts_at, ends_at).stream()
                .map(access::publicSlot).toList());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create an interview slot",
               description = "Candidates book for themselves; companies schedule only for their own jobs; ADMIN may schedule any job. Returns 409 SLOT_CONFLICT on overlap.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<InterviewSlot> createSlot(@RequestBody Map<String, Object> body) {
        Integer jobId = (Integer) body.get("jobId");
        Integer candidateId = body.get("candidateId") != null ? (Integer) body.get("candidateId") : null;
        candidateId = access.slotCandidate(access.currentActor(), jobId, candidateId);
        String startsAtStr = (String) body.get("startsAt");
        String endsAtStr = (String) body.get("endsAt");

        LocalDateTime startsAt = startsAtStr != null ? LocalDateTime.parse(startsAtStr.replace("Z", "")) : null;
        LocalDateTime endsAt = endsAtStr != null ? LocalDateTime.parse(endsAtStr.replace("Z", "")) : null;

        InterviewSlot slot = slotService.createSlot(jobId, candidateId, startsAt, endsAt);
        return ResponseEntity.status(HttpStatus.CREATED).body(slot);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete an interview slot",
               description = "Requires the assigned candidate, owning company, or ADMIN.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<Void> deleteSlot(@PathVariable Long id) {
        access.requireSlotOwner(id);
        slotService.deleteSlot(id);
        return ResponseEntity.noContent().build();
    }
}
