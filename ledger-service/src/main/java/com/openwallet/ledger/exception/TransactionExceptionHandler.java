package com.openwallet.ledger.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;

/**
 * Maps ledger exceptions to HTTP responses with a simple error payload.
 */
@ControllerAdvice
public class TransactionExceptionHandler {

    @ExceptionHandler(TransactionNotFoundException.class)
    @ResponseBody
    public ResponseEntity<ApiError> handleNotFound(TransactionNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        java.util.List<org.springframework.validation.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        java.util.List<FieldErrorDetail> details = fieldErrors.stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .collect(java.util.stream.Collectors.toList());
        
        String message = fieldErrors.stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Validation failed");
        
        return build(HttpStatus.BAD_REQUEST, message, request, details);
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseBody
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Authentication failed: " + ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseBody
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Access denied: " + ex.getMessage(), request);
    }

    @ExceptionHandler(com.openwallet.ledger.exception.LimitExceededException.class)
    @ResponseBody
    public ResponseEntity<ApiError> handleLimitExceeded(com.openwallet.ledger.exception.LimitExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(com.openwallet.ledger.exception.WalletNotFoundException.class)
    @ResponseBody
    public ResponseEntity<ApiError> handleWalletNotFound(com.openwallet.ledger.exception.WalletNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        return build(status, message, request, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request,
                                          java.util.List<FieldErrorDetail> details) {
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                details);
        return ResponseEntity.status(status).body(error);
    }

    public static class ApiError {
        private final Instant timestamp;
        private final int status;
        private final String error;
        private final String message;
        private final String path;
        private final java.util.List<FieldErrorDetail> details;

        public ApiError(Instant timestamp, int status, String error, String message, String path,
                       java.util.List<FieldErrorDetail> details) {
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

        public java.util.List<FieldErrorDetail> getDetails() {
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
