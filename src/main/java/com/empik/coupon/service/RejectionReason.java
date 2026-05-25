package com.empik.coupon.service;

enum RejectionReason {
    COUNTRY_NOT_ALLOWED,
    EXHAUSTED,
    ALREADY_USED;

    String tag() {
        return name().toLowerCase();
    }
}
