package com.jobportal.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.List;

@Data
@Schema(description = "Shared REST error response, including errors raised before controller execution.")
public class ErrorResponse {
    @Schema(description = "UTC time of the failure", example = "2027-01-10T10:00:00Z")
    private Instant timestamp;
    @Schema(description = "HTTP response status", example = "400")
    private int status;
    @Schema(description = "Stable application error code", example = "VALIDATION_ERROR")
    private String code;
    @Schema(description = "HTTP reason phrase", example = "Bad Request")
    private String error;
    @Schema(description = "Human-readable explanation", example = "Job title must be at least 3 characters long")
    private String message;
    @Schema(description = "Request path without query parameters", example = "/api/v1/jobs")
    private String path;
    @Schema(description = "Field validation details; empty when not applicable")
    private List<String> details;

    public static ErrorResponse of(HttpStatus status, String code, String message, String path, List<String> details) {
        ErrorResponse response = new ErrorResponse();
        response.timestamp = Instant.now();
        response.status = status.value();
        response.code = code;
        response.error = status.getReasonPhrase();
        response.message = message;
        response.path = path;
        response.details = details == null ? List.of() : details;
        return response;
    }
}
