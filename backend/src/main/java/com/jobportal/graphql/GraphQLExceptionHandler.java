package com.jobportal.graphql;

import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.exception.SchedulingConflictException;
import com.jobportal.exception.UnauthorizedException;
import com.jobportal.exception.ValidationException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class GraphQLExceptionHandler implements DataFetcherExceptionHandler {

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
            DataFetcherExceptionHandlerParameters handlerParameters) {
        Throwable exception = handlerParameters.getException();
        String code = resolveCode(exception);
        String message = exception.getMessage() != null ? exception.getMessage() : "An error occurred";

        GraphQLError error = GraphqlErrorBuilder.newError()
            .message(message)
            .path(handlerParameters.getPath())
            .location(handlerParameters.getSourceLocation())
            .extensions(Map.of("code", code))
            .build();

        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult.newResult().error(error).build()
        );
    }

    private String resolveCode(Throwable ex) {
        if (ex instanceof ResourceNotFoundException) return "RESOURCE_NOT_FOUND";
        if (ex instanceof SchedulingConflictException) return "SLOT_CONFLICT";
        if (ex instanceof UnauthorizedException) return "UNAUTHORIZED";
        if (ex instanceof ValidationException) return "VALIDATION_ERROR";
        if (ex instanceof org.springframework.security.access.AccessDeniedException) return "FORBIDDEN";
        if (ex instanceof com.jobportal.exception.DuplicateResourceException) return "DUPLICATE_RESOURCE";
        return "INTERNAL_ERROR";
    }
}
