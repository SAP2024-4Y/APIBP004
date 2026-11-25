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
    private com.jobportal.service.JobInputMapper jobInputs;

    @Autowired
    private InterviewSlotService slotService;

    @Autowired
    private AuthService authService;

    public Job createJob(Map<String, Object> input, DataFetchingEnvironment env) {
        access.requireCompanyOwner(access.actor(env), (Integer) input.get("companyId"));
        Job job = jobInputs.create(input);
        return jobService.saveJob(job);
    }

    public Job updateJob(String id, Map<String, Object> input, DataFetchingEnvironment env) {
        access.requireJobOwner(access.actor(env), Integer.parseInt(id));
        Job job = jobInputs.update(jobService.getJobById(Integer.parseInt(id)), input);
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
        LocalDateTime startsAt = com.jobportal.time.UtcTime.parse(startsAtStr);
        LocalDateTime endsAt = com.jobportal.time.UtcTime.parse(endsAtStr);
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
