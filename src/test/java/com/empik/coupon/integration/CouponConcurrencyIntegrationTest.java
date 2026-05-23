package com.empik.coupon.integration;

import com.empik.coupon.api.request.CreateCouponRequest;
import com.empik.coupon.api.request.UseCouponRequest;
import com.empik.coupon.domain.exception.CouponAlreadyUsedException;
import com.empik.coupon.domain.exception.CouponExhaustedException;
import com.empik.coupon.infrastructure.geolocation.GeoLocationService;
import com.empik.coupon.repository.CouponRepository;
import com.empik.coupon.repository.CouponUsageRepository;
import com.empik.coupon.service.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("Coupon concurrency: first-come-first-served invariant")
class CouponConcurrencyIntegrationTest extends BaseIntegrationTest {

    private static final int THREADS = 100;
    private static final String CLIENT_IP = "1.2.3.4";

    @Autowired private CouponService couponService;
    @Autowired private CouponRepository couponRepository;
    @Autowired private CouponUsageRepository couponUsageRepository;
    @MockBean  private GeoLocationService geoLocationService;

    @BeforeEach
    void setUp() {
        couponUsageRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        when(geoLocationService.getCountryCode(anyString())).thenReturn("PL");
    }

    @Test
    @DisplayName("maxUses=1 with 100 distinct users → exactly 1 success, 99 exhausted")
    void singleSlotIsClaimedByExactlyOneWinner() {
        // given
        final String code = "RACE-SINGLE";
        couponService.createCoupon(new CreateCouponRequest(code, 1, "PL"));

        // when
        final Result result = redeemConcurrently(THREADS, i -> "user-" + i, code);

        // then
        assertThat(result.successes).hasValue(1);
        assertThat(result.exhausted).hasValue(THREADS - 1);
        assertThat(result.alreadyUsed).hasValue(0);
        assertThat(result.unexpected).isEmpty();
        assertCouponState(code, 1, 1);
        assertThat(couponUsageRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("maxUses=10 with 100 distinct users → exactly 10 successes, 90 exhausted")
    void multiSlotConsumesExactlyMaxUses() {
        // given
        final String code = "RACE-MULTI";
        couponService.createCoupon(new CreateCouponRequest(code, 10, "PL"));

        // when
        final Result result = redeemConcurrently(THREADS, i -> "user-" + i, code);

        // then
        assertThat(result.successes).hasValue(10);
        assertThat(result.exhausted).hasValue(THREADS - 10);
        assertThat(result.alreadyUsed).hasValue(0);
        assertThat(result.unexpected).isEmpty();
        assertCouponState(code, 10, 10);
        assertThat(couponUsageRepository.count()).isEqualTo(10);
    }

    @Test
    @DisplayName("same user retrying 100x on maxUses=50 → exactly 1 success, 99 already-used")
    void singleUserCanRedeemAtMostOnce() {
        // given
        final String code = "RACE-USER";
        couponService.createCoupon(new CreateCouponRequest(code, 50, "PL"));

        // when
        final Result result = redeemConcurrently(THREADS, i -> "the-only-user", code);

        // then
        assertThat(result.successes).hasValue(1);
        assertThat(result.alreadyUsed).hasValue(THREADS - 1);
        assertThat(result.exhausted).hasValue(0);
        assertThat(result.unexpected).isEmpty();
        assertCouponState(code, 1, 50);
        assertThat(couponUsageRepository.count()).isEqualTo(1);
    }

    private Result redeemConcurrently(final int threads, final IntFunction<String> userIdFor, final String code) {
        final Result result = new Result();
        final CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, threads).forEach(i -> pool.submit(() -> {
                startGate.await();
                attemptRedemption(code, userIdFor.apply(i), result);
                return null;
            }));
            startGate.countDown();
        }
        return result;
    }

    private void attemptRedemption(final String code, final String userId, final Result result) {
        try {
            couponService.useCoupon(code, new UseCouponRequest(userId), CLIENT_IP);
            result.successes.incrementAndGet();
        } catch (final CouponExhaustedException e) {
            result.exhausted.incrementAndGet();
        } catch (final CouponAlreadyUsedException e) {
            result.alreadyUsed.incrementAndGet();
        } catch (final RuntimeException e) {
            result.unexpected.add(e);
        }
    }

    private void assertCouponState(final String code, final int expectedUses, final int expectedMax) {
        assertThat(couponRepository.findByCode(code)).hasValueSatisfying(c -> {
            assertThat(c.getCurrentUses()).isEqualTo(expectedUses);
            assertThat(c.getMaxUses()).isEqualTo(expectedMax);
        });
    }

    private static final class Result {
        final AtomicInteger successes = new AtomicInteger();
        final AtomicInteger exhausted = new AtomicInteger();
        final AtomicInteger alreadyUsed = new AtomicInteger();
        final List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
    }
}
