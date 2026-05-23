package com.empik.coupon.domain.exception;

public class CountryNotAllowedException extends RuntimeException {
    public CountryNotAllowedException(String message) {
        super(message);
    }
}
