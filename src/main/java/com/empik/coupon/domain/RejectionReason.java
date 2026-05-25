package com.empik.coupon.domain;

public enum RejectionReason {
    COUNTRY_NOT_ALLOWED,
    EXHAUSTED,
    ALREADY_USED;

    public String tag() {
        return name().toLowerCase();
    }
}
