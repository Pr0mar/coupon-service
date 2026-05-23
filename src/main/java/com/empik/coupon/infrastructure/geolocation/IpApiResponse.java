package com.empik.coupon.infrastructure.geolocation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IpApiResponse(String status, String countryCode) {

    public boolean isSuccess() {
        return "success".equals(status);
    }

    public String getCountryCode() {
        return countryCode;
    }
}
