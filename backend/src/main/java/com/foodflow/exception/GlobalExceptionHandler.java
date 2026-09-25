package com.foodflow.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns every exception thrown by a controller or service into the same ErrorResponse JSON.
 * Controllers and services just throw; they never build error responses themselves.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Our own business exceptions: status and code come from the exception. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request);
    }

    /*
     * @Valid failed on a request body (MethodArgumentNotValidException) or on query-string
     * parameters bound to an object (BindException, its parent class): 400 with one message
     * per invalid field. A binding failure (e.g. ?minRating=abc) gets a readable message
     * instead of Spring's "Failed to convert property value..." text.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(BindException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> fieldErrors.putIfAbsent(error.getField(),
                error.isBindingFailure() ? "Invalid value '" + error.getRejectedValue() + "'" : error.getDefaultMessage()));
        ErrorResponse body = new ErrorResponse(Instant.now(), 400, "VALIDATION_FAILED",
                "Request validation failed", request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Unparseable JSON, or a value of the wrong type (e.g. accountType = "ADMIN"). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        String message = "Malformed JSON request";
        if (ex.getCause() instanceof InvalidFormatException invalid
                && invalid.getTargetType() != null && invalid.getTargetType().isEnum()) {
            String field = invalid.getPath().isEmpty() ? "value" : invalid.getPath().getLast().getFieldName();
            message = "Invalid value '%s' for %s. Allowed values: %s".formatted(
                    invalid.getValue(), field, Arrays.toString(invalid.getTargetType().getEnumConstants()));
        } else if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()
                && mismatch.getPath().getLast().getFieldName() != null) {
            // e.g. "rating": 4.5 or "quantity": "two": name the field instead of a parser message.
            message = "Invalid value for " + mismatch.getPath().getLast().getFieldName();
        }
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", message, request);
    }

    /*
     * Wrong email OR wrong password: one identical message, so the response never
     * reveals whether an email is registered (prevents account enumeration).
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password", request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account has been disabled", request);
    }

    /*
     * Re-throw security exceptions raised inside controllers (e.g. by @PreAuthorize) so that
     * Spring Security's ExceptionTranslationFilter handles them: 401 for anonymous callers,
     * 403 for authenticated ones. Without this, the catch-all handler below would turn
     * them into 500 errors.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurityException(RuntimeException ex) {
        throw ex;
    }

    /*
     * A database constraint fired. Normally the service checks first (e.g. existsByEmail),
     * but two concurrent requests can both pass that check; the unique constraint is the
     * final guard, and this turns its error into a 409 instead of a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "CONFLICT",
                "The request conflicts with existing data", request);
    }

    /** A path variable of the wrong type, e.g. GET /api/restaurants/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Invalid value '%s' for %s".formatted(ex.getValue(), ex.getName()), request);
    }

    /*
     * Optimistic locking (@Version): someone else saved this row after we read it.
     * 409 tells the client to reload and retry instead of silently overwriting their change.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "This resource was modified by someone else. Reload and try again", request);
    }

    /** Safety net for an unknown sort property that got past PageableUtils. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handlePropertyReference(PropertyReferenceException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Unknown property '" + ex.getPropertyName() + "'", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "No endpoint " + request.getRequestURI(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), request);
    }

    /*
     * @Size/@Min/... on @RequestParam or @PathVariable (e.g. ?keyword= longer than 100 chars).
     * Spring 6.1+ validates such parameters itself and throws this exception.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParameterValidation(HandlerMethodValidationException ex,
                                                                   HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> fieldErrors.putIfAbsent(
                result.getMethodParameter().getParameterName(),
                result.getResolvableErrors().getFirst().getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorResponse(Instant.now(), 400, "VALIDATION_FAILED",
                "Request validation failed", request.getRequestURI(), fieldErrors));
    }

    /*
     * Everything else. Spring MVC's own exceptions (wrong Content-Type -> 415, unacceptable
     * Accept header -> 406, missing required parameter -> 400, ...) implement Spring's ErrorResponse
     * interface and know their correct status. Those are CLIENT mistakes: answer with that status,
     * log at WARN. Only a genuinely unexpected exception is a 500, logged at ERROR with the stack
     * trace, and its details are never sent to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse springError && springError.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
            log.warn("{} {} -> {}: {}", request.getMethod(), request.getRequestURI(), status.value(), ex.getMessage());
            String detail = springError.getBody().getDetail();
            return build(status, status.name(), detail != null ? detail : status.getReasonPhrase(), request);
        }
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                                HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), code, message, request.getRequestURI()));
    }
}
