package com.jobportal.security;

import com.jobportal.entity.*;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.exception.UnauthorizedException;
import com.jobportal.exception.ValidationException;
import com.jobportal.repository.*;
import graphql.kickstart.execution.context.GraphQLKickstartContext;
import graphql.schema.DataFetchingEnvironment;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Shared REST/GraphQL policy. Read ownership from repositories before modifying cached entities. */
@Component
@RequiredArgsConstructor
public class ResourceAuthorization {
    private final UserRepository users;
    private final CandidateRepository candidates;
    private final CompanyRepository companies;
    private final JobRepository jobs;
    private final ApplicationRepository applications;
    private final ResumeRepository resumes;
    private final InterviewSlotRepository slots;

    /** Immutable request identity, also captured for asynchronous GraphQL field resolution. */
    public record Actor(Integer userId, User.Role role) {
        public boolean isAdmin() { return role == User.Role.ADMIN; }
    }

    public Actor currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails user) {
            return new Actor(user.getId(), user.getRole());
        }
        return new Actor(null, null);
    }

    public Actor actor(DataFetchingEnvironment env) {
        Object context = env.getContext();
        Object actor = context instanceof GraphQLKickstartContext kickstart
                ? kickstart.getMapOfContext().get(Actor.class)
                : env.getGraphQlContext().get(Actor.class);
        return actor instanceof Actor identity ? identity : new Actor(null, null);
    }

    public void requireAuthenticated(Actor actor) {
        if (actor == null || actor.userId() == null || actor.role() == null) {
            throw new UnauthorizedException("Authentication required");
        }
    }

    private void require(boolean allowed) {
        if (!allowed) throw new AccessDeniedException("You don't have permission to access this resource");
    }

    public void requireAdmin() {
        Actor actor = currentActor();
        requireAuthenticated(actor);
        require(actor.isAdmin());
    }

    public void requireCurrentUser(Integer userId) {
        Actor actor = currentActor();
        requireAuthenticated(actor);
        require(actor.isAdmin() || Objects.equals(actor.userId(), userId));
    }

    public void requireUnchangedRole(User.Role requestedRole, User.Role existingRole) {
        requireAuthenticated(currentActor());
        require(requestedRole == null || requestedRole == existingRole || currentActor().isAdmin());
    }

    public Integer profileOwner(Integer requestedUserId, User.Role requiredRole) {
        Actor actor = currentActor();
        requireAuthenticated(actor);
        require(actor.isAdmin() || actor.role() == requiredRole);
        Integer owner = requestedUserId == null ? actor.userId() : requestedUserId;
        require(actor.isAdmin() || Objects.equals(owner, actor.userId()));
        User user = users.findById(owner).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != requiredRole) {
            throw new ValidationException("Profile owner must have role " + requiredRole);
        }
        return owner;
    }

    public void requireCompanyOwner(Integer companyId) { requireCompanyOwner(currentActor(), companyId); }

    public void requireCompanyOwner(Actor actor, Integer companyId) {
        requireAuthenticated(actor);
        require(actor.isAdmin() || actor.role() == User.Role.COMPANY);
        require(actor.isAdmin() || ownsCompany(actor, companyId));
        if (companyId == null || companyId <= 0) throw new ValidationException("Company ID must be positive");
        if (!companies.existsById(companyId)) throw new ResourceNotFoundException("Company not found");
    }

    private boolean ownsCompany(Actor actor, Integer companyId) {
        return actor.role() == User.Role.COMPANY && companyId != null
                && companies.findById(companyId).map(c -> Objects.equals(c.getUserId(), actor.userId())).orElse(false);
    }

    public void requireJobOwner(Integer jobId) { requireJobOwner(currentActor(), jobId); }

    public void requireJobOwner(Actor actor, Integer jobId) {
        requireAuthenticated(actor);
        require(actor.isAdmin() || actor.role() == User.Role.COMPANY);
        require(actor.isAdmin() || ownsJob(actor, jobId));
    }

    private boolean ownsJob(Actor actor, Integer jobId) {
        return jobId != null && jobs.findById(jobId).map(j -> ownsCompany(actor, j.getCompanyId())).orElse(false);
    }

    private boolean ownsCandidate(Actor actor, Integer candidateId) {
        return actor.role() == User.Role.CANDIDATE && candidateId != null
                && candidates.findById(candidateId).map(c -> Objects.equals(c.getUserId(), actor.userId())).orElse(false);
    }

    public void requireCandidateOwner(Integer candidateId) {
        Actor actor = currentActor();
        requireAuthenticated(actor);
        require(actor.isAdmin() || ownsCandidate(actor, candidateId));
    }

    public void requireCandidateReader(Integer candidateId) {
        Actor actor = currentActor();
        requireAuthenticated(actor);
        boolean applicant = actor.role() == User.Role.COMPANY && candidateId != null
                && applications.findByCandidateId(candidateId).stream().anyMatch(a -> ownsJob(actor, a.getJobId()));
        require(actor.isAdmin() || ownsCandidate(actor, candidateId) || applicant);
    }

    public Integer applicationCandidate(Integer requestedCandidateId) {
        Actor actor = currentActor();
        requireAuthenticated(actor);
        require(actor.role() == User.Role.CANDIDATE);
        Candidate candidate = candidates.findByUserId(actor.userId());
        if (candidate == null) throw new ResourceNotFoundException("Create a candidate profile before applying");
        require(requestedCandidateId == null || Objects.equals(requestedCandidateId, candidate.getId()));
        return candidate.getId();
    }

    public void requireApplicationReader(Integer applicationId) {
        Actor actor = currentActor();
        requireAuthenticated(actor);
        Application application = application(applicationId);
        require(actor.isAdmin() || ownsCandidate(actor, application.getCandidateId()) || ownsJob(actor, application.getJobId()));
    }

    public void requireApplicationReviewer(Integer applicationId) {
        requireAuthenticated(currentActor());
        requireJobOwner(application(applicationId).getJobId());
    }

    public void requireApplicationOwner(Integer applicationId) {
        requireAuthenticated(currentActor());
        requireCandidateOwner(application(applicationId).getCandidateId());
    }

    private Application application(Integer id) {
        return applications.findById(id).orElseThrow(() -> new ResourceNotFoundException("Application not found"));
    }

    public void requireResumeAccess(Integer resumeId, boolean write) {
        requireAuthenticated(currentActor());
        Resume resume = resumes.findById(resumeId).orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
        if (write) requireCandidateOwner(resume.getCandidateId());
        else requireCandidateReader(resume.getCandidateId());
    }

    public Integer slotCandidate(Actor actor, Integer jobId, Integer candidateId) {
        requireAuthenticated(actor);
        if (actor.role() == User.Role.CANDIDATE) {
            Candidate own = candidates.findByUserId(actor.userId());
            if (own == null) throw new ResourceNotFoundException("Create a candidate profile before booking");
            require(candidateId == null || Objects.equals(candidateId, own.getId()));
            candidateId = own.getId();
        } else {
            requireJobOwner(actor, jobId);
        }
        if (jobId == null || !jobs.existsById(jobId)) throw new ResourceNotFoundException("Job not found");
        if (candidateId != null && !candidates.existsById(candidateId)) throw new ResourceNotFoundException("Candidate not found");
        return candidateId;
    }

    public void requireSlotOwner(Long slotId) {
        Actor actor = currentActor();
        requireAuthenticated(actor);
        InterviewSlot slot = slots.findById(slotId).orElseThrow(() -> new ResourceNotFoundException("Interview slot not found"));
        require(actor.isAdmin() || ownsJob(actor, slot.getJobId()) || ownsCandidate(actor, slot.getCandidateId()));
    }

    public Integer visibleCandidateId(Actor actor, InterviewSlot slot) {
        return actor.isAdmin() || ownsCandidate(actor, slot.getCandidateId()) || ownsJob(actor, slot.getJobId())
                ? slot.getCandidateId() : null;
    }

    public InterviewSlot publicSlot(InterviewSlot slot) {
        InterviewSlot view = new InterviewSlot();
        view.setId(slot.getId());
        view.setJobId(slot.getJobId());
        view.setCandidateId(visibleCandidateId(currentActor(), slot));
        view.setStartsAt(slot.getStartsAt());
        view.setEndsAt(slot.getEndsAt());
        view.setStatus(slot.getStatus());
        view.setCreatedAt(slot.getCreatedAt());
        return view;
    }
}
