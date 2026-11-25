package com.jobportal.graphql;

import com.jobportal.exception.*;
import graphql.ExceptionWhileDataFetching;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.kickstart.execution.error.GraphQLErrorHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Kickstart's response error handler; preserves partial data and stable application error codes. */
@Component
public class GraphQLExceptionHandler implements GraphQLErrorHandler {
    @Override
    public List<GraphQLError> processErrors(List<GraphQLError> errors) {
        return errors.stream().map(this::mapError).toList();
    }

    private GraphQLError mapError(GraphQLError error) {
        if (!(error instanceof ExceptionWhileDataFetching failure)) return error;
        Throwable exception = failure.getException();
        while ((exception instanceof java.util.concurrent.CompletionException
                || exception instanceof java.lang.reflect.InvocationTargetException)
                && exception.getCause() != null) {
            exception = exception.getCause();
        }
        String code = resolveCode(exception);
        String message = switch (code) {
            case "INTERNAL_ERROR" -> "An unexpected error occurred";
            case "DATA_INTEGRITY_ERROR" -> "Request conflicts with database constraints";
            default -> exception.getMessage();
        };
        return GraphqlErrorBuilder.newError()
                .message(message == null ? "An error occurred" : message)
                .path(error.getPath())
                .locations(error.getLocations())
                .extensions(Map.of("code", code))
                .build();
    }

    private String resolveCode(Throwable ex) {
        if (ex instanceof ResourceNotFoundException) return "RESOURCE_NOT_FOUND";
        if (ex instanceof ApplicationConflictException) return "APPLICATION_CONFLICT";
        if (ex instanceof SchedulingConflictException) return "SLOT_CONFLICT";
        if (ex instanceof UnauthorizedException) return "UNAUTHORIZED";
        if (ex instanceof ValidationException || ex instanceof IllegalArgumentException || ex instanceof java.time.format.DateTimeParseException) return "VALIDATION_ERROR";
        if (ex instanceof AccessDeniedException) return "FORBIDDEN";
        if (ex instanceof DuplicateResourceException) return "DUPLICATE_RESOURCE";
        if (ex instanceof org.springframework.dao.DataIntegrityViolationException) return "DATA_INTEGRITY_ERROR";
        return "INTERNAL_ERROR";
    }
}
