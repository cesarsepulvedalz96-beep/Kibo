package com.stockflow.shared.presentation;

import com.stockflow.shared.domain.BusinessException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);

    public record ErrorResponse(String code) {}

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> business(BusinessException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        IllegalArgumentException.class
    })
    ResponseEntity<ErrorResponse> validation(Exception ignored) {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> conflict(Exception ignored) {
        return ResponseEntity.status(409).body(new ErrorResponse("CONFLICT"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> denied(Exception ignored) {
        return ResponseEntity.status(403).body(new ErrorResponse("FORBIDDEN"));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> unauthorized(Exception ignored) {
        return ResponseEntity.status(401).body(new ErrorResponse("INVALID_CREDENTIALS"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception exception) {
        // Do not log request bodies, database parameters, credentials or session identifiers.
        LOG.error("Unhandled request failure: {}", exception.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(new ErrorResponse("INTERNAL_ERROR"));
    }
}
