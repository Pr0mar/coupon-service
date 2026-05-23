package com.empik.coupon.domain.exception;

public class GeoLocationUnavailableException extends RuntimeException {
    public GeoLocationUnavailableException(String ip) {
        super("Could not determine country for IP '%s'. Coupon redemption rejected.".formatted(ip));
    }
}
