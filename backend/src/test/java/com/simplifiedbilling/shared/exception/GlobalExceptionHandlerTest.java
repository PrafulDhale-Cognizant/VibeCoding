package com.simplifiedbilling.shared.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = request();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void mapsValidationErrorsIncludingFallbackMessage() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "name", "Name is required."));
        binding.addError(new FieldError("request", "phone", null, false, null, null, null));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                mock(MethodParameter.class),
                binding);

        var response = handler.handleValidation(exception, request);

        assertError(response.getBody(), "VALIDATION_FAILED", "/test");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors())
                .extracting(FieldViolation::message)
                .containsExactly("Name is required.", "Invalid value.");
    }

    @Test
    void mapsConstraintViolations() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("request.username");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be blank");

        var response = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of(violation)),
                request);

        assertThat(response.getBody().code()).isEqualTo("CONSTRAINT_VIOLATION");
        assertThat(response.getBody().fieldErrors())
                .containsExactly(new FieldViolation("request.username", "must not be blank"));
    }

    @Test
    void mapsKnownInfrastructureAndApplicationFailures() {
        assertThat(handler.handleUnreadableMessage(
                new HttpMessageNotReadableException("bad"),
                request).getBody().code()).isEqualTo("MALFORMED_REQUEST");

        assertThat(handler.handleApplication(
                new ApplicationException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Missing"),
                request).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException(Object.class, "1"),
                request).getBody().code()).isEqualTo("CONCURRENT_MODIFICATION");

        assertThat(handler.handleDataIntegrity(
                new DataIntegrityViolationException("duplicate"),
                request).getBody().code()).isEqualTo("DATA_CONFLICT");

        assertThat(handler.handleUnexpected(
                new IllegalStateException("boom"),
                request).getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest value = new MockHttpServletRequest("POST", "/test");
        MDC.put("correlationId", "correlation-1");
        return value;
    }

    private void assertError(ApiError error, String code, String path) {
        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo(code);
        assertThat(error.path()).isEqualTo(path);
        assertThat(error.correlationId()).isEqualTo("correlation-1");
        assertThat(error.timestamp()).isNotNull();
    }
}
