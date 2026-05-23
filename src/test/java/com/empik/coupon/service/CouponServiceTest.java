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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService unit tests")
class CouponServiceTest {

    private static final String CODE = "SPRING2024";
    private static final String CLIENT_IP = "1.2.3.4";
    private static final Long COUPON_ID = 1L;

    @Mock private CouponRepository couponRepository;
    @Mock private CouponUsageRepository couponUsageRepository;
    @Mock private GeoLocationService geoLocationService;
    @Mock private CouponMapper couponMapper;

    private CouponService couponService;

    @BeforeEach
    void setUp() {
        final MeterRegistry meterRegistry = new SimpleMeterRegistry();
        couponService = new CouponService(
            couponRepository, couponUsageRepository,
            geoLocationService, couponMapper, meterRegistry
        );
    }

    @Test
    @DisplayName("normalizes coupon code to UPPERCASE on creation")
    void shouldNormalizeCouponCodeToUppercase() {
        // given
        when(couponRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(couponMapper.toResponse(any()))
            .thenReturn(new CouponResponse(COUPON_ID, CODE, null, 100, 0, "PL", false));

        // when
        final CouponResponse response = couponService.createCoupon(
            new CreateCouponRequest("spring2024", 100, "pl"));

        // then
        assertThat(response.code()).isEqualTo(CODE);
    }

    @Test
    @DisplayName("translates UNIQUE-violation on insert into CouponAlreadyExistsException")
    void shouldRejectDuplicateCreation() {
        // given
        when(couponRepository.saveAndFlush(any()))
            .thenThrow(new DataIntegrityViolationException("unique constraint"));

        // when / then
        assertThatThrownBy(() ->
            couponService.createCoupon(new CreateCouponRequest("dup", 5, "PL")))
            .isInstanceOf(CouponAlreadyExistsException.class);
    }

    @Test
    @DisplayName("redeems a coupon and returns the remaining-uses count")
    void shouldRedeemSuccessfully() {
        // given
        final Coupon initial = couponWithUses(4);
        final Coupon refreshed = couponWithUses(5);
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.of(initial));
        when(geoLocationService.getCountryCode(CLIENT_IP)).thenReturn("PL");
        when(couponRepository.incrementUsesIfAvailable(CODE)).thenReturn(1);
        when(couponRepository.getReferenceById(COUPON_ID)).thenReturn(initial);
        when(couponUsageRepository.saveAndFlush(any())).thenAnswer(inv -> {
            final CouponUsage u = inv.getArgument(0);
            u.setUsedAt(LocalDateTime.now());
            return u;
        });
        when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(refreshed));

        // when
        final CouponUseResponse response = couponService.useCoupon(
            CODE, new UseCouponRequest("user-1"), CLIENT_IP);

        // then
        assertThat(response.couponCode()).isEqualTo(CODE);
        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.remainingUses()).isEqualTo(5);
    }

    @Test
    @DisplayName("throws CouponNotFoundException when the coupon is missing")
    void shouldThrowWhenCouponNotFound() {
        // given
        when(couponRepository.findByCode("MISSING")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() ->
            couponService.useCoupon("missing", new UseCouponRequest("u"), CLIENT_IP))
            .isInstanceOf(CouponNotFoundException.class);
        verify(couponRepository, never()).incrementUsesIfAvailable(any());
    }

    @Test
    @DisplayName("throws CountryNotAllowedException for the wrong country and does not increment")
    void shouldThrowWhenCountryNotAllowed() {
        // given
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.of(couponWithUses(5)));
        when(geoLocationService.getCountryCode(CLIENT_IP)).thenReturn("DE");

        // when / then
        assertThatThrownBy(() ->
            couponService.useCoupon(CODE, new UseCouponRequest("u"), CLIENT_IP))
            .isInstanceOf(CountryNotAllowedException.class);
        verify(couponRepository, never()).incrementUsesIfAvailable(any());
    }

    @Test
    @DisplayName("throws CouponExhaustedException when the atomic UPDATE affects 0 rows")
    void shouldThrowWhenExhausted() {
        // given
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.of(couponWithUses(10)));
        when(geoLocationService.getCountryCode(CLIENT_IP)).thenReturn("PL");
        when(couponRepository.incrementUsesIfAvailable(CODE)).thenReturn(0);

        // when / then
        assertThatThrownBy(() ->
            couponService.useCoupon(CODE, new UseCouponRequest("u"), CLIENT_IP))
            .isInstanceOf(CouponExhaustedException.class);
        verify(couponUsageRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("translates UNIQUE-constraint violation into CouponAlreadyUsedException")
    void shouldThrowWhenSameUserRedeemsTwice() {
        // given
        final Coupon coupon = couponWithUses(4);
        when(couponRepository.findByCode(CODE)).thenReturn(Optional.of(coupon));
        when(geoLocationService.getCountryCode(CLIENT_IP)).thenReturn("PL");
        when(couponRepository.incrementUsesIfAvailable(CODE)).thenReturn(1);
        when(couponRepository.getReferenceById(COUPON_ID)).thenReturn(coupon);
        when(couponUsageRepository.saveAndFlush(any()))
            .thenThrow(new DataIntegrityViolationException("unique constraint"));

        // when / then
        assertThatThrownBy(() ->
            couponService.useCoupon(CODE, new UseCouponRequest("u"), CLIENT_IP))
            .isInstanceOf(CouponAlreadyUsedException.class);
    }

    private Coupon couponWithUses(final int currentUses) {
        return Coupon.builder()
            .id(COUPON_ID)
            .code(CODE)
            .country("PL")
            .maxUses(10)
            .currentUses(currentUses)
            .build();
    }
}
