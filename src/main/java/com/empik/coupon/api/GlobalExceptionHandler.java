package com.empik.coupon.api;

import com.empik.coupon.api.response.ErrorResponse;
import com.empik.coupon.domain.exception.CountryNotAllowedException;
import com.empik.coupon.domain.exception.CouponAlreadyExistsException;
import com.empik.coupon.domain.exception.CouponAlreadyUsedException;
import com.empik.coupon.domain.exception.CouponExhaustedException;
import com.empik.coupon.domain.exception.CouponNotFoundException;
import com.empik.coupon.domain.exception.GeoLocationUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CouponNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle(final CouponNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(CouponExhaustedException.class)
    public ResponseEntity<ErrorResponse> handle(final CouponExhaustedException ex) {
        return error(HttpStatus.CONFLICT, "COUPON_EXHAUSTED", ex.getMessage());
    }

    @ExceptionHandler(CountryNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handle(final CountryNotAllowedException ex) {
        return error(HttpStatus.FORBIDDEN, "COUNTRY_NOT_ALLOWED", ex.getMessage());
    }

    @ExceptionHandler(CouponAlreadyUsedException.class)
    public ResponseEntity<ErrorResponse> handle(final CouponAlreadyUsedException ex) {
        return error(HttpStatus.CONFLICT, "COUPON_ALREADY_USED", ex.getMessage());
    }

    @ExceptionHandler(CouponAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handle(final CouponAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "COUPON_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(GeoLocationUnavailableException.class)
    public ResponseEntity<ErrorResponse> handle(final GeoLocationUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "GEOLOCATION_UNAVAILABLE", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handle(final MethodArgumentNotValidException ex) {
        final String message = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handle(final Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "An unexpected error occurred. Please try again later.");
    }

    private ResponseEntity<ErrorResponse> error(final HttpStatus status, final String code, final String message) {
        return ResponseEntity.status(status).body(
            new ErrorResponse(code, message, LocalDateTime.now())
        );
    }
}
