package com.openwallet.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps authentication-related exceptions to HTTP responses with a consistent error payload.
 */
@ControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseBody
    public ResponseEntity<ApiError> handleUserAlreadyExists(UserAlreadyExistsException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseBody
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        List<FieldErrorDetail> details = fieldErrors.stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.toList());

        String message = fieldErrors.stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Validation failed");

        return build(HttpStatus.BAD_REQUEST, message, request, details);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseBody
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException ex,
                                                       HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseBody
    public ResponseEntity<ApiError> handleRuntimeException(RuntimeException ex,
                                                             HttpServletRequest request) {
        // Check if it's a Keycloak-related error
        String message = ex.getMessage();
        if (message != null) {
            if (message.contains("already exists")) {
                return build(HttpStatus.CONFLICT, message, request);
            }
            if (message.contains("Invalid credentials") || message.contains("Authentication failed")) {
                return build(HttpStatus.UNAUTHORIZED, message, request);
            }
            if (message.contains("not found")) {
                return build(HttpStatus.NOT_FOUND, message, request);
            }
        }
        // Default to 500 for unexpected errors
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An error occurred: " + message, request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        return build(status, message, request, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request,
                                          List<FieldErrorDetail> details) {
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                details
        );
        return ResponseEntity.status(status).body(error);
    }

    public static class ApiError {
        private final Instant timestamp;
        private final int status;
        private final String error;
        private final String message;
        private final String path;
        private final List<FieldErrorDetail> details;

        public ApiError(Instant timestamp, int status, String error, String message, String path,
                       List<FieldErrorDetail> details) {
            this.timestamp = timestamp;
            this.status = status;
            this.error = error;
            this.message = message;
            this.path = path;
            this.details = details;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public int getStatus() {
            return status;
        }

        public String getError() {
            return error;
        }

        public String getMessage() {
            return message;
        }

        public String getPath() {
            return path;
        }

        public List<FieldErrorDetail> getDetails() {
            return details;
        }
    }

    public static class FieldErrorDetail {
        private final String field;
        private final String message;

        public FieldErrorDetail(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }
}

