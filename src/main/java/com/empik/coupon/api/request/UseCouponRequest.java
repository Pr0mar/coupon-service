package com.empik.coupon.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to redeem a coupon")
public record UseCouponRequest(

    @NotBlank(message = "User id must not be blank")
    @Size(min = 1, max = 255, message = "User id must be between 1 and 255 characters")
    @Schema(description = "Unique user identifier", example = "user-123")
    String userId
) {}
