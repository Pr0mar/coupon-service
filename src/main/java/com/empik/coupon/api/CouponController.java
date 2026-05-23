package com.empik.coupon.api;

import com.empik.coupon.api.request.CreateCouponRequest;
import com.empik.coupon.api.request.UseCouponRequest;
import com.empik.coupon.api.response.CouponResponse;
import com.empik.coupon.api.response.CouponUseResponse;
import com.empik.coupon.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupons", description = "Discount coupon management")
public class CouponController {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final CouponService couponService;

    @PostMapping
    @Operation(summary = "Create a new coupon")
    public ResponseEntity<CouponResponse> createCoupon(@Valid @RequestBody final CreateCouponRequest request) {
        final CouponResponse response = couponService.createCoupon(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{code}/use")
    @Operation(summary = "Redeem a coupon")
    public ResponseEntity<CouponUseResponse> useCoupon(
            @PathVariable final String code,
            @Valid @RequestBody final UseCouponRequest request,
            final HttpServletRequest httpRequest) {

        final CouponUseResponse response = couponService.useCoupon(code, request, extractClientIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    private String extractClientIp(final HttpServletRequest request) {
        final String xForwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
