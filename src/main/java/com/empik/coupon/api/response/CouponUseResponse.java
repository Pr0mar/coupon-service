package com.empik.coupon.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Result of a coupon redemption")
public record CouponUseResponse(

    @Schema(description = "Coupon code", example = "SPRING2024")
    String couponCode,

    @Schema(description = "User id", example = "user-123")
    String userId,

    @Schema(description = "Redemption timestamp")
    LocalDateTime usedAt,

    @Schema(description = "Remaining number of uses", example = "58")
    int remainingUses
) {}
