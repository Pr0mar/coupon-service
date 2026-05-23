package com.empik.coupon.infrastructure.geolocation;

import com.empik.coupon.domain.exception.GeoLocationUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Slf4j
@Service
public class IpApiGeoLocationService implements GeoLocationService {

    private static final String CIRCUIT_BREAKER_NAME = "geoLocation";
    private static final String FALLBACK_METHOD = "fallback";
    private static final String IP_API_FIELDS = "status,countryCode";
    private static final String IP_API_PATH = "/{ip}?fields={fields}";

    private final RestClient restClient;
    private final String apiUrl;
    private final String overrideCountry;

    public IpApiGeoLocationService(
        @Value("${geolocation.api.url}") final String apiUrl,
        @Value("${geolocation.api.connect-timeout-ms:1000}") final int connectTimeoutMs,
        @Value("${geolocation.api.read-timeout-ms:2000}") final int readTimeoutMs,
        @Value("${geolocation.override-country:}") final String overrideCountry
    ) {
        this.apiUrl = apiUrl;
        this.overrideCountry = overrideCountry;
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();

        if (isOverrideActive()) {
            log.info("Geolocation override active: every request will resolve to '{}'", overrideCountry);
        }
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = FALLBACK_METHOD)
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public String getCountryCode(final String ipAddress) {
        if (isOverrideActive()) {
            return overrideCountry;
        }

        log.debug("Resolving country for IP {}", ipAddress);

        final IpApiResponse response;
        try {
            response = restClient.get()
                .uri(apiUrl + IP_API_PATH, ipAddress, IP_API_FIELDS)
                .retrieve()
                .body(IpApiResponse.class);
        } catch (final RestClientException ex) {
            log.warn("IP API call failed for {}: {}", ipAddress, ex.getMessage());
            throw new GeoLocationUnavailableException(ipAddress);
        }

        if (response == null || !response.isSuccess()) {
            log.warn("IP API returned a non-success response for {}", ipAddress);
            throw new GeoLocationUnavailableException(ipAddress);
        }

        log.debug("IP {} resolved to country {}", ipAddress, response.getCountryCode());
        return response.getCountryCode();
    }

    private boolean isOverrideActive() {
        return overrideCountry != null && !overrideCountry.isBlank();
    }

    private String fallback(final String ipAddress, final Throwable ex) {
        log.warn("Geolocation circuit open for IP {}: {}", ipAddress, ex.getMessage());
        throw new GeoLocationUnavailableException(ipAddress);
    }
}
