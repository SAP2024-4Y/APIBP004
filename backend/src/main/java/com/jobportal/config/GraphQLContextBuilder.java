package com.jobportal.config;

import com.jobportal.graphql.dataloader.CompanyDataLoader;
import com.jobportal.graphql.dataloader.CompanyJobsDataLoader;
import graphql.kickstart.execution.context.GraphQLKickstartContext;
import graphql.kickstart.servlet.context.DefaultGraphQLServletContext;
import graphql.kickstart.servlet.context.DefaultGraphQLWebSocketContext;
import graphql.kickstart.servlet.context.GraphQLServletContextBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import com.jobportal.security.ResourceAuthorization;
import org.springframework.stereotype.Component;

@Component
public class GraphQLContextBuilder implements GraphQLServletContextBuilder {

    @Autowired
    private ResourceAuthorization access;

    @Autowired
    private CompanyJobsDataLoader companyJobsDataLoader;

    @Autowired
    private CompanyDataLoader companyDataLoader;

    private DataLoaderRegistry buildRegistry() {
        DataLoaderRegistry registry = new DataLoaderRegistry();
        registry.register("companyJobsLoader", DataLoader.newMappedDataLoader(companyJobsDataLoader));
        registry.register("companyLoader", DataLoader.newMappedDataLoader(companyDataLoader));
        return registry;
    }

    @Override
    public GraphQLKickstartContext build(HttpServletRequest req, HttpServletResponse res) {
        var context = DefaultGraphQLServletContext.createServletContext()
            .with(req)
            .with(res)
            .with(buildRegistry())
            .build();
        context.put(ResourceAuthorization.Actor.class, access.currentActor());
        return context;
    }

    @Override
    public GraphQLKickstartContext build(Session session, HandshakeRequest handshakeRequest) {
        return DefaultGraphQLWebSocketContext.createWebSocketContext()
            .with(session)
            .with(handshakeRequest)
            .with(buildRegistry())
            .build();
    }

    @Override
    public GraphQLKickstartContext build() {
        return DefaultGraphQLServletContext.createServletContext()
            .with(buildRegistry())
            .build();
    }
}
