package com.empik.coupon.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Request to create a new coupon")
public record CreateCouponRequest(

    @NotBlank(message = "Coupon code must not be blank")
    @Size(min = 3, max = 100, message = "Coupon code must be between 3 and 100 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Coupon code may contain only letters, digits, '_' and '-'")
    @Schema(description = "Unique coupon code (case-insensitive)", example = "SPRING2024")
    String code,

    @NotNull(message = "Max uses is required")
    @Min(value = 1, message = "Max uses must be greater than 0")
    @Max(value = 1_000_000, message = "Max uses must not exceed 1,000,000")
    @Schema(description = "Maximum number of times the coupon can be used", example = "100")
    Integer maxUses,

    @NotBlank(message = "Country is required")
    @Size(min = 2, max = 2, message = "Country must be an ISO 3166-1 alpha-2 code (2 letters)")
    @Schema(description = "ISO 3166-1 alpha-2 country code", example = "PL")
    String country
) {}
