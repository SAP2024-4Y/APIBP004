package com.jobportal.graphql;

import com.jobportal.entity.InterviewSlot;
import com.jobportal.entity.Job;
import com.jobportal.graphql.dto.AuthPayload;
import com.jobportal.service.AuthService;
import com.jobportal.service.InterviewSlotService;
import com.jobportal.service.JobService;
import com.jobportal.security.dto.LoginRequest;
import com.jobportal.security.dto.AuthResponse;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import com.jobportal.security.ResourceAuthorization;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class JobMutationResolver implements GraphQLMutationResolver {

    @Autowired
    private ResourceAuthorization access;

    @Autowired
    private JobService jobService;

    @Autowired
    private InterviewSlotService slotService;

    @Autowired
    private AuthService authService;

    public Job createJob(Map<String, Object> input, DataFetchingEnvironment env) {
        access.requireCompanyOwner(access.actor(env), (Integer) input.get("companyId"));
        Job job = new Job();
        job.setCompanyId((Integer) input.get("companyId"));
        job.setTitle((String) input.get("title"));
        job.setDescription((String) input.get("description"));
        job.setCategory((String) input.get("category"));
        job.setStatus(input.get("status") != null ? (String) input.get("status") : "OPEN");
        job.setLocation((String) input.get("location"));
        job.setMinSalary((Integer) input.get("minSalary"));
        job.setMaxSalary((Integer) input.get("maxSalary"));
        job.setEmploymentType((String) input.get("employmentType"));
        job.setSkillsRequired((String) input.get("skillsRequired"));
        if (input.get("latitude") != null) job.setLatitude(((Number) input.get("latitude")).doubleValue());
        if (input.get("longitude") != null) job.setLongitude(((Number) input.get("longitude")).doubleValue());
        return jobService.saveJob(job);
    }

    public Job updateJob(String id, Map<String, Object> input, DataFetchingEnvironment env) {
        access.requireJobOwner(access.actor(env), Integer.parseInt(id));
        Job job = jobService.getJobById(Integer.parseInt(id));
        if (input.get("title") != null) job.setTitle((String) input.get("title"));
        if (input.get("description") != null) job.setDescription((String) input.get("description"));
        if (input.get("category") != null) job.setCategory((String) input.get("category"));
        if (input.get("status") != null) job.setStatus((String) input.get("status"));
        if (input.get("location") != null) job.setLocation((String) input.get("location"));
        if (input.get("minSalary") != null) job.setMinSalary((Integer) input.get("minSalary"));
        if (input.get("maxSalary") != null) job.setMaxSalary((Integer) input.get("maxSalary"));
        if (input.get("employmentType") != null) job.setEmploymentType((String) input.get("employmentType"));
        if (input.get("skillsRequired") != null) job.setSkillsRequired((String) input.get("skillsRequired"));
        return jobService.saveJob(job);
    }

    public boolean deleteJob(String id, DataFetchingEnvironment env) {
        access.requireJobOwner(access.actor(env), Integer.parseInt(id));
        jobService.deleteJob(Integer.parseInt(id));
        return true;
    }

    public InterviewSlot createSlot(Map<String, Object> input, DataFetchingEnvironment env) {
        Integer jobId = (Integer) input.get("jobId");
        Integer candidateId = input.get("candidateId") != null ? (Integer) input.get("candidateId") : null;
        candidateId = access.slotCandidate(access.actor(env), jobId, candidateId);
        String startsAtStr = (String) input.get("startsAt");
        String endsAtStr = (String) input.get("endsAt");
        LocalDateTime startsAt = startsAtStr != null ? LocalDateTime.parse(startsAtStr.replace("Z", "")) : null;
        LocalDateTime endsAt = endsAtStr != null ? LocalDateTime.parse(endsAtStr.replace("Z", "")) : null;
        return slotService.createSlot(jobId, candidateId, startsAt, endsAt);
    }

    public AuthPayload login(String username, String password) {
        String email = authService.resolveEmailByUsername(username);
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        AuthResponse resp = authService.login(req);
        return new AuthPayload(
            resp.getToken(),
            resp.getEmail(),
            resp.getRole() != null ? resp.getRole().name() : null,
            resp.getUserId()
        );
    }
}
