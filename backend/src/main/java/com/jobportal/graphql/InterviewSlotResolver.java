package com.jobportal.graphql;

import com.jobportal.entity.InterviewSlot;
import com.jobportal.security.ResourceAuthorization;
import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterviewSlotResolver implements GraphQLResolver<InterviewSlot> {
    private final ResourceAuthorization access;

    public String startsAt(InterviewSlot slot) { return com.jobportal.time.UtcTime.format(slot.getStartsAt()); }
    public String endsAt(InterviewSlot slot) { return com.jobportal.time.UtcTime.format(slot.getEndsAt()); }
    public String createdAt(InterviewSlot slot) { return com.jobportal.time.UtcTime.format(slot.getCreatedAt()); }

    public Integer candidateId(InterviewSlot slot, DataFetchingEnvironment env) {
        return access.visibleCandidateId(access.actor(env), slot);
    }
}
