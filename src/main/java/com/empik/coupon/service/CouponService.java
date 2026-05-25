package com.empik.coupon.service;

import com.empik.coupon.api.request.CreateCouponRequest;
import com.empik.coupon.api.request.UseCouponRequest;
import com.empik.coupon.api.response.CouponResponse;
import com.empik.coupon.api.response.CouponUseResponse;
import com.empik.coupon.domain.Coupon;
import com.empik.coupon.domain.CouponUsage;
import com.empik.coupon.domain.exception.CountryNotAllowedException;
import com.empik.coupon.domain.exception.CouponAlreadyExistsException;
import com.empik.coupon.domain.exception.CouponAlreadyUsedException;
import com.empik.coupon.domain.exception.CouponExhaustedException;
import com.empik.coupon.domain.exception.CouponNotFoundException;
import com.empik.coupon.infrastructure.geolocation.GeoLocationService;
import com.empik.coupon.repository.CouponRepository;
import com.empik.coupon.repository.CouponUsageRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private static final int START_CURRENT_USES = 0;

    private static final String METRIC_CREATIONS = "coupon.creations";
    private static final String METRIC_USED = "coupon.used";
    private static final String METRIC_REJECTED = "coupon.rejected";
    private static final String TAG_COUNTRY = "country";
    private static final String TAG_COUPON = "coupon";
    private static final String TAG_REASON = "reason";

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final GeoLocationService geoLocationService;
    private final CouponMapper couponMapper;
    private final MeterRegistry meterRegistry;

    @Transactional
    public CouponResponse createCoupon(final CreateCouponRequest request) {
        final String normalizedCode = request.code().toUpperCase();

        final Coupon coupon = Coupon.builder()
            .code(normalizedCode)
            .maxUses(request.maxUses())
            .currentUses(START_CURRENT_USES)
            .country(request.country().toUpperCase())
            .build();

        final Coupon saved = saveCoupon(coupon, normalizedCode);

        log.info("Created coupon '{}' for country {}, maxUses={}",
            saved.getCode(), saved.getCountry(), saved.getMaxUses());
        meterRegistry.counter(METRIC_CREATIONS, TAG_COUNTRY, saved.getCountry()).increment();

        return couponMapper.toResponse(saved);
    }

    @Transactional
    public CouponUseResponse useCoupon(final String code, final UseCouponRequest request, final String clientIp) {
        final String normalizedCode = code.toUpperCase();
        final String userId = request.userId();

        final Coupon coupon = loadCoupon(normalizedCode);
        verifyCountryEligibility(coupon, normalizedCode, clientIp);
        claimSlotOrFail(normalizedCode);
        final CouponUsage usage = registerUsageOrFail(coupon.getId(), userId, normalizedCode);

        return buildUseResponse(coupon.getId(), userId, normalizedCode, usage);
    }

    private Coupon saveCoupon(final Coupon coupon, final String code) {
        try {
            return couponRepository.saveAndFlush(coupon);
        } catch (final DataIntegrityViolationException ex) {
            throw new CouponAlreadyExistsException(
                "Coupon '%s' already exists".formatted(code));
        }
    }

    private Coupon loadCoupon(final String normalizedCode) {
        return couponRepository.findByCode(normalizedCode)
            .orElseThrow(() -> new CouponNotFoundException(
                "Coupon '%s' does not exist".formatted(normalizedCode)));
    }

    private void verifyCountryEligibility(final Coupon coupon, final String normalizedCode, final String clientIp) {
        final String country = geoLocationService.getCountryCode(clientIp);
        if (coupon.isAllowedForCountry(country)) {
            return;
        }
        log.warn("Coupon '{}' redemption rejected: country {} not allowed (required: {})",
            normalizedCode, country, coupon.getCountry());
        meterRegistry.counter(METRIC_REJECTED,
            TAG_REASON, RejectionReason.COUNTRY_NOT_ALLOWED.tag(), TAG_COUPON, normalizedCode).increment();
        throw new CountryNotAllowedException(
            "Coupon '%s' is only available in %s".formatted(normalizedCode, coupon.getCountry()));
    }

    private void claimSlotOrFail(final String normalizedCode) {
        final int claimed = couponRepository.incrementUsesIfAvailable(normalizedCode);
        if (claimed > 0) {
            return;
        }
        log.warn("Coupon '{}' redemption rejected: exhausted", normalizedCode);
        meterRegistry.counter(METRIC_REJECTED,
            TAG_REASON, RejectionReason.EXHAUSTED.tag(), TAG_COUPON, normalizedCode).increment();
        throw new CouponExhaustedException(
            "Coupon '%s' has reached its maximum number of uses".formatted(normalizedCode));
    }

    private CouponUsage registerUsageOrFail(final Long couponId, final String userId, final String normalizedCode) {
        final CouponUsage usage = CouponUsage.builder()
            .coupon(couponRepository.getReferenceById(couponId))
            .userId(userId)
            .build();
        try {
            return couponUsageRepository.saveAndFlush(usage);
        } catch (final DataIntegrityViolationException ex) {
            log.warn("Coupon '{}' redemption rejected: user '{}' already used it",
                normalizedCode, userId);
            meterRegistry.counter(METRIC_REJECTED,
                TAG_REASON, RejectionReason.ALREADY_USED.tag(), TAG_COUPON, normalizedCode).increment();
            throw new CouponAlreadyUsedException(
                "User '%s' has already used coupon '%s'".formatted(userId, normalizedCode));
        }
    }

    private CouponUseResponse buildUseResponse(final Long couponId, final String userId,
                                               final String normalizedCode, final CouponUsage usage) {
        final Coupon refreshed = couponRepository.findById(couponId)
            .orElseThrow(() -> new IllegalStateException(
                "Coupon id=%d vanished mid-transaction".formatted(couponId)));
        final int remaining = refreshed.getMaxUses() - refreshed.getCurrentUses();

        log.info("Coupon '{}' redeemed by user '{}'. Remaining uses: {}",
            normalizedCode, userId, remaining);
        meterRegistry.counter(METRIC_USED,
            TAG_COUPON, normalizedCode, TAG_COUNTRY, refreshed.getCountry()).increment();

        return new CouponUseResponse(normalizedCode, userId, usage.getUsedAt(), remaining);
    }
}
