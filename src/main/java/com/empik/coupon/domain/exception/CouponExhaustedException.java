package com.empik.coupon.domain.exception;

public class CouponExhaustedException extends RuntimeException {
    public CouponExhaustedException(String message) {
        super(message);
    }
}
