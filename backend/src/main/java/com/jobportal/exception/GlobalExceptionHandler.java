package com.jobportal.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private ResponseEntity<ErrorResponse> error(HttpServletRequest request, HttpStatus status,
                                               String code, String message, List<String> details) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, code, message, request.getRequestURI(), details));
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), null);
    }
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> duplicate(DuplicateResourceException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ex.getMessage(), null);
    }
    @ExceptionHandler(SchedulingConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(SchedulingConflictException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "SLOT_CONFLICT", ex.getMessage(), null);
    }
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> unauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), null);
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> forbidden(AccessDeniedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.FORBIDDEN, "FORBIDDEN", "You don't have permission to access this resource", null);
    }
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> validation(ValidationException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), null);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> fields(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()).sorted().toList();
        return error(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Input validation failed for one or more fields", details);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> constraints(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().toList();
        return error(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Input validation failed for one or more fields", details);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> malformed(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Malformed JSON or invalid field type or enum value", null);
    }
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> missing(Exception ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), null);
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> type(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid value for parameter '" + ex.getName() + "'", null);
    }
    @ExceptionHandler({IllegalArgumentException.class, DateTimeParseException.class})
    public ResponseEntity<ErrorResponse> argument(Exception ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid argument or date-time format", null);
    }
    @ExceptionHandler({PayloadTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponse> tooLarge(Exception ex, HttpServletRequest request) {
        return error(request, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Resume file must not exceed 5 MiB; multipart request must not exceed 6 MiB", null);
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> media(Exception ex, HttpServletRequest request) {
        return error(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported request Content-Type", null);
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> method(Exception ex, HttpServletRequest request) {
        return error(request, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method is not supported for this path", null);
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> route(Exception ex, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The requested resource was not found", null);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integrity(Exception ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "DATA_INTEGRITY_ERROR", "Request conflicts with database constraints", null);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> internal(Exception ex, HttpServletRequest request) {
        return error(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred. Please try again later.", null);
    }
}
