package com.empik.coupon.service;

import com.empik.coupon.api.response.CouponResponse;
import com.empik.coupon.domain.Coupon;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CouponMapper {

    @Mapping(target = "exhausted", expression = "java(coupon.isExhausted())")
    CouponResponse toResponse(Coupon coupon);
}
