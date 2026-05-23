package com.empik.coupon.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Error response")
public record ErrorResponse(

    @Schema(description = "Error code", example = "COUPON_NOT_FOUND")
    String errorCode,

    @Schema(description = "Human-readable error description", example = "Coupon does not exist")
    String message,

    @Schema(description = "Time the error occurred")
    LocalDateTime timestamp
) {}
