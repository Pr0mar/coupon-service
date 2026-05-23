package com.empik.coupon.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Coupon details")
public record CouponResponse(

    @Schema(description = "Coupon id", example = "1")
    Long id,

    @Schema(description = "Coupon code", example = "SPRING2024")
    String code,

    @Schema(description = "Creation timestamp")
    LocalDateTime createdAt,

    @Schema(description = "Maximum number of uses", example = "100")
    Integer maxUses,

    @Schema(description = "Current number of uses", example = "42")
    Integer currentUses,

    @Schema(description = "Country code (ISO 3166-1 alpha-2)", example = "PL")
    String country,

    @Schema(description = "Whether the coupon is exhausted", example = "false")
    boolean exhausted
) {}
