package com.jobportal.graphql;

import com.jobportal.entity.Application;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

@Component
public class ApplicationResolver implements GraphQLResolver<Application> {
    public String appliedAt(Application application) { return com.jobportal.time.UtcTime.format(application.getAppliedAt()); }
}
