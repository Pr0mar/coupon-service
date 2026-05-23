package com.empik.coupon.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Slf4j
@Component
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final int BODY_LIMIT_BYTES = 2_048;
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String TRUNCATED_MARKER = "...(truncated)";
    private static final Set<String> EXCLUDED_PREFIXES = Set.of(
        "/actuator", "/swagger-ui", "/v3/api-docs", "/api-docs"
    );

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        final String path = request.getRequestURI();
        return EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        final ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request);
        final ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

        final long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            final long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.info("HTTP {} {} -> {} ({}ms)",
                req.getMethod(), req.getRequestURI(), res.getStatus(), durationMs,
                StructuredArguments.kv("http.method", req.getMethod()),
                StructuredArguments.kv("http.uri", req.getRequestURI()),
                StructuredArguments.kv("http.query", nullIfBlank(req.getQueryString())),
                StructuredArguments.kv("http.status", res.getStatus()),
                StructuredArguments.kv("http.duration_ms", durationMs),
                StructuredArguments.kv("http.client_ip", clientIp(req)),
                StructuredArguments.kv("http.request_body", snippet(req.getContentAsByteArray())),
                StructuredArguments.kv("http.response_body", snippet(res.getContentAsByteArray())));

            res.copyBodyToResponse();
        }
    }

    private static String snippet(final byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        final int len = Math.min(bytes.length, BODY_LIMIT_BYTES);
        final String s = new String(bytes, 0, len, StandardCharsets.UTF_8);
        return bytes.length > BODY_LIMIT_BYTES ? s + TRUNCATED_MARKER : s;
    }

    private static String nullIfBlank(final String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String clientIp(final HttpServletRequest request) {
        final String fwd = request.getHeader(X_FORWARDED_FOR);
        return fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : request.getRemoteAddr();
    }
}
