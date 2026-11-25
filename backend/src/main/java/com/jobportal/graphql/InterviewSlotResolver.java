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

    public Integer candidateId(InterviewSlot slot, DataFetchingEnvironment env) {
        return access.visibleCandidateId(access.actor(env), slot);
    }
}
